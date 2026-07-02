package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.config.TickerConfigurer
import io.stevelabs.ticker.server.poll.Poller
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetRegistry
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

    @Test fun `TickerConfigurer bean adds target to registry`() {
        runner
            .withBean(
                TickerConfigurer::class.java,
                { TickerConfigurer { t -> t.addTarget("x", ServiceType.HTTP, "http://x.example.com") } },
            )
            .run { ctx ->
                val registry = ctx.getBean(TargetRegistry::class.java)
                assertThat(registry.all().map { it.name }).contains("x")
            }
    }

    @Test fun `self-requests meter filter denies actuator and own api, respects base-path, opts out`() {
        runner.run {
            val filter = it.getBean(io.micrometer.core.instrument.config.MeterFilter::class.java)
            fun id(uri: String) = io.micrometer.core.instrument.Meter.Id(
                "http.server.requests", io.micrometer.core.instrument.Tags.of("uri", uri), null, null, io.micrometer.core.instrument.Meter.Type.TIMER,
            )
            assertThat(filter.accept(id("/actuator/health"))).isEqualTo(io.micrometer.core.instrument.config.MeterFilterReply.DENY)
            assertThat(filter.accept(id("/api/services"))).isEqualTo(io.micrometer.core.instrument.config.MeterFilterReply.DENY)
            assertThat(filter.accept(id("/orders/{id}"))).isEqualTo(io.micrometer.core.instrument.config.MeterFilterReply.NEUTRAL)
        }
        runner.withPropertyValues("ticker.server.base-path=/ticker").run {
            val filter = it.getBean(io.micrometer.core.instrument.config.MeterFilter::class.java)
            fun id(uri: String) = io.micrometer.core.instrument.Meter.Id(
                "http.server.requests", io.micrometer.core.instrument.Tags.of("uri", uri), null, null, io.micrometer.core.instrument.Meter.Type.TIMER,
            )
            assertThat(filter.accept(id("/ticker/api/services"))).isEqualTo(io.micrometer.core.instrument.config.MeterFilterReply.DENY)
        }
        runner.withPropertyValues("ticker.server.exclude-self-requests=false").run {
            assertThat(it).doesNotHaveBean(io.micrometer.core.instrument.config.MeterFilter::class.java)
        }
    }
}
