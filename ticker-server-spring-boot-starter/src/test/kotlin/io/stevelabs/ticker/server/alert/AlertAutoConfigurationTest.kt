package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.detail.ResolvedGroup
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class AlertAutoConfigurationTest {
    private val noopMetricSource = object : MetricSource {
        override fun fetch(target: Target): List<ResolvedGroup> = emptyList()
    }

    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AlertAutoConfiguration::class.java))
        .withBean(HealthStateStore::class.java, { HealthStateStore(TargetRegistry(emptyList()), PollProperties()) })
        .withBean(MetricSource::class.java, { noopMetricSource })

    @Test fun `no alert beans by default`() {
        runner.run { ctx -> assertThat(ctx).doesNotHaveBean(AlertService::class.java) }
    }

    @Test fun `alert beans present when enabled with a webhook`() {
        runner.withPropertyValues("ticker.alert.enabled=true", "ticker.alert.slack-webhook-url=http://hook")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(AlertService::class.java)
                assertThat(ctx).hasSingleBean(SlackSender::class.java)
            }
    }

    @Test fun `enabled without a webhook has AlertService but no SlackSender`() {
        runner.withPropertyValues("ticker.alert.enabled=true")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(AlertService::class.java)
                assertThat(ctx).doesNotHaveBean(SlackSender::class.java)
            }
    }
}
