package io.stevelabs.ticker.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ServiceController::class)
@Import(MockServices::class)
class ServiceControllerTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `GET api services returns the mock services across all four states`() {
        mockMvc.get("/api/services")
            .andExpect { status { isOk() } }
            .andExpect { content { contentType("application/json") } }
            .andExpect { jsonPath("$.length()") { value(7) } }
            // contract fields (camelCase) on the first tile
            .andExpect { jsonPath("$[0].id") { value("payment-api") } }
            .andExpect { jsonPath("$[0].type") { value("SPRING") } }
            .andExpect { jsonPath("$[0].state") { value("UP") } }
            .andExpect { jsonPath("$[0].latencyMs") { value(42) } }
            .andExpect { jsonPath("$[0].tags[0]") { value("payments") } }
            .andExpect { jsonPath("$[0].sparkline.length()") { value(6) } }
            // every non-UP state is represented somewhere in the set
            .andExpect { jsonPath("$[3].state") { value("DEGRADED") } }
            .andExpect { jsonPath("$[5].state") { value("DOWN") } }
            .andExpect { jsonPath("$[6].state") { value("UNKNOWN") } }
            // null latencyMs serializes as JSON null for a DOWN service (batch-worker)
            .andExpect { jsonPath("$[5].latencyMs") { value(null as Any?) } }
            // sparkline null gap — batch-worker's 3rd sample (index 2) is a failed-poll gap
            .andExpect { jsonPath("$[5].sparkline[2]") { value(null as Any?) } }
            // HTTP type row is present (edge-nginx)
            .andExpect { jsonPath("$[4].type") { value("HTTP") } }
    }
}
