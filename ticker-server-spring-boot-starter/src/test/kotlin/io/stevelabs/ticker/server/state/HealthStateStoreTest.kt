package io.stevelabs.ticker.server.state

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.check.CheckOutcome
import io.stevelabs.ticker.server.check.CheckResult
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class HealthStateStoreTest {
    @Test fun `evict removes a target's health from the snapshot`() {
        val registry = TargetRegistry(listOf(TargetDefinition("svc", ServiceType.HTTP, "http://svc")))
        val store = HealthStateStore(registry, PollProperties())
        val now = Instant.now()
        store.record("svc", CheckResult(CheckOutcome.SUCCESS, 5), now)
        assertThat(store.snapshot(now).single().state).isEqualTo(ServiceState.UP)
        store.evict("svc")
        // snapshot is registry-driven: 'svc' is still a target, but its health resets to UNKNOWN after eviction
        assertThat(store.snapshot(now).single().state).isEqualTo(ServiceState.UNKNOWN)
    }
}
