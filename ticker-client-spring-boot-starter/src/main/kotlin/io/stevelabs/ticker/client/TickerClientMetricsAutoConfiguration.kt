package io.stevelabs.ticker.client

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.config.MeterFilterReply
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * Keeps the monitored app's OWN traffic metrics honest: the Ticker collector fetches health plus
 * ~90 whitelisted metrics from `/actuator/..` every poll cycle, and k8s probes hit it too — left
 * alone, that polling dominates `http.server.requests`, so the dashboard's requests/sec, latency
 * and error rate describe the monitoring itself instead of real users. This [MeterFilter] drops
 * `/actuator/..` observations from `http.server.requests` (registered before meters are created,
 * so nothing is ever recorded). Opt out with `ticker.client.exclude-actuator-requests=false`.
 */
@AutoConfiguration
@ConditionalOnClass(MeterFilter::class)
@EnableConfigurationProperties(TickerClientProperties::class)
@ConditionalOnProperty(prefix = "ticker.client", name = ["exclude-actuator-requests"], matchIfMissing = true)
class TickerClientMetricsAutoConfiguration {

    @Bean
    fun tickerActuatorRequestsMeterFilter(): MeterFilter = object : MeterFilter {
        override fun accept(id: Meter.Id): MeterFilterReply {
            if (id.name == "http.server.requests" && id.getTag("uri")?.startsWith("/actuator") == true) {
                return MeterFilterReply.DENY
            }
            return MeterFilterReply.NEUTRAL
        }
    }
}
