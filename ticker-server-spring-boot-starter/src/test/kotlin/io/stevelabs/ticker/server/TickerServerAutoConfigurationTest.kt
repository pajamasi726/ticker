package io.stevelabs.ticker.server

import io.stevelabs.ticker.server.poll.Poller
import io.stevelabs.ticker.server.state.HealthStateStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class TickerServerAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TickerServerAutoConfiguration::class.java))

    @Test fun `wires collector beans by default`() {
        runner.run {
            assertThat(it).hasSingleBean(HealthStateStore::class.java)
            assertThat(it).hasSingleBean(Poller::class.java)
            assertThat(it).hasSingleBean(ServiceController::class.java)
            assertThat(it).hasSingleBean(TargetController::class.java)
            assertThat(it).hasSingleBean(DetailController::class.java)
        }
    }

    @Test fun `backs off when ticker server disabled`() {
        runner.withPropertyValues("ticker.server.enabled=false").run {
            assertThat(it).doesNotHaveBean(Poller::class.java)
        }
    }
}
