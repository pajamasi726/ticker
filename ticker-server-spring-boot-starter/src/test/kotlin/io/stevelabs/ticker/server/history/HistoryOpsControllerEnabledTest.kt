package io.stevelabs.ticker.server.history

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files

/** History ON (in-memory H2 for stats; MYSQL branch for the unsupported-backup message). */
@WebMvcTest(HistoryOpsController::class)
class HistoryOpsControllerEnabledTest(@Autowired val mvc: MockMvc) {

    @TestConfiguration
    class Beans {
        private val tmp = Files.createTempDirectory("ticker-ops-test")

        @Bean
        fun jdbc(): JdbcTemplate =
            JdbcTemplate(DriverManagerDataSource("jdbc:h2:mem:opsctl;DB_CLOSE_DELAY=-1", "sa", "")).also {
                MetricHistoryRepository(it).ensureSchema(HistoryDb.H2)
                MetricHistoryRepository(it).saveAll("t@h:1", listOf("cpu" to 0.4), 42L)
            }

        // MYSQL props so the backup endpoint exercises the friendly UNSUPPORTED_DB path — while the
        // actual jdbc underneath is H2, which stats() is happy with.
        @Bean
        fun historyOpsController(jdbc: JdbcTemplate): HistoryOpsController {
            val props = HistoryProperties(
                enabled = true, db = HistoryDb.MYSQL, url = "jdbc:mysql://ignored/x",
                backup = HistoryProperties.BackupProperties(dir = tmp.toString()),
            )
            return HistoryOpsController(props, MetricHistoryRepository(jdbc), HistoryBackupService(jdbc, props))
        }
    }

    @Test fun `stats reports rows, range and db when enabled`() {
        mvc.perform(get("/api/history/stats"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.db").value("MYSQL"))
            .andExpect(jsonPath("$.rowCount").value(1))
            .andExpect(jsonPath("$.oldestTsMillis").value(42))
            .andExpect(jsonPath("$.backupSupported").value(false))
    }

    @Test fun `backup on MYSQL answers with the native-tool guidance`() {
        mvc.perform(post("/api/history/backup"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_DB"))
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("mysqldump")))
    }
}
