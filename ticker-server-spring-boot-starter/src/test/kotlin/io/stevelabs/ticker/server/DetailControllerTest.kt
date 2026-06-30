package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.detail.Render
import io.stevelabs.ticker.server.detail.ResolvedGroup
import io.stevelabs.ticker.server.detail.ResolvedWidget
import io.stevelabs.ticker.server.detail.Unit
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
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
class DetailControllerTest(@Autowired val mvc: MockMvc) {

    @TestConfiguration
    class Beans {
        @Bean fun targetRegistry() = TargetRegistry(
            listOf(
                TargetDefinition("spring-svc", ServiceType.SPRING, "http://spring-svc:8080"),
                TargetDefinition("http-svc", ServiceType.HTTP, "http://http-svc"),
            ),
        )
        @Bean fun healthStateStore(registry: TargetRegistry) = HealthStateStore(registry, PollProperties())
        @Bean fun metricSource() = object : MetricSource {
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
        }
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
}
