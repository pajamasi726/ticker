package io.stevelabs.ticker.server.alert

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ticker.alert")
data class AlertProperties(
    val enabled: Boolean = false,
    val slackWebhookUrl: String? = null,
    val cooldown: Duration = Duration.ofMinutes(15),
    val metricInterval: Duration = Duration.ofSeconds(30),
    /**
     * Absolute URL of the Ticker board (include the base-path if one is set), e.g.
     * `https://ops.acme.com/ticker`. When set, Slack alerts carry an "Open Ticker board" link —
     * the collector can't know its own externally-reachable address, hence a property.
     */
    val boardUrl: String? = null,
)
