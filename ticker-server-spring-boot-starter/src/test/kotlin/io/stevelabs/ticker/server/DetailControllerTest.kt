package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.detail.MetricValue
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
            override fun fetch(target: Target): List<MetricValue> =
                if (target.type == ServiceType.SPRING) listOf(MetricValue("jvm.threads.live", null, mapOf("VALUE" to 12.0))) else emptyList()
        }
    }

    @Test fun `detail for a SPRING target returns metrics`() {
        mvc.perform(get("/api/services/spring-svc/detail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("spring-svc"))
            .andExpect(jsonPath("$.metrics[0].name").value("jvm.threads.live"))
            .andExpect(jsonPath("$.metrics[0].measurements.VALUE").value(12.0))
    }

    @Test fun `detail for an HTTP target has no metrics`() {
        mvc.perform(get("/api/services/http-svc/detail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.metrics").isEmpty)
    }

    @Test fun `detail for an unknown id is 404 with error envelope`() {
        mvc.perform(get("/api/services/ghost/detail"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"))
    }
}
