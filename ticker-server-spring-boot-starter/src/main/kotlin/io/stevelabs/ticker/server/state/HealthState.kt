package io.stevelabs.ticker.server.state

import io.stevelabs.ticker.server.check.CheckOutcome
import io.stevelabs.ticker.server.check.CheckResult
import java.time.Duration
import java.time.Instant

class HealthState(
    private val failureThreshold: Int,
    private val sparklineCapacity: Int = 20,
) {
    var state: ServiceState = ServiceState.UNKNOWN
        private set
    var consecutiveFailures: Int = 0
        private set
    var lastLatencyMs: Int? = null
        private set
    var lastSampleAt: Instant? = null
        private set
    var lastChangeAt: Instant? = null
        private set

    private val window = ArrayDeque<Int?>()
    fun sparkline(): List<Int?> = window.toList()

    fun record(result: CheckResult, now: Instant) {
        lastSampleAt = now
        when (result.outcome) {
            CheckOutcome.SUCCESS -> {
                consecutiveFailures = 0
                lastLatencyMs = result.latencyMs
                transitionTo(ServiceState.UP, now)
                push(result.latencyMs)
            }
            CheckOutcome.DEGRADED -> {
                consecutiveFailures = 0
                lastLatencyMs = result.latencyMs
                transitionTo(ServiceState.DEGRADED, now)
                push(result.latencyMs)
            }
            CheckOutcome.FAILURE -> {
                consecutiveFailures += 1
                lastLatencyMs = null
                if (consecutiveFailures >= failureThreshold) transitionTo(ServiceState.DOWN, now)
                push(null)
            }
        }
    }

    fun effectiveState(now: Instant, stalenessWindow: Duration): ServiceState {
        val last = lastSampleAt ?: return ServiceState.UNKNOWN
        return if (Duration.between(last, now) > stalenessWindow) ServiceState.UNKNOWN else state
    }

    private fun transitionTo(next: ServiceState, now: Instant) {
        if (state != next) { state = next; lastChangeAt = now }
    }

    private fun push(v: Int?) {
        window.addLast(v)
        while (window.size > sparklineCapacity) window.removeFirst()
    }
}
