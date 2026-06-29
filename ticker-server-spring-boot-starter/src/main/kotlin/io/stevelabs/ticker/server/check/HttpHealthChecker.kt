package io.stevelabs.ticker.server.check

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.target.Target
import org.springframework.web.client.RestClient

/** Generic HTTP liveness: GET the url, success = 2xx within timeout. retrieve() throws on non-2xx -> FAILURE. */
class HttpHealthChecker(private val restClient: RestClient, private val props: PollProperties) : HealthChecker {
    override fun supports(type: ServiceType) = type == ServiceType.HTTP

    override fun check(target: Target): CheckResult {
        val start = System.nanoTime()
        return try {
            restClient.get().uri(target.url).retrieve().toBodilessEntity()
            val ms = elapsedMs(start)
            if (ms > props.degradedLatencyMs) CheckResult(CheckOutcome.DEGRADED, ms, "slow ${ms}ms")
            else CheckResult(CheckOutcome.SUCCESS, ms)
        } catch (e: Exception) {
            CheckResult(CheckOutcome.FAILURE, elapsedMs(start), e.javaClass.simpleName)
        }
    }
}
