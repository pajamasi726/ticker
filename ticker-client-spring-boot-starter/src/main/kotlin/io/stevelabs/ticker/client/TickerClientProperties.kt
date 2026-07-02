package io.stevelabs.ticker.client

import io.stevelabs.ticker.core.ServiceType
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ticker.client")
data class TickerClientProperties(
    val enabled: Boolean = true,
    /** Base URL of the Ticker collector, e.g. `http://ticker:8080` (include the base-path if one is set). */
    val collectorUrl: String? = null,
    /**
     * The URL the collector should poll THIS instance at. Leave unset to default to this machine's own
     * `http://<ip>:<server.port>` — the right thing when N replicas share one config (each pod then
     * registers its own address). Set it explicitly only when the poller must use a different route
     * (NAT, port mapping, TLS). Never point replicas at one shared/load-balanced URL: the collector
     * keys instances by this address.
     */
    val url: String? = null,
    /** Display name; defaults to `spring.application.name`. Replicas SHARE this — the wall groups by it. */
    val name: String? = null,
    val type: ServiceType = ServiceType.SPRING,
    val tags: List<String> = emptyList(),
    val heartbeatInterval: Duration = Duration.ofSeconds(30),
    /**
     * Deregister from the collector on graceful shutdown (default on). Rolling deploys then clean up
     * their old instances immediately; a crash skips this and correctly stays on the wall as DOWN.
     */
    val deregisterOnShutdown: Boolean = true,
)
