package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.check.CheckOutcome
import io.stevelabs.ticker.server.check.CheckResult
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.beans.factory.annotation.Autowired
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ServiceController::class)
@Import(ServiceControllerTest.SeededStore::class)
class ServiceControllerTest(@Autowired val mockMvc: MockMvc) {

    @TestConfiguration
    class SeededStore {
        @Bean fun store(): HealthStateStore {
            val registry = TargetRegistry(listOf(
                TargetDefinition("payment-api", ServiceType.SPRING, "http://payment-api", listOf("payments")),
                TargetDefinition("edge", ServiceType.HTTP, "http://edge"),
            ))
            return HealthStateStore(registry, PollProperties()).apply {
                record("payment-api", CheckResult(CheckOutcome.SUCCESS, 42))
                record("edge", CheckResult(CheckOutcome.FAILURE, 0))
            }
        }
    }

    @Test fun `GET api services returns live state`() {
        mockMvc.get("/api/services")
            .andExpect { status { isOk() } }
            .andExpect { content { contentType("application/json") } }
            .andExpect { jsonPath("$.length()") { value(2) } }
            .andExpect { jsonPath("$[0].id") { value("payment-api") } }
            .andExpect { jsonPath("$[0].type") { value("SPRING") } }
            .andExpect { jsonPath("$[0].state") { value("UP") } }
            .andExpect { jsonPath("$[0].latencyMs") { value(42) } }
            .andExpect { jsonPath("$[0].tags[0]") { value("payments") } }
            .andExpect { jsonPath("$[0].source") { value("STATIC") } }
            // 'edge' had 1 failure; default threshold 3 -> still UNKNOWN (debounce), latency null
            .andExpect { jsonPath("$[1].id") { value("edge") } }
            .andExpect { jsonPath("$[1].state") { value("UNKNOWN") } }
            .andExpect { jsonPath("$[1].latencyMs") { value(null as Any?) } }
            .andExpect { jsonPath("$[1].source") { value("STATIC") } }
    }
}
