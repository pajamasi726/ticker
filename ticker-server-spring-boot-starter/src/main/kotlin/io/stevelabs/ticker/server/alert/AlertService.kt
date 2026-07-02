package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.state.ServiceState
import io.stevelabs.ticker.server.state.TargetHealth
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executor

/**
 * Each poll cycle, diff every target's effective state vs the previous cycle and alert on DOWN
 * entry/exit. Deploys and incidents are distinct: gracefully-stopped instances deregister and never
 * alert, and an active [AlertSilence] window (deploy pipelines call `POST /api/alerts/silence`)
 * suppresses incident dispatch — anything still DOWN when the window ends is announced then.
 */
class AlertService(
    private val store: HealthStateStore,
    private val decider: AlertDecider,
    private val properties: AlertProperties,
    private val sender: AlertSender?,
    private val executor: Executor,
    private val silence: AlertSilence = AlertSilence(),
) {
    private val log = LoggerFactory.getLogger(AlertService::class.java)
    private val previousStates = HashMap<String, ServiceState>()
    private val lastIncidentAt = HashMap<String, Instant>()
    /** IDs with an open, actually-dispatched incident — used to suppress orphan recoveries. */
    private val alerted = HashSet<String>()
    /** IDs that went DOWN during a silence window — announced when the window ends if still DOWN. */
    private val suppressedDown = HashSet<String>()
    private var warnedNoSender = false

    @Scheduled(fixedRateString = "\${ticker.poll.interval:10s}")
    fun checkForAlerts() {
        val now = Instant.now()
        val silenced = silence.isActive(now)
        val snapshot = store.snapshot(now)
        val liveIds = HashSet<String>()
        for (th in snapshot) {
            val id = th.target.id
            liveIds += id
            val outcome = decider.decide(previousStates[id], th.state, lastIncidentAt[id], now, properties.cooldown)
            when (outcome.kind) {
                AlertKind.INCIDENT -> {
                    if (silenced) {
                        suppressedDown += id
                        log.info("Incident for '{}' suppressed by the alert silence window (deploy?).", id)
                    } else {
                        emit(downMessage(th))
                        if (sender != null) alerted += id
                    }
                }
                AlertKind.RECOVERY -> {
                    suppressedDown -= id // bounced during a deploy window: never announced, stay quiet
                    if (id in alerted) {
                        // A recovery closes an ALREADY-ANNOUNCED incident, so it bypasses the
                        // silence window — otherwise the channel is left with a dangling 🔴.
                        emit(recoveryMessage(th, lastIncidentAt[id], now))
                        alerted -= id
                    }
                }
                AlertKind.NONE -> {}
            }
            outcome.lastIncidentAt?.let { lastIncidentAt[id] = it }
            previousStates[id] = th.state
        }
        // Window just ended: announce whatever is STILL down — a silence must never swallow a real outage.
        if (!silenced && suppressedDown.isNotEmpty()) {
            for (th in snapshot) {
                if (th.target.id in suppressedDown && th.state == ServiceState.DOWN) {
                    emit(downMessage(th))
                    if (sender != null) alerted += th.target.id
                }
            }
            suppressedDown.clear()
        }
        // forget targets that no longer exist (deregistered / removed)
        previousStates.keys.retainAll(liveIds)
        lastIncidentAt.keys.retainAll(liveIds)
        alerted.retainAll(liveIds)
        suppressedDown.retainAll(liveIds)
    }

    // Card layout rule: the title carries the whole sentence (name, instance, what happened);
    // fields only add NEW information — repeating the title as fields reads as clutter.

    private fun downMessage(th: TargetHealth): AlertMessage {
        val t = th.target
        val plain = "🔴 *${t.name}*${instanceSuffix(t.instance)} is DOWN"
        return AlertMessage(
            severity = AlertSeverity.DOWN,
            title = plain,
            fields = buildList {
                t.ip?.let { add("IP" to it) }
                add("URL" to t.url)
            },
            context = contextLine(TextSparkline.of(th.sparkline.map { it?.toDouble() }).takeIf { it.isNotEmpty() }?.let { "latency $it" }),
            fallback = plain,
        )
    }

    private fun recoveryMessage(th: TargetHealth, downSince: Instant?, now: Instant): AlertMessage {
        val t = th.target
        val downtime = downSince?.let { humanDuration(Duration.between(it, now)) }
        val plain = "🟢 *${t.name}*${instanceSuffix(t.instance)} recovered" +
            (downtime?.let { " · down $it" } ?: "")
        return AlertMessage(
            severity = AlertSeverity.RECOVERED,
            title = plain,
            context = contextLine(null),
            fallback = plain,
        )
    }

    /** Joins the trend snippet with an optional board link into the dim footer line. */
    private fun contextLine(trend: String?): String? {
        val board = properties.boardUrl?.takeIf { it.isNotBlank() }?.let { "<$it|Open Ticker board>" }
        val parts = listOfNotNull(trend, board)
        return parts.joinToString("  ·  ").ifBlank { null }
    }

    private fun instanceSuffix(instance: String?): String =
        if (instance.isNullOrBlank()) "" else " [$instance]"

    private fun humanDuration(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        return when {
            s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m"
            s >= 60 -> "${s / 60}m ${s % 60}s"
            else -> "${s}s"
        }
    }

    private fun emit(message: AlertMessage) {
        val s = sender
        if (s == null) {
            if (!warnedNoSender) {
                log.warn("ticker.alert.enabled=true but no slack-webhook-url is set; alerts are inert.")
                warnedNoSender = true
            }
            return
        }
        executor.execute { s.send(message) }
    }
}
