package io.stevelabs.ticker.server

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ticker.server")
data class TickerServerProperties(
    val enabled: Boolean = true,
    /**
     * Optional base path to relocate the whole Ticker UI + REST API under — e.g. `/ticker` — for when a
     * bare `/api` would collide behind a shared gateway/ingress. Empty (default) keeps the UI at the root
     * and the API under `/api`. When set, the UI serves at `<base>/` and the API under `<base>/api`; the
     * collector's own `/actuator` deliberately stays put so external liveness probes have a stable path
     * (guardrail #1). Leading and trailing slashes are normalized (`ticker` == `/ticker/` == `/ticker`).
     */
    val basePath: String = "",
    /**
     * Opt-in eviction of self-registered instances whose heartbeat has stopped for longer than this
     * (e.g. `10m`). Default 0 = never evict: a crashed instance SHOULD stay on the wall as a red tile —
     * that is the board's job — and gracefully-stopped clients deregister themselves. Enable for
     * autoscaling churn where replaced replicas can't always deregister.
     */
    val registrationExpiry: java.time.Duration = java.time.Duration.ZERO,
)
