# Ticker — Roadmap

Build in thin vertical slices. Each phase ends in something runnable and demoable. Don't
start a phase's polish before its "Done when" is met, and don't pull work forward from later
phases.

## Phase 0 — Walking skeleton
Scaffold backend (Kotlin / Spring Boot / Gradle) + frontend (React / TS / Vite).
`/api/services` returns hardcoded mock services. The UI renders the status wall from that mock
(tiles + states + placeholder sparkline). One Docker image serves both.
**Done when:** `docker run` shows a status wall of fake services in the browser.

## Phase 0.5 — Modularize
Split the project into `ticker-core` / `ticker-client-spring-boot-starter` / `ticker-server-spring-boot-starter` / `ticker-server-sample`. Wire properties + auto-configuration for both starters (`ticker.client.enabled`, `ticker.client.collector-url`, `ticker.server.enabled`). Bundle the built `frontend/` assets into the server starter jar. Verify local publish with `./gradlew publishToMavenLocal`.
**Done when:** `./gradlew build` is green across all modules; `./gradlew publishToMavenLocal` publishes core + the two starters to `~/.m2`; `./gradlew :ticker-server-sample:bootRun` starts the collector.

## Phase 1 — Real liveness (static targets)
Add `targets.yml` loading, the `Target` model, `HttpHealthChecker` and `SpringHealthChecker`
(health endpoint only), the `Poller` (scheduled + virtual-thread fan-out), and the health
state machine with `failureThreshold` debounce. `/api/services` now reflects real polls.
**Done when:** put a real Spring app + an nginx URL in `targets.yml`; killing either turns its
tile red after the threshold; a single blip does not.

## Phase 2 — Self-registration (push)
`POST /api/targets` upsert + `GET` / `DELETE`. Wire the HTTP POST in `ticker-client-spring-boot-starter` (startup `ApplicationReadyEvent` POST using `ticker.client.collector-url`); document in README.
**Done when:** a new Spring app appears on the wall just by starting up with `ticker.client.enabled=true` and `ticker.client.collector-url` set, with zero collector-side config.

## Phase 3 — Registration heartbeat (no DB)
Client heartbeat: `ticker-client-spring-boot-starter` sends a periodic re-registration so
the collector's in-memory target list self-heals across a collector restart. Configurable
interval (`ticker.client.heartbeat-interval`, default 30s); `<=0` disables. The sparkline
draws from the in-memory history window — real recent data, no DB required.
**Done when:** restart the collector → all live clients reappear within one heartbeat interval; the sparkline shows real recent samples held in memory.

## Phase 4 — Alerts (incident + digest) ✓ (incident implemented; digest deferred)
`AlertDecider` (pure transition logic) + `AlertSender` interface + `SlackSender` (webhook
from env) + `AlertService` (scheduled snapshot-diff), all gated by
`@ConditionalOnProperty(ticker.alert.enabled=true)`:
- **Incident (Type 1) — implemented:** state machine transition detection + cooldown; DOWN +
  recovery messages posted to Slack. Off by default; webhook URL from env only, never committed.
  Flapping is suppressed by `failureThreshold` debounce (state machine) + `cooldown` (alert layer).
- **Periodic digest (Type 2) — deferred:** a scheduled summary (counts + anything not-UP) with
  `onlyWhenIssues` and optional separate webhook/channel. Not built; add in a future phase.
**Done when (incident):** killing a service posts a Slack "DOWN" after debounce and a
"recovered" on return; flapping respects the cooldown; `ticker.alert.enabled` not set → no
alert beans loaded at all.

## Phase 5 — Drill-down
Detail endpoint + view: pull the metric whitelist (heap / GC / threads / HTTP / DB pool) for
`SPRING` targets; render calm charts. Actuator non-UP components surface as `DEGRADED`.
**Done when:** clicking a Spring tile shows live JVM / HTTP / DB internals; a degraded
component shows amber, not red.

## Phase 6 — History persistence + archival (optional, deferred)
Add **optional, off-by-default embedded H2 (file)** persistence for poll history
(`HealthSample`) only — registered targets stay heartbeat-managed in memory. Append a sample
per poll; prune past `retention`. Then add the archive job: write+verify cold storage
*before* deleting rows, optional S3 upload, `@ConditionalOnProperty`. **No MySQL** —
embedded H2 file only; no external datastore.
**Done when:** with persistence on, history survives restart and old samples are pruned; with
archival on, aged rows land as `health-YYYY-MM-DD.ndjson.gz` and are deleted only after a
verified write — a failed upload leaves the rows intact for the next run.

## Phase 7 — Polish & hardening
Design pass with the `frontend-design` skill (heartbeat signature, density, type scale, color
semantics). Watchdog wiring (k8s probes + a documented external check). Finalize the config
surface. README: deploy steps + the "not the sole alert path / watch the watcher" notes.
**Done when:** it looks like the ops board in the PRD, the watchdog is documented and wired,
and a teammate can deploy it from the README alone.

## Phase 7b — Comprehensive Spring/JVM dashboard (live)
Expand the P5/P7a drill-down (7 flat cards) into a **rich curated dashboard** matching the common
pre-built Grafana Spring Boot dashboards: ~9 grouped sections (Basic · JVM Memory heap/non-heap ·
GC · Threads · Classes & HTTP · Logback · Data Sources · Web) of ~40 widgets rendered as gauges +
live charts + numbers. The backend owns the **dashboard definition** (`ticker.detail.dashboard`);
`MetricFetcher` resolves it over virtual threads, pulling ONLY whitelisted `/actuator/metrics/{name}`
(guardrail #4); the frontend is a **generic renderer** (render/unit-driven, no per-metric code).
Charts accumulate live while open (cumulative counters charted as per-poll deltas). This is a
deliberate identity expansion: "liveness board **+ a rich curated Spring/JVM dashboard**" — still
a fixed curated dashboard, not a configurable builder / query language.
**Done when:** clicking a Spring tile opens a wide, grouped dashboard updating live; absent groups
(e.g. no-DB → Data Sources) are omitted gracefully; only `/actuator/metrics/` is ever called;
`./gradlew test` green; `npm run build` clean; a Playwright screenshot shows the grouped dashboard.
Stored history / a "Last N min" time-axis is the **next phase** (the renderer is built to receive
stored series).

## Maven Central publishing
Publish `ticker-core`, `ticker-client-spring-boot-starter`, and `ticker-server-spring-boot-starter` to Maven Central (`io.stevelabs`). Requires Sonatype OSSRH account, signing config, and DNS-verified domain (`stevelabs.io`). Local-publish path (`publishToMavenLocal`) is verified in Phase 0.5; the Central push is a separate release step done once the API is stable.
**Done when:** the three artifacts are available on Maven Central and the README has coordinates.

## Explicitly later / maybe never
Telegram / email notifiers · auth / RBAC · multi-collector federation · long *queryable*
history (use a real TSDB instead) · MySQL (not a near-term goal) / any external datastore ·
auto-remediation.
Only on explicit request — and check it's still in the tool's spirit (a *simple liveness
board*) first.
