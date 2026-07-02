package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.MetricRef
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.detail.Unit
import io.stevelabs.ticker.server.state.HealthStateStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.util.concurrent.Executor

/**
 * Periodically evaluates every enabled metric-alert rule against every live SPRING target.
 * Per (targetId, ruleKey) cooldown prevents alert spam.  Fires are dispatched via the
 * injected [AlertSender] (async) and also recorded in the [MetricAlertStore] so the UI
 * can show recent alerts without a Slack webhook being configured.
 */
class MetricAlertService(
    private val store: HealthStateStore,
    private val metricSource: MetricSource,
    private val rules: MetricAlertStore,
    private val sender: AlertSender?,
    private val executor: Executor,
    private val silence: AlertSilence = AlertSilence(),
    private val boardUrl: String? = null,
) {
    private val log = LoggerFactory.getLogger(MetricAlertService::class.java)
    private val lastFiredAt = HashMap<String, Instant>() // key = "$targetId/$ruleKey"
    private val breachingSince = HashMap<String, Instant>() // key = "$targetId/$ruleKey"
    /** Recent evaluated values per (target,rule) — fuels the trend sparkline in the alert message. */
    private val recentValues = HashMap<String, ArrayDeque<Double>>()
    private var warnedNoSender = false

    private companion object {
        const val TREND_SAMPLES = 12
    }

    @Scheduled(fixedRateString = "\${ticker.alert.metric-interval:30s}")
    fun evaluate() {
        val now = Instant.now()
        val snapshot = store.snapshot(now)
        val liveCompositeKeys = HashSet<String>()

        val enabledRules = rules.all().filter { it.enabled }

        for (th in snapshot) {
            val target = th.target
            if (target.type != ServiceType.SPRING) continue

            for (rule in enabledRules) {
                val compositeKey = "${target.id}/${rule.key}"
                liveCompositeKeys += compositeKey

                val quantity = resolveQuantity(target, rule)
                quantity?.let { q ->
                    recentValues.getOrPut(compositeKey) { ArrayDeque() }.let { ring ->
                        ring.addLast(q)
                        while (ring.size > TREND_SAMPLES) ring.removeFirst()
                    }
                }
                if (!rule.breaches(quantity)) {
                    breachingSince.remove(compositeKey)
                    continue
                }

                // Breaching: track start time and check sustained duration
                val sinceInstant = breachingSince.getOrPut(compositeKey) { now }
                val sustained = now.epochSecond - sinceInstant.epochSecond
                if (sustained < rule.forSeconds) continue

                // Deploy/maintenance silence: keep tracking, but don't fire (and don't consume the
                // cooldown) — a still-breaching rule fires as soon as the window ends.
                if (silence.isActive(now)) continue

                val lastFired = lastFiredAt[compositeKey]
                val cooldownElapsed = lastFired == null ||
                    (now.epochSecond - lastFired.epochSecond) >= rule.cooldownSeconds

                if (!cooldownElapsed) continue

                // Sustained breach + cooldown elapsed — fire
                val displayValue = quantity!!
                dispatch(buildMessage(target, rule, displayValue, recentValues[compositeKey]))

                rules.record(
                    AlertFire(
                        targetId = target.id,
                        targetName = target.name,
                        ruleKey = rule.key,
                        label = rule.label,
                        value = displayValue,
                        threshold = rule.threshold,
                        unit = rule.unit,
                        at = now,
                    ),
                )
                lastFiredAt[compositeKey] = now
            }
        }

        // Prune entries for targets/rules that no longer exist
        lastFiredAt.keys.retainAll(liveCompositeKeys)
        breachingSince.keys.retainAll(liveCompositeKeys)
        recentValues.keys.retainAll(liveCompositeKeys)
    }

    private fun resolveQuantity(target: io.stevelabs.ticker.server.target.Target, rule: MetricAlertRule): Double? {
        val numerator = metricSource.resolveValue(target, rule.metric, rule.statistic) ?: return null
        if (rule.over == null) return numerator
        val denominator = metricSource.resolveValue(target, rule.over, "VALUE") ?: return null
        if (denominator <= 0.0) return null
        return numerator / denominator
    }

    private fun buildMessage(
        target: io.stevelabs.ticker.server.target.Target,
        rule: MetricAlertRule,
        quantity: Double,
        trend: List<Double>?,
    ): AlertMessage {
        val formattedValue = formatByUnit(quantity, rule.unit)
        val formattedThreshold = formatByUnit(rule.threshold, rule.unit)
        val direction = if (rule.comparator == Comparator.GT) "exceeds" else "is below"
        val who = if (target.instance.isNullOrBlank()) target.name else "${target.name} [${target.instance}]"
        // Title = the whole sentence; no fields repeating it (clutter). Trend + link live in the footer.
        val plain = "⚠️ *$who* ${rule.label} $formattedValue $direction $formattedThreshold threshold"
        val sparkline = trend?.takeIf { it.size >= 2 }?.let { TextSparkline.of(it) }
        val sustained = rule.forSeconds.takeIf { it > 0 }?.let { "sustained ${it}s" }
        val board = boardUrl?.takeIf { it.isNotBlank() }?.let { "<$it|Open Ticker board>" }
        val context = listOfNotNull(sparkline?.let { "trend $it" }, sustained, board)
            .joinToString("  ·  ").ifBlank { null }
        return AlertMessage(
            severity = AlertSeverity.WARNING,
            title = plain,
            context = context,
            fallback = plain,
        )
    }

    private fun formatByUnit(value: Double, unit: Unit): String =
        when (unit) {
            Unit.PERCENT -> "${(value * 100).toInt()}%"
            else -> value.toString()
        }

    private fun dispatch(message: AlertMessage) {
        val s = sender
        if (s == null) {
            if (!warnedNoSender) {
                log.warn("ticker.alert.enabled=true but no slack-webhook-url is set; metric alerts are inert.")
                warnedNoSender = true
            }
            return
        }
        executor.execute { s.send(message) }
    }
}
