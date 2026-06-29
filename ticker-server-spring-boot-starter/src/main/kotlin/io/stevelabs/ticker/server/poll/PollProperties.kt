package io.stevelabs.ticker.server.poll

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ticker.poll")
data class PollProperties(
    val interval: Duration = Duration.ofSeconds(10),
    val failureThreshold: Int = 3,
    val timeout: Duration = Duration.ofSeconds(5),
    val degradedLatencyMs: Long = 1000,
    val stalenessMultiplier: Int = 3,
)
