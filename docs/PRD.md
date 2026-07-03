# Ticker — Product Requirements

## Problem
We monitor with Spring Actuator + Prometheus + Grafana today. It works, but: assembling
node_exporter + Prometheus + Grafana + Alertmanager is fiddly, Grafana dashboards are tedious
to build and keep nice, nginx lives in a separate silo, and per-node tools (and single-agent
dashboards) mean hopping between screens. Spring Boot Admin gives a single registry, but its
UI isn't the at-a-glance monitoring view we want. No single *simple* tool gives all three of:
**one screen for everything + Spring/JVM internals + dead-simple alerts**. So we build a thin
one — the data already lives in actuator; we just want our own face on it.

## Who it's for
Internal dev/SRE team. Small scale (tens of services, not thousands). Runs inside our network
/ cluster. Not customer-facing, not multi-tenant.

## Scope decision (read before building)
Priority is **liveness first**: the #1 job is "is each service up, right now, on one screen."
On top of that we want a **rich, curated Spring/JVM dashboard** on click — a comprehensive,
grouped set of actuator/JVM widgets (heap & pools, GC, threads, classes, HTTP, DB pool, Tomcat,
logback) rendered as gauges + live charts + numbers, matching the common pre-built Grafana
Spring Boot dashboards. It's a *fixed, curated* dashboard (server-owned definition), **not** an
end-user dashboard builder or query language; live now, opt-in stored history (off by default;
H2/MySQL; up to 7d with a time-range picker) is shipped.
That detail is the reason we build instead of adopting:
- **Uptime Kuma** — nails liveness, but no JVM/app internals.
- **Netdata** — great infra view, but no app internals without per-app setup.

nginx and other non-Spring endpoints only need up/down.

## Goals (prioritized)
1. **One status wall.** All services (Spring + nginx + arbitrary HTTP) as live tiles
   (green/amber/red) on a single screen. No navigating per-node.
2. **Trivial to add a service.** A Spring app joins by knowing only the collector URL
   (self-register on startup). Non-Spring targets via a one-line config entry. Target: under
   2 minutes, no dashboard authoring.
3. **SBA-style deployment.** Attach monitoring by adding a dependency — a **client starter**
   on each monitored app + a **server starter** embedded in a collector app (Spring Boot Admin
   model). Activated by properties + auto-configuration; no annotation setup required.
4. **Drill-down on Spring internals.** Click a tile → a rich curated dashboard (~9 grouped
   sections, ~50 widgets — gauges, live charts, numbers) covering JVM heap/GC/threads/classes,
   HTTP rate/latency/errors, DB connection pool, Tomcat, Logback, and basic process stats.
   Click any widget for a full-page per-metric inspector with a time-range picker (live to 7d).
5. **Simple, trustworthy alerts.** Slack on down (after debounce) and on recovery.
   Configuring alerts = setting a webhook, not authoring rules.
6. **Open source / Maven Central.** `ticker-core`, `ticker-client-spring-boot-starter`, and
   `ticker-server-spring-boot-starter` are published to Maven Central (`io.stevelabs`,
   Apache-2.0), so any Spring Boot team can adopt them.

## Non-goals
Short version: no TSDB, no logs, no tracing, no query language,
no **end-user** dashboard builder (the curated server-owned dashboard is in scope), no RBAC at MVP.

## Success criteria
- Add a brand-new Spring service and see it on the wall **without touching collector config**
  (pure self-register).
- The whole fleet's up/down is visible on **one screen**, no per-host hopping.
- A killed service shows `DOWN` and posts to Slack within ~`(pollInterval × failureThreshold)`
  seconds — and a single transient blip does **not** alert.
- The collector runs in **one small container** (≤256Mi) at our scale.
- Bringing the stack up locally needs **no external services** (in-memory, no DB by default; opt-in
  metric history adds an embedded H2 file when `ticker.history.enabled=true`).

## UX & design direction
The subject is a fintech **operations board** — closer to a NOC / control-room wall than a
generic SaaS dashboard. Lean into that, but avoid the default "black bg + one neon accent"
`frontend-design` skill; this is a brief, not final CSS):

- **Signature — the heartbeat.** Every tile carries a live **heartbeat sparkline**: a thin,
  continuously updating pulse of recent response time / poll success. The board should
  literally look *alive*; a flatline reads instantly as "this stopped breathing." This is the
  one memorable element — keep everything around it quiet.
- **Status semantics.** Don't rely on red/green alone (colorblind-hostile, low information).
  Use four states with distinct hue **and** glyph/label: `UP`, `DEGRADED` (slow/partial),
  `DOWN`, `UNKNOWN` (never polled / stale). Encode state in the tile's border + a small glyph,
  not just fill color.
- **Type.** A clean grotesk for labels/headings; a **monospace** for data (hostnames,
  latencies, versions, counts) — monospace is native to the ops/terminal world and makes
  numbers scan. Set a real type scale; don't default to one size.
- **Density.** Information-dense by design — an operator should absorb ~40 services at a
  glance. Tight grid, small tiles, no decorative whitespace padding out the screen.
- **Detail view.** Clicking a tile opens internals (heap/GC/threads/HTTP/DB) as calm,
  legible charts — not a wall of gauges.
- **Copy.** Name things by what the operator controls. "Add a service," not "register a
  target endpoint." Empty/error states give direction ("No services yet — point an app's
  `ticker.client.collector-url` here"), never mood. Active voice on every control.

## Open product questions (decide as you go)
- **History retention:** ✓ resolved — default 7d (`ticker.history.retention`), configurable.
  Opt-in persisted history (off by default); in-memory sparkline window always present.
- **Degraded definition:** actuator non-UP component, or latency over threshold, or both?
  Start: actuator non-UP component OR p95 latency over `degradedLatencyMs`.
