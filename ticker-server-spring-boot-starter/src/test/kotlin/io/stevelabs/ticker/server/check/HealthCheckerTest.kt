package io.stevelabs.ticker.server.check

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetSource
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

class HealthCheckerTest {
    private lateinit var server: MockWebServer
    // Generous threshold: prevents Jackson cold-start or loopback jitter from flipping SUCCESS cases to DEGRADED
    private val successProps = PollProperties(timeout = Duration.ofMillis(500), degradedLatencyMs = 2000L)
    // Tight threshold: used only for slow-response DEGRADED assertions (250ms body delay > 100ms threshold)
    private val slowProps = PollProperties(timeout = Duration.ofMillis(500), degradedLatencyMs = 100L)
    private val restClient: RestClient = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply { setConnectTimeout(500); setReadTimeout(500) })
        .build()

    @BeforeEach fun setUp() { server = MockWebServer(); server.start() }
    @AfterEach fun tearDown() { server.shutdown() }

    private fun target(type: ServiceType) =
        Target(id = "t", name = "t", type = type, url = server.url("/").toString().trimEnd('/'), source = TargetSource.STATIC)

    @Test fun `http 2xx is SUCCESS`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val r = HttpHealthChecker(restClient, successProps).check(target(ServiceType.HTTP))
        assertThat(r.outcome).isEqualTo(CheckOutcome.SUCCESS)
    }

    @Test fun `http 5xx is FAILURE`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val r = HttpHealthChecker(restClient, successProps).check(target(ServiceType.HTTP))
        assertThat(r.outcome).isEqualTo(CheckOutcome.FAILURE)
    }

    @Test fun `http slow response is DEGRADED`() {
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(250, java.util.concurrent.TimeUnit.MILLISECONDS).setBody(" "))
        val r = HttpHealthChecker(restClient, slowProps).check(target(ServiceType.HTTP))
        assertThat(r.outcome).isEqualTo(CheckOutcome.DEGRADED)
    }

    @Test fun `spring actuator UP is SUCCESS`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("""{"status":"UP"}"""))
        val r = SpringHealthChecker(restClient, successProps).check(target(ServiceType.SPRING))
        assertThat(r.outcome).isEqualTo(CheckOutcome.SUCCESS)
    }

    @Test fun `spring actuator UP slow is DEGRADED`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type","application/json")
            .setBodyDelay(250, java.util.concurrent.TimeUnit.MILLISECONDS).setBody("""{"status":"UP"}"""))
        val r = SpringHealthChecker(restClient, slowProps).check(target(ServiceType.SPRING))
        assertThat(r.outcome).isEqualTo(CheckOutcome.DEGRADED)
    }

    @Test fun `spring actuator 503 DOWN is FAILURE`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json").setBody("""{"status":"DOWN"}"""))
        val r = SpringHealthChecker(restClient, successProps).check(target(ServiceType.SPRING))
        assertThat(r.outcome).isEqualTo(CheckOutcome.FAILURE)
    }

    @Test fun `spring 200 with non-UP status is DEGRADED`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("""{"status":"OUT_OF_SERVICE"}"""))
        val r = SpringHealthChecker(restClient, successProps).check(target(ServiceType.SPRING))
        assertThat(r.outcome).isEqualTo(CheckOutcome.DEGRADED)
    }

    @Test fun `unreachable is FAILURE`() {
        server.shutdown()   // nothing listening
        val r = HttpHealthChecker(restClient, successProps).check(target(ServiceType.HTTP))
        assertThat(r.outcome).isEqualTo(CheckOutcome.FAILURE)
    }

    @Test fun `supports matches type`() {
        assertThat(HttpHealthChecker(restClient, successProps).supports(ServiceType.HTTP)).isTrue()
        assertThat(SpringHealthChecker(restClient, successProps).supports(ServiceType.SPRING)).isTrue()
        assertThat(HttpHealthChecker(restClient, successProps).supports(ServiceType.SPRING)).isFalse()
        assertThat(SpringHealthChecker(restClient, successProps).supports(ServiceType.HTTP)).isFalse()
    }
}
