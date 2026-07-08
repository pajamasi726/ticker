package io.stevelabs.ticker.server.history

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** History OFF: every endpoint still answers friendly (the 0.2.1 no-404 rule). */
@WebMvcTest(HistoryOpsController::class)
class HistoryOpsControllerTest(@Autowired val mvc: MockMvc) {

    @TestConfiguration
    class Beans {
        @Bean
        fun historyOpsController(): HistoryOpsController =
            HistoryOpsController(HistoryProperties(enabled = false), repository = null, backupService = null)
    }

    @Test fun `stats answers enabled=false instead of 404 when history is off`() {
        mvc.perform(get("/api/history/stats"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
    }

    @Test fun `backup explains that history is disabled`() {
        mvc.perform(post("/api/history/backup"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("HISTORY_DISABLED"))
    }

    @Test fun `backups list is empty, not an error`() {
        mvc.perform(get("/api/history/backups"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test fun `download of any name is a clean 404`() {
        mvc.perform(get("/api/history/backups/{name}", "ticker-history-20260709-120000.zip"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("BACKUP_NOT_FOUND"))
    }
}
