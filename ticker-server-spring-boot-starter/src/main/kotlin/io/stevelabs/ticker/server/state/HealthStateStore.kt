package io.stevelabs.ticker.server.state

import io.stevelabs.ticker.server.check.CheckResult
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetRegistry
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class TargetHealth(val target: Target, val state: ServiceState, val latencyMs: Int?, val sparkline: List<Int?>)

class HealthStateStore(
    private val registry: TargetRegistry,
    private val pollProperties: PollProperties,
) {
    private val states = ConcurrentHashMap<String, HealthState>()

    fun record(targetId: String, result: CheckResult, now: Instant = Instant.now()) {
        states.getOrPut(targetId) { HealthState(pollProperties.failureThreshold) }.record(result, now)
    }

    fun snapshot(now: Instant = Instant.now()): List<TargetHealth> {
        val staleness = pollProperties.interval.multipliedBy(pollProperties.stalenessMultiplier.toLong())
        return registry.all().map { t ->
            val hs = states[t.id]
            TargetHealth(
                target = t,
                state = hs?.effectiveState(now, staleness) ?: ServiceState.UNKNOWN,
                latencyMs = hs?.lastLatencyMs,
                sparkline = hs?.sparkline() ?: emptyList(),
            )
        }
    }
}
