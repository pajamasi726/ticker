package io.stevelabs.ticker.server.detail

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetSource
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClient

class MetricFetcherTest {
    private lateinit var server: MockWebServer
    private lateinit var restClient: RestClient
    private val paths = mutableListOf<String>()

    @BeforeEach fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.path!!
                return if (request.path!!.startsWith("/actuator/metrics/jvm.threads.live")) {
                    MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                        .setBody("""{"name":"jvm.threads.live","measurements":[{"statistic":"VALUE","value":42.0}],"availableTags":[]}""")
                } else {
                    MockResponse().setResponseCode(404) // every other whitelisted metric is "missing"
                }
            }
        }
        server.start()
        restClient = RestClient.builder().build()
    }

    @AfterEach fun teardown() = server.shutdown()

    private fun target() = Target("t", "t", ServiceType.SPRING, server.url("/").toString().trimEnd('/'), emptyList(), TargetSource.STATIC)

    @Test fun `returns reachable metrics and skips missing ones`() {
        val props = DetailProperties(metrics = listOf(MetricSpec("jvm.threads.live"), MetricSpec("process.uptime")))
        val result = MetricFetcher(restClient, props).fetch(target())
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("jvm.threads.live")
        assertThat(result[0].measurements["VALUE"]).isEqualTo(42.0)
    }

    @Test fun `only ever calls actuator metrics paths (guardrail 4)`() {
        val props = DetailProperties(metrics = listOf(MetricSpec("jvm.threads.live"), MetricSpec("process.uptime")))
        MetricFetcher(restClient, props).fetch(target())
        assertThat(paths).isNotEmpty()
        assertThat(paths).allSatisfy { assertThat(it).startsWith("/actuator/metrics/") }
    }

    @Test fun `an unreachable target yields an empty list without throwing`() {
        server.shutdown() // nothing listening
        val props = DetailProperties(metrics = listOf(MetricSpec("jvm.threads.live")))
        assertThat(MetricFetcher(restClient, props).fetch(target())).isEmpty()
    }

    @Test fun `appends the tag query when a MetricSpec has one`() {
        val props = DetailProperties(metrics = listOf(MetricSpec("jvm.threads.live", "area:heap")))
        MetricFetcher(restClient, props).fetch(target())
        assertThat(paths).anySatisfy { assertThat(it).isEqualTo("/actuator/metrics/jvm.threads.live?tag=area:heap") }
    }

    @Test fun `MetricSpec rejects path-traversal names`() {
        assertThrows<IllegalArgumentException> { MetricSpec("../env") }
        assertThrows<IllegalArgumentException> { MetricSpec("a/b") }
    }

    @Test fun `MetricSpec accepts a normal Micrometer name`() {
        val spec = MetricSpec("jvm.memory.used", "area:heap")
        assertThat(spec.name).isEqualTo("jvm.memory.used")
    }

    @Test fun `default DetailProperties does not throw (all default names are valid)`() {
        assertThat(DetailProperties().metrics).isNotEmpty()
    }

    @Test fun `default whitelist fetch stays inside actuator metrics prefix (guardrail 4 full defaults)`() {
        MetricFetcher(restClient, DetailProperties()).fetch(target())
        assertThat(paths).isNotEmpty()
        assertThat(paths).allSatisfy { assertThat(it).startsWith("/actuator/metrics/") }
    }
}
