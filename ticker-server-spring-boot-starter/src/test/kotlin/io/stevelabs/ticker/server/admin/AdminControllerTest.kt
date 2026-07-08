package io.stevelabs.ticker.server.admin

import io.stevelabs.ticker.core.RegistrationRequest
import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.TickerServerProperties
import io.stevelabs.ticker.server.history.HistoryProperties
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminController::class)
class AdminControllerTest(@Autowired val mvc: MockMvc) {

    companion object {
        private const val SECRET_WEBHOOK = "https://hooks.slack.example/services/SECRET"
    }

    @TestConfiguration
    class Beans {
        @Bean
        fun environment(): Environment = MockEnvironment()
            .withProperty("ticker.alert.enabled", "true")
            .withProperty("ticker.alert.slack-webhook-url", SECRET_WEBHOOK)

        @Bean
        fun registry(): TargetRegistry =
            TargetRegistry(listOf(TargetDefinition("edge-nginx", ServiceType.HTTP, "http://edge/healthz"))).also {
                it.register(
                    RegistrationRequest(
                        name = "orders-api", type = ServiceType.SPRING, url = "http://10.0.0.7:8081",
                        instance = "pod-a", ip = "10.0.0.7",
                    ),
                    nowMillis = 1_720_000_000_000,
                )
            }

        @Bean
        fun adminController(registry: TargetRegistry, environment: Environment): AdminController =
            AdminController(
                server = TickerServerProperties(publicUrl = "https://ops.example/ticker"),
                poll = PollProperties(),
                history = HistoryProperties(enabled = true),
                registry = registry,
                environment = environment,
            )
    }

    @Test fun `info reports config as facts and secrets only as booleans`() {
        mvc.perform(get("/api/admin/info"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").exists())
            .andExpect(jsonPath("$.uptimeMillis").isNumber)
            .andExpect(jsonPath("$.poll.failureThreshold").value(3))
            .andExpect(jsonPath("$.server.publicUrlConfigured").value(true))
            .andExpect(jsonPath("$.alert.enabled").value(true))
            .andExpect(jsonPath("$.alert.webhookConfigured").value(true))
            .andExpect(jsonPath("$.history.enabled").value(true))
            .andExpect(content().string(not(containsString("SECRET")))) // guardrail #5
    }

    @Test fun `targets carry source and heartbeat for self-registered instances only`() {
        mvc.perform(get("/api/admin/targets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name=='edge-nginx')].source").value("STATIC"))
            .andExpect(jsonPath("$[?(@.name=='edge-nginx')].lastSeenMillis").value(null))
            .andExpect(jsonPath("$[?(@.name=='orders-api')].source").value("REGISTERED"))
            .andExpect(jsonPath("$[?(@.name=='orders-api')].lastSeenMillis").value(1_720_000_000_000))
            .andExpect(jsonPath("$[?(@.name=='orders-api')].instance").value("pod-a:8081"))
    }
}
