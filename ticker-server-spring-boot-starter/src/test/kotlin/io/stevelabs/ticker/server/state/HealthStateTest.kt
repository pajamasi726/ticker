package io.stevelabs.ticker.server.state

import io.stevelabs.ticker.server.check.CheckOutcome
import io.stevelabs.ticker.server.check.CheckResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class HealthStateTest {
    private val t0: Instant = Instant.parse("2026-06-30T00:00:00Z")
    private val staleness: Duration = Duration.ofSeconds(30)
    private fun ok(ms: Int = 10) = CheckResult(CheckOutcome.SUCCESS, ms)
    private fun degraded(ms: Int = 2000) = CheckResult(CheckOutcome.DEGRADED, ms)
    private fun fail() = CheckResult(CheckOutcome.FAILURE, 0)

    @Test fun `initial state is UNKNOWN`() {
        val hs = HealthState(failureThreshold = 3)
        assertThat(hs.effectiveState(t0, staleness)).isEqualTo(ServiceState.UNKNOWN)
    }

    @Test fun `success goes UP`() {
        val hs = HealthState(3); hs.record(ok(42), t0)
        assertThat(hs.effectiveState(t0, staleness)).isEqualTo(ServiceState.UP)
        assertThat(hs.lastLatencyMs).isEqualTo(42)
    }

    @Test fun `degraded is immediate`() {
        val hs = HealthState(3); hs.record(degraded(), t0)
        assertThat(hs.effectiveState(t0, staleness)).isEqualTo(ServiceState.DEGRADED)
    }

    @Test fun `failures below threshold keep prior state (debounce hides blip)`() {
        val hs = HealthState(3)
        hs.record(ok(), t0)
        hs.record(fail(), t0); hs.record(fail(), t0)   // 2 failures < threshold 3
        assertThat(hs.effectiveState(t0, staleness)).isEqualTo(ServiceState.UP)
    }

    @Test fun `threshold consecutive failures flips DOWN`() {
        val hs = HealthState(3)
        hs.record(ok(), t0); repeat(3) { hs.record(fail(), t0) }
        assertThat(hs.effectiveState(t0, staleness)).isEqualTo(ServiceState.DOWN)
    }

    @Test fun `success resets the failure counter`() {
        val hs = HealthState(3)
        hs.record(fail(), t0); hs.record(fail(), t0)
        hs.record(ok(), t0)                            // reset
        hs.record(fail(), t0); hs.record(fail(), t0)   // only 2 again -> still UP
        assertThat(hs.effectiveState(t0, staleness)).isEqualTo(ServiceState.UP)
    }

    @Test fun `stale sample reads UNKNOWN`() {
        val hs = HealthState(3); hs.record(ok(), t0)
        assertThat(hs.effectiveState(t0.plusSeconds(31), staleness)).isEqualTo(ServiceState.UNKNOWN)
    }

    @Test fun `sparkline keeps recent latencies with null for failures, bounded`() {
        val hs = HealthState(failureThreshold = 3, sparklineCapacity = 3)
        hs.record(ok(1), t0); hs.record(ok(2), t0); hs.record(fail(), t0); hs.record(ok(4), t0)
        assertThat(hs.sparkline()).containsExactly(2, null, 4)   // bounded to last 3
    }
}
