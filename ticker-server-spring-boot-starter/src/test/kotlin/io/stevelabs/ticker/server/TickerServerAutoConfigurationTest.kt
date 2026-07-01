package io.stevelabs.ticker.server

import io.stevelabs.ticker.server.poll.Poller
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.UiTargetStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.ObjectMapper

class TickerServerAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration::class.java,
                TickerServerAutoConfiguration::class.java,
            ),
        )

    @Test fun `wires collector beans by default`() {
        runner.run {
            assertThat(it).hasSingleBean(HealthStateStore::class.java)
            assertThat(it).hasSingleBean(Poller::class.java)
            assertThat(it).hasSingleBean(ServiceController::class.java)
            assertThat(it).hasSingleBean(TargetController::class.java)
            assertThat(it).hasSingleBean(DetailController::class.java)
            assertThat(it).hasSingleBean(UiTargetStore::class.java)
            assertThat(it).hasSingleBean(ObjectMapper::class.java)
        }
    }

    @Test fun `backs off when ticker server disabled`() {
        runner.withPropertyValues("ticker.server.enabled=false").run {
            assertThat(it).doesNotHaveBean(Poller::class.java)
        }
    }
}
