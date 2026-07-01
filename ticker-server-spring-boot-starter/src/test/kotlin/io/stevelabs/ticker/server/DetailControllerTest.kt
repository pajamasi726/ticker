package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.DetailProperties
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.detail.Render
import io.stevelabs.ticker.server.detail.ResolvedGroup
import io.stevelabs.ticker.server.detail.ResolvedWidget
import io.stevelabs.ticker.server.detail.TagStat
import io.stevelabs.ticker.server.detail.Unit
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(DetailController::class)
class DetailControllerTest(@Autowired val mvc: MockMvc, @Autowired val metricSource: CapturingMetricSource) {

    @TestConfiguration
    class Beans {
        @Bean fun targetRegistry() = TargetRegistry(
            listOf(
                TargetDefinition("spring-svc", ServiceType.SPRING, "http://spring-svc:8080"),
                TargetDefinition("http-svc", ServiceType.HTTP, "http://http-svc"),
            ),
        )
        @Bean fun healthStateStore(registry: TargetRegistry) = HealthStateStore(registry, PollProperties())
        @Bean fun detailProperties() = DetailProperties()
        @Bean fun metricSource() = CapturingMetricSource()
    }

    @Test fun `detail for a SPRING target returns grouped widgets`() {
        mvc.perform(get("/api/services/spring-svc/detail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("spring-svc"))
            .andExpect(jsonPath("$.groups[0].title").value("Threads"))
            .andExpect(jsonPath("$.groups[0].widgets[0].key").value("threads-live"))
            .andExpect(jsonPath("$.groups[0].widgets[0].render").value("CHART"))
            .andExpect(jsonPath("$.groups[0].widgets[0].value").value(42.0))
    }

    @Test fun `detail for an HTTP target has empty groups`() {
        mvc.perform(get("/api/services/http-svc/detail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.groups").isEmpty)
    }

    @Test fun `detail for an unknown id is 404 with error envelope`() {
        mvc.perform(get("/api/services/ghost/detail"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"))
    }

    // --- metric-breakdown ---

    @Test fun `metric-breakdown for whitelisted metric and tag returns stub rows`() {
        mvc.perform(get("/api/services/spring-svc/metric-breakdown")
            .param("metric", "http.server.requests")
            .param("tag", "uri"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].value").value("/api/foo"))
            .andExpect(jsonPath("$[0].count").value(100.0))
            .andExpect(jsonPath("$[0].mean").value(0.05))
            .andExpect(jsonPath("$[0].max").value(0.2))
    }

    @Test fun `metric-breakdown for unknown service id returns 404`() {
        mvc.perform(get("/api/services/ghost/metric-breakdown")
            .param("metric", "http.server.requests")
            .param("tag", "uri"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"))
    }

    @Test fun `metric-breakdown for non-whitelisted metric returns 400`() {
        mvc.perform(get("/api/services/spring-svc/metric-breakdown")
            .param("metric", "env.secrets")
            .param("tag", "uri"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("METRIC_NOT_ALLOWED"))
    }

    @Test fun `metric-breakdown for disallowed tag returns 400`() {
        mvc.perform(get("/api/services/spring-svc/metric-breakdown")
            .param("metric", "http.server.requests")
            .param("tag", "configKey"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("TAG_NOT_ALLOWED"))
    }

    @Test fun `metric-breakdown with filter passes filter map to source and returns rows`() {
        mvc.perform(get("/api/services/spring-svc/metric-breakdown")
            .param("metric", "http.server.requests")
            .param("tag", "uri")
            .param("filter", "outcome:CLIENT_ERROR"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].value").value("/api/foo"))
        assertThat(metricSource.lastFilter).isEqualTo(mapOf("outcome" to "CLIENT_ERROR"))
    }

    @Test fun `metric-breakdown with disallowed filter tag returns 400`() {
        mvc.perform(get("/api/services/spring-svc/metric-breakdown")
            .param("metric", "http.server.requests")
            .param("tag", "uri")
            .param("filter", "badtag:x"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("FILTER_NOT_ALLOWED"))
    }
}

class CapturingMetricSource : MetricSource {
    var lastFilter: Map<String, String>? = null

    override fun fetch(target: Target): List<ResolvedGroup> =
        if (target.type == ServiceType.SPRING) {
            listOf(
                ResolvedGroup(
                    "Threads",
                    listOf(ResolvedWidget("threads-live", "Live", Render.CHART, Unit.COUNT, 42.0, null, false)),
                ),
            )
        } else {
            emptyList()
        }

    override fun tagBreakdown(target: Target, metricName: String, tag: String, filter: Map<String, String>): List<TagStat> {
        lastFilter = filter
        return listOf(TagStat("/api/foo", 100.0, 0.05, 0.2))
    }
}
