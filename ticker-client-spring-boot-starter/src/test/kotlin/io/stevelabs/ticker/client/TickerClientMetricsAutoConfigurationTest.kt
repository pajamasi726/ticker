package io.stevelabs.ticker.client

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.config.MeterFilterReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class TickerClientMetricsAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TickerClientMetricsAutoConfiguration::class.java))

    private fun id(name: String, uri: String?) = Meter.Id(
        name,
        if (uri == null) Tags.empty() else Tags.of("uri", uri),
        null,
        null,
        Meter.Type.TIMER,
    )

    @Test fun `filter registered by default and denies actuator requests only`() {
        runner.run { ctx ->
            assertThat(ctx).hasSingleBean(MeterFilter::class.java)
            val filter = ctx.getBean(MeterFilter::class.java)
            assertThat(filter.accept(id("http.server.requests", "/actuator/health"))).isEqualTo(MeterFilterReply.DENY)
            assertThat(filter.accept(id("http.server.requests", "/actuator/metrics/jvm.memory.used"))).isEqualTo(MeterFilterReply.DENY)
            assertThat(filter.accept(id("http.server.requests", "/orders/{id}"))).isEqualTo(MeterFilterReply.NEUTRAL)
            assertThat(filter.accept(id("http.server.requests", null))).isEqualTo(MeterFilterReply.NEUTRAL)
            // Other meters are untouched — even actuator-ish ones.
            assertThat(filter.accept(id("jvm.memory.used", null))).isEqualTo(MeterFilterReply.NEUTRAL)
        }
    }

    @Test fun `opt-out removes the filter`() {
        runner.withPropertyValues("ticker.client.exclude-actuator-requests=false")
            .run { ctx -> assertThat(ctx).doesNotHaveBean(MeterFilter::class.java) }
    }
}
