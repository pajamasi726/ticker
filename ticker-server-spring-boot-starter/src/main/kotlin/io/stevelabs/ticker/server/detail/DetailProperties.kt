package io.stevelabs.ticker.server.detail

import org.springframework.boot.context.properties.ConfigurationProperties

data class MetricSpec(val name: String, val tag: String? = null)

@ConfigurationProperties(prefix = "ticker.detail")
data class DetailProperties(
    val metrics: List<MetricSpec> = listOf(
        MetricSpec("jvm.memory.used", "area:heap"),
        MetricSpec("jvm.memory.max", "area:heap"),
        MetricSpec("jvm.threads.live"),
        MetricSpec("jvm.gc.pause"),
        MetricSpec("process.cpu.usage"),
        MetricSpec("http.server.requests"),
        MetricSpec("process.uptime"),
        MetricSpec("hikaricp.connections.active"),
        MetricSpec("hikaricp.connections.max"),
    ),
)
