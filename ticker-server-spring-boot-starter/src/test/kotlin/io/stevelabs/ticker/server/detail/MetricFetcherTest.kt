package io.stevelabs.ticker.server.detail

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.poll.PollProperties
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
import org.springframework.web.client.RestClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MetricFetcherTest {
    private lateinit var server: MockWebServer
    private lateinit var restClient: RestClient
    private lateinit var executor: ExecutorService
    private val paths = mutableListOf<String>()

    @BeforeEach fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                paths += path
                val ok = { stats: String ->
                    MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                        .setBody("""{"name":"m","measurements":[$stats],"availableTags":[]}""")
                }
                return when {
                    path == "/actuator/metrics" -> MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                        .setBody("""{"names":["jvm.threads.live","jvm.memory.used","jvm.memory.max","http.server.requests","neg.gauge"]}""")
                    path.startsWith("/actuator/metrics/jvm.threads.live") -> ok("""{"statistic":"VALUE","value":42.0}""")
                    path.startsWith("/actuator/metrics/neg.gauge") -> ok("""{"statistic":"VALUE","value":-1.0}""")
                    path.startsWith("/actuator/metrics/jvm.memory.used") -> ok("""{"statistic":"VALUE","value":100.0}""")
                    path.startsWith("/actuator/metrics/jvm.memory.max") -> ok("""{"statistic":"VALUE","value":200.0}""")
                    path.startsWith("/actuator/metrics/http.server.requests") ->
                        ok("""{"statistic":"COUNT","value":10.0},{"statistic":"TOTAL_TIME","value":5.0}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        restClient = RestClient.builder().build()
        executor = Executors.newVirtualThreadPerTaskExecutor()
    }

    @AfterEach fun teardown() {
        server.shutdown()
        executor.close()
    }

    private fun target() = Target("t", "t", ServiceType.SPRING, server.url("/").toString().trimEnd('/'), emptyList(), TargetSource.STATIC)

    private fun fetcher(dashboard: List<GroupSpec>) =
        MetricFetcher(restClient, DetailProperties(dashboard = dashboard), executor, PollProperties())

    private val sampleDashboard = listOf(
        GroupSpec(
            "G1",
            listOf(
                WidgetSpec("threads", "Threads", "jvm.threads.live", render = Render.CHART, unit = Unit.COUNT),
                WidgetSpec("heap", "Heap", "jvm.memory.used", tags = mapOf("area" to "heap"), render = Render.GAUGE, unit = Unit.BYTES, max = MetricRef("jvm.memory.max", mapOf("area" to "heap"))),
                WidgetSpec("mean", "Latency", "http.server.requests", statistic = "MEAN", render = Render.CHART, unit = Unit.SECONDS),
                WidgetSpec("missing", "Gone", "absent.metric", render = Render.NUMBER, unit = Unit.COUNT),
            ),
        ),
        GroupSpec(
            "EmptyGroup",
            listOf(WidgetSpec("gone", "Gone", "absent.metric", render = Render.NUMBER, unit = Unit.COUNT)),
        ),
    )

    @Test fun `keeps every group and widget, marking absent metrics unavailable`() {
        val groups = fetcher(sampleDashboard).fetch(target())
        assertThat(groups.map { it.title }).containsExactly("G1", "EmptyGroup") // nothing omitted
        val byKey = groups[0].widgets.associateBy { it.key }
        assertThat(byKey.keys).containsExactlyInAnyOrder("threads", "heap", "mean", "missing")
        assertThat(byKey.getValue("threads").value).isEqualTo(42.0)
        assertThat(byKey.getValue("threads").available).isTrue()
        // Absent metric: present as a dimmed placeholder, not dropped.
        assertThat(byKey.getValue("missing").available).isFalse()
        assertThat(byKey.getValue("missing").value).isNull()
        assertThat(groups[1].widgets.single().available).isFalse() // EmptyGroup's only widget is absent
    }

    @Test fun `computes MEAN as TOTAL_TIME over COUNT`() {
        val groups = fetcher(sampleDashboard).fetch(target())
        val mean = groups[0].widgets.first { it.key == "mean" }
        assertThat(mean.value).isEqualTo(0.5) // 5.0 / 10.0
    }

    @Test fun `resolves a gauge max from its paired MetricRef`() {
        val groups = fetcher(sampleDashboard).fetch(target())
        val heap = groups[0].widgets.first { it.key == "heap" }
        assertThat(heap.value).isEqualTo(100.0)
        assertThat(heap.max).isEqualTo(200.0)
    }

    @Test fun `defaults a PERCENT gauge max to 1 when no MetricRef is set`() {
        val dashboard = listOf(
            GroupSpec("G", listOf(WidgetSpec("cpu", "CPU", "jvm.memory.used", render = Render.GAUGE, unit = Unit.PERCENT))),
        )
        // jvm.memory.used returns VALUE 100; render=GAUGE + unit=PERCENT + no max -> max defaults to 1.0
        val cpu = fetcher(dashboard).fetch(target())[0].widgets.first()
        assertThat(cpu.max).isEqualTo(1.0)
    }

    @Test fun `treats a negative value (-1 not-applicable) as unavailable`() {
        val dashboard = listOf(GroupSpec("G", listOf(WidgetSpec("neg", "Neg", "neg.gauge", render = Render.NUMBER, unit = Unit.COUNT))))
        val w = fetcher(dashboard).fetch(target())[0].widgets.single()
        assertThat(w.available).isFalse()
        assertThat(w.value).isNull()
    }

    @Test fun `only ever calls actuator metrics paths (guardrail 4)`() {
        fetcher(sampleDashboard).fetch(target())
        assertThat(paths).isNotEmpty()
        // Covers both the names list (/actuator/metrics) and per-metric GETs (/actuator/metrics/{name}).
        assertThat(paths).allSatisfy { assertThat(it).startsWith("/actuator/metrics") }
        assertThat(paths).noneMatch { it.contains("absent.metric") } // absent metric skipped, never GET'd
    }

    @Test fun `default dashboard fetch stays inside the actuator metrics prefix (guardrail 4 full defaults)`() {
        MetricFetcher(restClient, DetailProperties(), executor, PollProperties()).fetch(target())
        assertThat(paths).isNotEmpty()
        assertThat(paths).allSatisfy { assertThat(it).startsWith("/actuator/metrics") }
    }

    @Test fun `a non-SPRING target yields empty groups without any HTTP call`() {
        val http = Target("h", "h", ServiceType.HTTP, server.url("/").toString().trimEnd('/'), emptyList(), TargetSource.STATIC)
        assertThat(fetcher(sampleDashboard).fetch(http)).isEmpty()
        assertThat(paths).isEmpty()
    }

    @Test fun `an unreachable target yields empty groups without throwing`() {
        server.shutdown() // nothing listening
        assertThat(fetcher(sampleDashboard).fetch(target())).isEmpty()
    }
}
