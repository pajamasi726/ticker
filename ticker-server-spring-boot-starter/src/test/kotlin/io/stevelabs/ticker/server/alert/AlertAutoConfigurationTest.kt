package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.config.TickerConfig
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
        .withConfiguration(AutoConfigurations.of(AlertApiAutoConfiguration::class.java, AlertAutoConfiguration::class.java))
        .withBean(HealthStateStore::class.java, { HealthStateStore(TargetRegistry(emptyList()), PollProperties()) })
        .withBean(MetricSource::class.java, { noopMetricSource })
        .withBean(TickerConfig::class.java, { TickerConfig(emptyList(), MetricAlertRule.DEFAULTS) })

    @Test fun `no dispatch beans by default`() {
        runner.run { ctx -> assertThat(ctx).doesNotHaveBean(AlertService::class.java) }
    }

    @Test fun `rules store, silence and the alerts API stay available with alerting OFF`() {
        // The UI polls /api/alerts/rules + /recent unconditionally (severity colouring needs the
        // thresholds) — an alerts-off collector must answer them, not 404.
        runner.run { ctx ->
            assertThat(ctx).hasSingleBean(MetricAlertController::class.java)
            assertThat(ctx).hasSingleBean(MetricAlertStore::class.java)
            assertThat(ctx).hasSingleBean(AlertSilence::class.java)
            assertThat(ctx).doesNotHaveBean(AlertService::class.java)
            assertThat(ctx).doesNotHaveBean(MetricAlertService::class.java)
        }
    }

    @Test fun `enabling alerting reuses the SAME store the API edits`() {
        runner.withPropertyValues("ticker.alert.enabled=true")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(MetricAlertStore::class.java)
                assertThat(ctx.getBean(MetricAlertService::class.java)).isNotNull
                assertThat(ctx).hasSingleBean(MetricAlertController::class.java)
            }
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

    @Test fun `a BLANK webhook counts as unset (templated configs resolve missing env to empty string)`() {
        // e.g. `slack-webhook-url: ${SLACK_WEBHOOK_URL:}` with the env var absent → "" must mean
        // "no Slack", not a SlackSender firing at an empty URL.
        runner.withPropertyValues("ticker.alert.enabled=true", "ticker.alert.slack-webhook-url=")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(AlertService::class.java)
                assertThat(ctx).doesNotHaveBean(SlackSender::class.java)
            }
    }
}
