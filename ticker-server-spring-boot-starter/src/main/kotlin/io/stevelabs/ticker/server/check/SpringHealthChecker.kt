package io.stevelabs.ticker.server.check

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.target.Target
import org.springframework.web.client.RestClient

/** Spring actuator liveness: GET {url}/actuator/health, read the top-level status.
 * Uses plain-String body + regex to avoid Jackson cold-start overhead in latency measurements. */
class SpringHealthChecker(private val restClient: RestClient, private val props: PollProperties) : HealthChecker {
    override fun supports(type: ServiceType) = type == ServiceType.SPRING

    override fun check(target: Target): CheckResult {
        val start = System.nanoTime()
        return try {
            val bodyStr = restClient.get().uri("${target.url.trimEnd('/')}/actuator/health")
                .retrieve().body(String::class.java) ?: ""
            val ms = elapsedMs(start)
            val status = STATUS_RE.find(bodyStr)?.groupValues?.getOrNull(1)?.uppercase()
            when (status) {
                "UP" -> if (ms > props.degradedLatencyMs) CheckResult(CheckOutcome.DEGRADED, ms, "slow ${ms}ms") else CheckResult(CheckOutcome.SUCCESS, ms)
                "DOWN" -> CheckResult(CheckOutcome.FAILURE, ms, "actuator DOWN")
                null -> CheckResult(CheckOutcome.FAILURE, ms, "no status")
                else -> CheckResult(CheckOutcome.DEGRADED, ms, "actuator $status")
            }
        } catch (e: Exception) {
            CheckResult(CheckOutcome.FAILURE, elapsedMs(start), e.javaClass.simpleName)
        }
    }

    companion object {
        private val STATUS_RE = Regex(""""status"\s*:\s*"([^"]+)"""")
    }
}
