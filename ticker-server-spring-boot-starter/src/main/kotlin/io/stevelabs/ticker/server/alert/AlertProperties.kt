package io.stevelabs.ticker.server.alert

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ticker.alert")
data class AlertProperties(
    val enabled: Boolean = false,
    val slackWebhookUrl: String? = null,
    val cooldown: Duration = Duration.ofMinutes(15),
    val metricInterval: Duration = Duration.ofSeconds(30),
)
