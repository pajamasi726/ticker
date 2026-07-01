package io.stevelabs.ticker.server.history

import com.zaxxer.hikari.HikariDataSource
import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.DetailProperties
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(MetricHistoryController::class)
class MetricHistoryControllerTest(@Autowired val mvc: MockMvc) {

    @TestConfiguration
    class Beans {
        @Bean
        fun targetRegistry() = TargetRegistry(
            listOf(TargetDefinition("spring-svc", ServiceType.SPRING, "http://spring-svc:8080")),
        )

        @Bean fun detailProperties() = DetailProperties()
        @Bean fun historyProperties() = HistoryProperties(enabled = true)

        @Bean
        fun metricHistoryRepository(): MetricHistoryRepository {
            val ds = HikariDataSource().apply {
                jdbcUrl = "jdbc:h2:mem:ctrl-test;DB_CLOSE_DELAY=-1"
                maximumPoolSize = 2
            }
            val repo = MetricHistoryRepository(JdbcTemplate(ds))
            repo.ensureSchema(HistoryDb.H2)
            // Seed one data point so the "returns points" test has something to return
            repo.saveAll("spring-svc", listOf("heap-used" to 512_000_000.0), System.currentTimeMillis() - 30_000)
            return repo
        }
    }

    @Test
    fun `metric-history for known key returns shape`() {
        mvc.perform(
            get("/api/services/spring-svc/metric-history")
                .param("key", "heap-used")
                .param("range", "1h"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.range").value("1h"))
            .andExpect(jsonPath("$.from").isNumber)
            .andExpect(jsonPath("$.to").isNumber)
            .andExpect(jsonPath("$.bucketMs").isNumber)
            .andExpect(jsonPath("$.points").isArray)
    }

    @Test
    fun `metric-history for unknown key returns 400 METRIC_NOT_ALLOWED`() {
        mvc.perform(
            get("/api/services/spring-svc/metric-history")
                .param("key", "env.secrets")
                .param("range", "1h"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("METRIC_NOT_ALLOWED"))
    }

    @Test
    fun `metric-history for bad range returns 400 BAD_RANGE`() {
        mvc.perform(
            get("/api/services/spring-svc/metric-history")
                .param("key", "heap-used")
                .param("range", "99d"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_RANGE"))
    }

    @Test
    fun `metric-history for unknown id returns 404 SERVICE_NOT_FOUND`() {
        mvc.perform(
            get("/api/services/ghost/metric-history")
                .param("key", "heap-used")
                .param("range", "1h"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"))
    }
}
