package io.stevelabs.ticker.client

import io.stevelabs.ticker.core.ServiceType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.concurrent.TimeUnit

class TickerClientRegistrarTest {
    private lateinit var server: MockWebServer
    private lateinit var restClient: RestClient

    @BeforeEach fun setup() {
        server = MockWebServer()
        server.start()
        restClient = RestClient.builder().build()
    }

    @AfterEach fun tearDown() {
        server.shutdown()
    }

    private fun registrar(props: TickerClientProperties, attempts: Int = 3) =
        TickerClientRegistrar(props, MockEnvironment(), restClient, maxAttempts = attempts, retryDelayMs = 0)

    private fun base() = server.url("/").toString().trimEnd('/')

    @Test fun `POSTs a registration to the collector on ready`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val props = TickerClientProperties(
            collectorUrl = base(), url = "http://my-app:8081", name = "my-app",
            type = ServiceType.SPRING, tags = listOf("team-a"),
        )
        registrar(props).onApplicationReady()

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/targets")
        val body = recorded.body.readUtf8()
        assertThat(body).contains("\"name\":\"my-app\"")
        assertThat(body).contains("\"url\":\"http://my-app:8081\"")
        assertThat(body).contains("\"type\":\"SPRING\"")
        assertThat(body).contains("\"tags\":[\"team-a\"]")
    }

    @Test fun `falls back to spring application name when name is unset`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val env = MockEnvironment().withProperty("spring.application.name", "env-app")
        val props = TickerClientProperties(collectorUrl = base(), url = "http://my-app:8081", name = null)
        TickerClientRegistrar(props, env, restClient, maxAttempts = 1, retryDelayMs = 0).onApplicationReady()
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"name\":\"env-app\"")
    }

    @Test fun `skips registration when url is not set`() {
        registrar(TickerClientProperties(collectorUrl = base(), url = null, name = "x")).onApplicationReady()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun `skips registration when collector-url is not set`() {
        registrar(TickerClientProperties(collectorUrl = null, url = "http://my-app:8081", name = "x")).onApplicationReady()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun `retries then gives up without throwing on persistent failure`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        val props = TickerClientProperties(collectorUrl = base(), url = "http://my-app:8081", name = "my-app")
        registrar(props, attempts = 3).onApplicationReady() // must not throw
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test fun `sends periodic heartbeats after the initial registration`() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(200)) } // initial + heartbeats
        val props = TickerClientProperties(
            collectorUrl = base(), url = "http://my-app:8081", name = "my-app",
            heartbeatInterval = Duration.ofMillis(100),
        )
        val reg = TickerClientRegistrar(props, MockEnvironment(), restClient, maxAttempts = 1, retryDelayMs = 0)
        reg.onApplicationReady()
        try {
            val first = server.takeRequest(2, TimeUnit.SECONDS)   // initial
            val second = server.takeRequest(2, TimeUnit.SECONDS)  // first heartbeat
            assertThat(first).isNotNull()
            assertThat(second).isNotNull()
            assertThat(first!!.path).isEqualTo("/api/targets")
            assertThat(second!!.path).isEqualTo("/api/targets")
        } finally {
            reg.destroy()
        }
    }

    @Test fun `does not heartbeat when interval is zero`() {
        server.enqueue(MockResponse().setResponseCode(200)) // initial only
        val props = TickerClientProperties(
            collectorUrl = base(), url = "http://my-app:8081", name = "my-app",
            heartbeatInterval = Duration.ZERO,
        )
        val reg = TickerClientRegistrar(props, MockEnvironment(), restClient, maxAttempts = 1, retryDelayMs = 0)
        reg.onApplicationReady()
        try {
            assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull()          // initial
            assertThat(server.takeRequest(500, TimeUnit.MILLISECONDS)).isNull()      // no heartbeat
        } finally {
            reg.destroy()
        }
    }
}
