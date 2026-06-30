package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.detail.MetricRef
import io.stevelabs.ticker.server.detail.Unit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(MetricAlertController::class)
class MetricAlertControllerTest(@Autowired val mvc: MockMvc) {

    @TestConfiguration
    class Beans {
        @Bean
        fun metricAlertStore(): MetricAlertStore = MetricAlertStore()
    }

    @Test fun `GET rules returns the seeded default rules`() {
        mvc.perform(get("/api/alerts/rules"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[0].key").value("cpu-process"))
            .andExpect(jsonPath("$[0].threshold").value(0.80))
            .andExpect(jsonPath("$[0].comparator").value("GT"))
            .andExpect(jsonPath("$[0].enabled").value(true))
    }

    @Test fun `PUT rule updates enabled and threshold`() {
        mvc.perform(
            put("/api/alerts/rules/cpu-process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":false,"threshold":0.70}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("cpu-process"))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.threshold").value(0.70))
    }

    @Test fun `PUT unknown key returns 404 with RULE_NOT_FOUND code`() {
        mvc.perform(
            put("/api/alerts/rules/no-such-rule")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RULE_NOT_FOUND"))
    }

    @Test fun `PUT invalid threshold returns 400 with INVALID_THRESHOLD code`() {
        mvc.perform(
            put("/api/alerts/rules/cpu-process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"threshold":1.5}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_THRESHOLD"))
    }

    @Test fun `GET recent returns empty list when no fires recorded`() {
        mvc.perform(get("/api/alerts/recent"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
