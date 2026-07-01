package io.stevelabs.ticker.server.history

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("ticker.history")
data class HistoryProperties(
    val enabled: Boolean = false,
    val sampleInterval: Duration = Duration.ofSeconds(15),
    val retention: Duration = Duration.ofDays(7),
    val h2Path: String = "./data/ticker-history",
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val maxBuckets: Int = 240,
)
