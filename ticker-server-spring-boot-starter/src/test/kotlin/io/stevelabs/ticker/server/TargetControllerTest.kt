package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(TargetController::class)
class TargetControllerTest(@Autowired val mvc: MockMvc) {

    @TestConfiguration
    class Beans {
        @Bean fun targetRegistry() = TargetRegistry(
            listOf(TargetDefinition(name = "static-svc", type = ServiceType.SPRING, url = "http://static:8080")),
        )
        @Bean fun healthStateStore(registry: TargetRegistry) = HealthStateStore(registry, PollProperties())
    }

    @Test fun `POST registers a target and GET lists it`() {
        mvc.perform(
            post("/api/targets").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"edge","type":"HTTP","url":"http://edge:80"}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("edge"))
            .andExpect(jsonPath("$.source").value("REGISTERED"))
        mvc.perform(get("/api/targets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id=='edge')]").exists())
    }

    @Test fun `DELETE a registered target returns 204 and removes it`() {
        mvc.perform(
            post("/api/targets").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"temp","type":"HTTP","url":"http://temp:80"}"""),
        ).andExpect(status().isOk)
        mvc.perform(delete("/api/targets/temp")).andExpect(status().isNoContent)
        mvc.perform(get("/api/targets")).andExpect(jsonPath("$[?(@.id=='temp')]").doesNotExist())
    }

    @Test fun `DELETE an unknown target returns 404 with error envelope`() {
        mvc.perform(delete("/api/targets/ghost"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"))
    }

    @Test fun `DELETE a static target returns 409`() {
        mvc.perform(delete("/api/targets/static-svc"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TARGET_STATIC"))
    }

    @Test fun `malformed POST body returns 400 with error envelope`() {
        mvc.perform(post("/api/targets").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }
}
