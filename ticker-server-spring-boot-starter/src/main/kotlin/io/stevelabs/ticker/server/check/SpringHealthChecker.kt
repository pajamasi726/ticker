package io.stevelabs.ticker.server.check

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.target.Target
import org.springframework.web.client.RestClient

/** Spring actuator liveness: GET {url}/actuator/health, read the top-level status via Jackson. */
class SpringHealthChecker(private val restClient: RestClient, private val props: PollProperties) : HealthChecker {
    override fun supports(type: ServiceType) = type == ServiceType.SPRING

    override fun check(target: Target): CheckResult {
        val start = System.nanoTime()
        return try {
            val body = restClient.get().uri("${target.url.trimEnd('/')}/actuator/health")
                .retrieve().body(HealthBody::class.java)
            val ms = elapsedMs(start)
            when (body?.status?.uppercase()) {
                "UP"   -> if (ms > props.degradedLatencyMs) CheckResult(CheckOutcome.DEGRADED, ms, "slow ${ms}ms") else CheckResult(CheckOutcome.SUCCESS, ms)
                "DOWN" -> CheckResult(CheckOutcome.FAILURE, ms, "actuator DOWN")
                null   -> CheckResult(CheckOutcome.FAILURE, ms, "no status")
                else   -> CheckResult(CheckOutcome.DEGRADED, ms, "actuator ${body.status}")
            }
        } catch (e: Exception) {
            CheckResult(CheckOutcome.FAILURE, elapsedMs(start), e.javaClass.simpleName)
        }
    }

    private data class HealthBody(val status: String? = null)
}
