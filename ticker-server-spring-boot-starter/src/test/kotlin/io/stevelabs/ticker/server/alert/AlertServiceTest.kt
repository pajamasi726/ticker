package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.check.CheckOutcome
import io.stevelabs.ticker.server.check.CheckResult
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AlertServiceTest {
    private class RecordingSender : AlertSender {
        val sent = mutableListOf<String>()
        override fun send(text: String) { sent += text }
    }

    private fun storeWith(name: String): HealthStateStore =
        HealthStateStore(
            TargetRegistry(listOf(TargetDefinition(name, ServiceType.HTTP, "http://$name"))),
            PollProperties(failureThreshold = 1), // one failure → DOWN, so the test needs no loop
        )

    @Test fun `fires one incident on DOWN and one recovery on UP across cycles`() {
        val store = storeWith("svc")
        val sender = RecordingSender()
        val svc = AlertService(store, AlertDecider(), AlertProperties(enabled = true), sender)

        svc.checkForAlerts()                                              // cycle 1: baseline (UNKNOWN) → no alert
        assertThat(sender.sent).isEmpty()

        store.record("svc", CheckResult(CheckOutcome.FAILURE, 0), Instant.now())
        svc.checkForAlerts()                                              // cycle 2: → DOWN → incident
        store.record("svc", CheckResult(CheckOutcome.SUCCESS, 5), Instant.now())
        svc.checkForAlerts()                                              // cycle 3: → UP → recovery

        assertThat(sender.sent).hasSize(2)
        assertThat(sender.sent[0]).contains("svc").contains("DOWN")
        assertThat(sender.sent[1]).contains("svc").contains("recovered")
    }

    @Test fun `stays silent for a DEGRADED transition`() {
        val store = storeWith("svc")
        val sender = RecordingSender()
        val svc = AlertService(store, AlertDecider(), AlertProperties(enabled = true), sender)
        svc.checkForAlerts()                                              // baseline
        store.record("svc", CheckResult(CheckOutcome.DEGRADED, 1500), Instant.now())
        svc.checkForAlerts()                                              // UNKNOWN → DEGRADED → silent
        assertThat(sender.sent).isEmpty()
    }

    @Test fun `does not throw when no sender is configured`() {
        val store = storeWith("svc")
        val svc = AlertService(store, AlertDecider(), AlertProperties(enabled = true), sender = null)
        svc.checkForAlerts()
        store.record("svc", CheckResult(CheckOutcome.FAILURE, 0), Instant.now())
        svc.checkForAlerts()                                              // would-be incident, no sender → inert, no throw
    }
}
