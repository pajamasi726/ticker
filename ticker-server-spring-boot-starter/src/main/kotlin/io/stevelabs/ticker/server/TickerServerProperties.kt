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
     * The externally-reachable URL people use to open THIS Ticker in a browser — scheme + host +
     * port + path, e.g. `https://ops.acme.com/ticker`. Ticker can't discover this itself (inside a
     * container it only knows it listens on 0.0.0.0; the domain / port-mapping / ingress prefix are
     * deployment facts). Used wherever Ticker points humans back at itself, e.g. the "Open Ticker
     * board" link in Slack alerts. Optional: unset → links are simply omitted. Same idea as
     * Grafana's `root_url` / Spring Boot Admin's `public-url`.
     */
    val publicUrl: String? = null,
    /**
     * Opt-in eviction of self-registered instances whose heartbeat has stopped for longer than this
     * (e.g. `10m`). Default 0 = never evict: a crashed instance SHOULD stay on the wall as a red tile —
     * that is the board's job — and gracefully-stopped clients deregister themselves. Enable for
     * autoscaling churn where replaced replicas can't always deregister.
     */
    val registrationExpiry: java.time.Duration = java.time.Duration.ZERO,
    /**
     * Drop the collector's OWN monitoring traffic — `/actuator/..` (its self-poll, probes) and its
     * `/api/..` (the UI polling the wall) — from its `http.server.requests` metric (default on), so
     * the collector's "self" tile shows real traffic. Set false to count everything again.
     */
    val excludeSelfRequests: Boolean = true,
)
