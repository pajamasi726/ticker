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
            .andExpect(jsonPath("$.id").value("edge@edge:80"))
            .andExpect(jsonPath("$.name").value("edge"))
            .andExpect(jsonPath("$.instance").value("edge:80"))
            .andExpect(jsonPath("$.source").value("REGISTERED"))
        mvc.perform(get("/api/targets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name=='edge')]").exists())
    }

    @Test fun `DELETE a registered target returns 204 and removes it`() {
        mvc.perform(
            post("/api/targets").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"temp","type":"HTTP","url":"http://temp:80"}"""),
        ).andExpect(status().isOk)
        mvc.perform(delete("/api/targets/{id}", "temp@temp:80")).andExpect(status().isNoContent)
        mvc.perform(get("/api/targets")).andExpect(jsonPath("$[?(@.name=='temp')]").doesNotExist())
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

    // ---- POST /api/targets/http tests ----

    @Test fun `POST http with valid name and url returns 201 with source UI and type HTTP`() {
        mvc.perform(
            post("/api/targets/http").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"ui-monitor","url":"https://example.com/health"}"""),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("ui-monitor"))
            .andExpect(jsonPath("$.source").value("UI"))
            .andExpect(jsonPath("$.type").value("HTTP"))
    }

    @Test fun `POST http with blank name returns 400 with INVALID_REQUEST`() {
        mvc.perform(
            post("/api/targets/http").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"  ","url":"https://example.com/health"}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test fun `POST http with blank url returns 400 with INVALID_REQUEST`() {
        mvc.perform(
            post("/api/targets/http").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"my-svc","url":""}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test fun `POST http without http(s) scheme returns 400 with INVALID_REQUEST`() {
        mvc.perform(
            post("/api/targets/http").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"my-svc","url":"ftp://example.com"}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test fun `POST http with duplicate name returns 409 with TARGET_NAME_TAKEN`() {
        mvc.perform(
            post("/api/targets/http").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"dup-monitor","url":"https://example.com"}"""),
        ).andExpect(status().isCreated)

        mvc.perform(
            post("/api/targets/http").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"dup-monitor","url":"https://other.com"}"""),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TARGET_NAME_TAKEN"))
    }

    @Test fun `POST http with name colliding with static target returns 409`() {
        mvc.perform(
            post("/api/targets/http").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"static-svc","url":"https://example.com"}"""),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TARGET_NAME_TAKEN"))
    }

    @Test fun `DELETE a UI target returns 204`() {
        mvc.perform(
            post("/api/targets/http").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"ui-to-delete","url":"https://example.com"}"""),
        ).andExpect(status().isCreated)
        mvc.perform(delete("/api/targets/ui-to-delete")).andExpect(status().isNoContent)
        mvc.perform(get("/api/targets")).andExpect(jsonPath("$[?(@.id=='ui-to-delete')]").doesNotExist())
    }
}
