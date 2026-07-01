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
) {
    private val log = LoggerFactory.getLogger(MetricAlertService::class.java)
    private val lastFiredAt = HashMap<String, Instant>() // key = "$targetId/$ruleKey"
    private val breachingSince = HashMap<String, Instant>() // key = "$targetId/$ruleKey"
    private var warnedNoSender = false

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
                if (!rule.breaches(quantity)) {
                    breachingSince.remove(compositeKey)
                    continue
                }

                // Breaching: track start time and check sustained duration
                val sinceInstant = breachingSince.getOrPut(compositeKey) { now }
                val sustained = now.epochSecond - sinceInstant.epochSecond
                if (sustained < rule.forSeconds) continue

                val lastFired = lastFiredAt[compositeKey]
                val cooldownElapsed = lastFired == null ||
                    (now.epochSecond - lastFired.epochSecond) >= rule.cooldownSeconds

                if (!cooldownElapsed) continue

                // Sustained breach + cooldown elapsed — fire
                val displayValue = quantity!!
                val message = buildMessage(target.name, rule, displayValue)
                dispatch(message)

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
    }

    private fun resolveQuantity(target: io.stevelabs.ticker.server.target.Target, rule: MetricAlertRule): Double? {
        val numerator = metricSource.resolveValue(target, rule.metric, rule.statistic) ?: return null
        if (rule.over == null) return numerator
        val denominator = metricSource.resolveValue(target, rule.over, "VALUE") ?: return null
        if (denominator <= 0.0) return null
        return numerator / denominator
    }

    private fun buildMessage(targetName: String, rule: MetricAlertRule, quantity: Double): String {
        val formattedValue = formatByUnit(quantity, rule.unit)
        val formattedThreshold = formatByUnit(rule.threshold, rule.unit)
        val direction = if (rule.comparator == Comparator.GT) "exceeds" else "is below"
        return "⚠️ *$targetName* ${rule.label} $formattedValue $direction $formattedThreshold threshold"
    }

    private fun formatByUnit(value: Double, unit: Unit): String =
        when (unit) {
            Unit.PERCENT -> "${(value * 100).toInt()}%"
            else -> value.toString()
        }

    private fun dispatch(text: String) {
        val s = sender
        if (s == null) {
            if (!warnedNoSender) {
                log.warn("ticker.alert.enabled=true but no slack-webhook-url is set; metric alerts are inert.")
                warnedNoSender = true
            }
            return
        }
        executor.execute { s.send(text) }
    }
}
