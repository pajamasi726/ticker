package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.state.ServiceState
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant

/** Each poll cycle, diff every target's effective state vs the previous cycle and alert on DOWN entry/exit. */
class AlertService(
    private val store: HealthStateStore,
    private val decider: AlertDecider,
    private val properties: AlertProperties,
    private val sender: AlertSender?,
) {
    private val log = LoggerFactory.getLogger(AlertService::class.java)
    private val previousStates = HashMap<String, ServiceState>()
    private val lastIncidentAt = HashMap<String, Instant>()
    private var warnedNoSender = false

    @Scheduled(fixedRateString = "\${ticker.poll.interval:10s}")
    fun checkForAlerts() {
        val now = Instant.now()
        val snapshot = store.snapshot(now)
        val liveIds = HashSet<String>()
        for (th in snapshot) {
            val id = th.target.id
            liveIds += id
            val outcome = decider.decide(previousStates[id], th.state, lastIncidentAt[id], now, properties.cooldown)
            when (outcome.kind) {
                AlertKind.INCIDENT -> emit("🔴 *${th.target.name}* is DOWN")
                AlertKind.RECOVERY -> emit("🟢 *${th.target.name}* recovered")
                AlertKind.NONE -> {}
            }
            outcome.lastIncidentAt?.let { lastIncidentAt[id] = it }
            previousStates[id] = th.state
        }
        // forget targets that no longer exist (deregistered / removed)
        previousStates.keys.retainAll(liveIds)
        lastIncidentAt.keys.retainAll(liveIds)
    }

    private fun emit(text: String) {
        val s = sender
        if (s == null) {
            if (!warnedNoSender) {
                log.warn("ticker.alert.enabled=true but no slack-webhook-url is set; alerts are inert.")
                warnedNoSender = true
            }
            return
        }
        s.send(text)
    }
}
