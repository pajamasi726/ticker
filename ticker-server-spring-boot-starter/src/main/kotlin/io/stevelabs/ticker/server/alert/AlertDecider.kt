package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.state.ServiceState
import java.time.Duration
import java.time.Instant

enum class AlertKind { NONE, INCIDENT, RECOVERY }

data class AlertOutcome(val kind: AlertKind, val lastIncidentAt: Instant?)

/** Pure transition→alert decision (no Spring, no HTTP) — the unit-tested core. */
class AlertDecider {
    fun decide(
        previous: ServiceState?,
        current: ServiceState,
        lastIncidentAt: Instant?,
        now: Instant,
        cooldown: Duration,
    ): AlertOutcome {
        if (previous == null) return AlertOutcome(AlertKind.NONE, lastIncidentAt) // baseline; never alert on first sight

        val enteredDown = previous != ServiceState.DOWN && current == ServiceState.DOWN
        val leftDown = previous == ServiceState.DOWN && (current == ServiceState.UP || current == ServiceState.DEGRADED)

        return when {
            enteredDown ->
                if (lastIncidentAt == null || Duration.between(lastIncidentAt, now) >= cooldown) {
                    AlertOutcome(AlertKind.INCIDENT, now)
                } else {
                    AlertOutcome(AlertKind.NONE, lastIncidentAt)
                }
            leftDown -> AlertOutcome(AlertKind.RECOVERY, lastIncidentAt)
            else -> AlertOutcome(AlertKind.NONE, lastIncidentAt)
        }
    }
}
