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

## Phase 3 — Persistence & history
JPA entities (`Target`, `HealthSample`, `AlertEvent`); H2 (`dev`) + MySQL (`prod`) profiles.
Append a sample per poll; scheduled prune past `retention`. The wall's sparkline is now real
recent data.
**Done when:** restart the collector -> registered targets and recent history survive; old
samples get pruned.

## Phase 4 — Alerts (incident + digest)
`AlertEngine` + the `Notifier` interface + `SlackNotifier` (webhook from env), wired for both
alert types from ARCHITECTURE -> Alerting:
- **Incident (Type 1):** transition detection + cooldown; DOWN + recovery messages.
- **Periodic digest (Type 2):** a scheduled summary (counts + anything not-UP), with
  `onlyWhenIssues` and an optional separate webhook/channel.
Both gated by `@ConditionalOnProperty` — incident on by default, digest opt-in.
**Done when:** killing a service posts a Slack "DOWN" after debounce and a "recovered" with
downtime on return (flapping respects cooldown); and enabling the digest posts a scheduled
"N up / M down" summary to its channel.

## Phase 5 — Drill-down
Detail endpoint + view: pull the metric whitelist (heap / GC / threads / HTTP / DB pool) for
`SPRING` targets; render calm charts. Actuator non-UP components surface as `DEGRADED`.
**Done when:** clicking a Spring tile shows live JVM / HTTP / DB internals; a degraded
component shows amber, not red.

## Phase 6 — History archival
Implement the Retention & archival model (ARCHITECTURE): the scheduled archive job, compression
(ndjson/gzip), the **write+verify -> then delete** ordering, and the optional
`@ConditionalOnProperty(s3.enabled)` S3 upload path. Default build stays prune-only (no AWS SDK
pulled in).
**Done when:** with archival off, rows past `retention` are pruned; with it on, aged rows land
as `health-YYYY-MM-DD.ndjson.gz` (local or S3) and are deleted from the DB only after a verified
write — a failed upload leaves the rows intact for the next run.

## Phase 7 — Polish & hardening
Design pass with the `frontend-design` skill (heartbeat signature, density, type scale, color
semantics). Watchdog wiring (k8s probes + a documented external check). Finalize the config
surface. README: deploy steps + the "not the sole alert path / watch the watcher" notes.
**Done when:** it looks like the ops board in the PRD, the watchdog is documented and wired,
and a teammate can deploy it from the README alone.

## Maven Central publishing
Publish `ticker-core`, `ticker-client-spring-boot-starter`, and `ticker-server-spring-boot-starter` to Maven Central (`io.stevelabs`). Requires Sonatype OSSRH account, signing config, and DNS-verified domain (`stevelabs.io`). Local-publish path (`publishToMavenLocal`) is verified in Phase 0.5; the Central push is a separate release step done once the API is stable.
**Done when:** the three artifacts are available on Maven Central and the README has coordinates.

## Explicitly later / maybe never
Telegram / email notifiers · auth / RBAC · multi-collector federation · long *queryable*
history in the hot DB (archive to cold storage or use a real TSDB instead) · auto-remediation.
Only on explicit request — and check it's still in the tool's spirit (a *simple liveness
board*) first.
