package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.state.ServiceState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AlertDeciderTest {
    private val decider = AlertDecider()
    private val t0: Instant = Instant.parse("2026-06-30T00:00:00Z")
    private val cooldown: Duration = Duration.ofMinutes(15)

    @Test fun `first observation establishes baseline without alerting`() {
        assertThat(decider.decide(null, ServiceState.DOWN, null, t0, cooldown).kind).isEqualTo(AlertKind.NONE)
        assertThat(decider.decide(null, ServiceState.UP, null, t0, cooldown).kind).isEqualTo(AlertKind.NONE)
    }

    @Test fun `entering DOWN fires an incident and sets the cooldown anchor`() {
        val out = decider.decide(ServiceState.UP, ServiceState.DOWN, null, t0, cooldown)
        assertThat(out.kind).isEqualTo(AlertKind.INCIDENT)
        assertThat(out.lastIncidentAt).isEqualTo(t0)
    }

    @Test fun `leaving DOWN fires a recovery (to UP or DEGRADED)`() {
        assertThat(decider.decide(ServiceState.DOWN, ServiceState.UP, t0, t0.plusSeconds(60), cooldown).kind)
            .isEqualTo(AlertKind.RECOVERY)
        assertThat(decider.decide(ServiceState.DOWN, ServiceState.DEGRADED, t0, t0.plusSeconds(60), cooldown).kind)
            .isEqualTo(AlertKind.RECOVERY)
    }

    @Test fun `non-DOWN transitions are silent`() {
        assertThat(decider.decide(ServiceState.UP, ServiceState.DEGRADED, null, t0, cooldown).kind).isEqualTo(AlertKind.NONE)
        assertThat(decider.decide(ServiceState.UP, ServiceState.UNKNOWN, null, t0, cooldown).kind).isEqualTo(AlertKind.NONE)
        assertThat(decider.decide(ServiceState.DEGRADED, ServiceState.UP, null, t0, cooldown).kind).isEqualTo(AlertKind.NONE)
    }

    @Test fun `a second incident within the cooldown is suppressed and keeps the anchor`() {
        val first = decider.decide(ServiceState.UP, ServiceState.DOWN, null, t0, cooldown)
        val within = decider.decide(ServiceState.UP, ServiceState.DOWN, first.lastIncidentAt, t0.plusSeconds(300), cooldown)
        assertThat(within.kind).isEqualTo(AlertKind.NONE)
        assertThat(within.lastIncidentAt).isEqualTo(t0)
    }

    @Test fun `a second incident after the cooldown fires again and moves the anchor`() {
        val after = decider.decide(ServiceState.UP, ServiceState.DOWN, t0, t0.plus(Duration.ofMinutes(16)), cooldown)
        assertThat(after.kind).isEqualTo(AlertKind.INCIDENT)
        assertThat(after.lastIncidentAt).isEqualTo(t0.plus(Duration.ofMinutes(16)))
    }
}
