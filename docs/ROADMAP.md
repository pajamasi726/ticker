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

## Phase 5 — Drill-down ✓ (expanded through Phase 7b)
`GET /api/services/{id}/detail` returns a grouped, server-curated dashboard for `SPRING` targets
(full widget inventory in Phase 7b). Actuator non-UP components surface as `DEGRADED`. The
**per-metric inspector** (nav level 3: wall → service dashboard → per-metric detail) ships
alongside: click any widget to open a full-page chart with an **auto / 0–100% scale toggle**, a
**time-range picker** (live · 5m · 15m · 1h · 6h · 24h · 7d, backed by opt-in history — Phase 6),
a **timestamp-format selector** (persisted), min/avg/max stats, HTTP by-endpoint breakdown (outcome-
scoped), and inline alert config. HTTP targets show a minimal view; absent groups are omitted
gracefully.
**Done when:** ✓ grouped dashboard renders and updates live; per-metric inspector opens on widget
click with range picker; a degraded component shows amber; HTTP target shows minimal view.

## Phase 6 — Metric history persistence ✓ (archival to cold storage remains deferred)
**Opt-in, off-by-default** (`ticker.history.enabled=false`). When enabled, a `@Scheduled`
`MetricHistoryRecorder` writes resolved dashboard widget values (keyed by widget key) to a
`metric_sample` table via Spring JDBC — **embedded H2 file** by default (`ticker.history.h2-path`);
**MySQL** also supported (operator runs `docs/history-schema-mysql.sql`; credentials from env).
Hourly prune to `ticker.history.retention` (default 7d). `GET /api/services/{id}/metric-history?key=&range=`
returns server-side avg-downsampled points (≤ `maxBuckets`, default 240) for ranges 5m · 15m · 1h · 6h ·
24h · 7d. Disabled → fully in-memory / no DB, no schema, no recorder.
Archival-to-cold-storage (write+verify then delete, S3) remains the only deferred sub-part.
**Done when:** ✓ with `ticker.history.enabled=true`, the drill-down range picker fetches server-side
downsampled series; samples prune on schedule; H2 auto-creates; MySQL DDL documented.

## Phase 7 — Polish & hardening
Design pass with the `frontend-design` skill (heartbeat signature, density, type scale, color
semantics). Watchdog wiring (k8s probes + a documented external check). Finalize the config
surface. README: deploy steps + the "not the sole alert path / watch the watcher" notes.
**Done when:** it looks like the ops board in the PRD, the watchdog is documented and wired,
and a teammate can deploy it from the README alone.

## Phase 7b — Comprehensive Spring/JVM dashboard ✓ (done 2026-06-30)
Expand the P5/P7a drill-down (7 flat cards) into a **rich curated dashboard** matching the common
pre-built Grafana Spring Boot dashboards: ~9 grouped sections (Basic · JVM Memory heap/non-heap ·
GC · Threads · Classes & HTTP · Logback · Data Sources · Web) of ~40 widgets rendered as gauges +
live charts + numbers. The backend owns the **dashboard definition** (`ticker.detail.dashboard`);
`MetricFetcher` resolves it over virtual threads, pulling ONLY whitelisted `/actuator/metrics/{name}`
(guardrail #4); the frontend is a **generic renderer** (render/unit-driven, no per-metric code).
Charts accumulate live while open (cumulative counters charted as per-poll deltas). This is a
deliberate identity expansion: "liveness board **+ a rich curated Spring/JVM dashboard**" — still
a fixed curated dashboard, not a configurable builder / query language.
**Done when:** clicking a Spring tile opens a wide, grouped dashboard updating live; absent metrics
are handled gracefully; only `/actuator/metrics/` is ever called; `./gradlew test` green;
`npm run build` clean; a Playwright screenshot shows the grouped dashboard.

**Extended 2026-07-01 — full catalog + dimmed-absent:** the set now spans the full common Micrometer
surface — **15 groups, ~90 widgets** (added HTTP Client, JPA/Hibernate, Cache, full Tomcat
threads/connections/throughput, generic JDBC, Task Executors). Absent metrics are **no longer
omitted** — every widget carries an `available` flag and the UI renders uncollected ones **dimmed
("not collected")**, so the whole catalog stays visible per target. `MetricFetcher` reads the
target's metric-names list once and GETs only what exists, keeping request volume sane.
Stored history / a "Last N min" time-axis shipped in **Phase 6** (the renderer already receives
stored series via the range picker).

## Phase 7c — Metric-threshold alerting ✓ (done 2026-07-01)
Per-metric threshold alerts on top of the existing alert subsystem. Recommended defaults
(CPU>80% · system-CPU>90% · heap>85% · disk-free<10% · GC-overhead>25% · open-files>80%),
**adjustable from the drill-down UI** (🔔 per widget → threshold/cooldown/enable). A `@Scheduled`
`MetricAlertService` evaluates each enabled rule against live SPRING targets (only whitelisted
`/actuator/metrics` — guardrail #4), fires via the existing Slack `AlertSender` (or logs if no
webhook) **with the captured value**, with per-(target,rule) **cooldown** (guardrail #2), and keeps a
recent-fires log (`GET /api/alerts/recent`) so breaches are visible without Slack. Rules are
**in-memory** (seeded from defaults; no-DB); REST `GET/PUT /api/alerts/rules`. Runs **alongside**
incident alerting (guardrail #3).
**Done when:** ✓ a low threshold set in the UI fires within the eval interval and shows in "Recent
alerts"; tests green; Playwright shows the 🔔 popover + fired strip. (`docs/qa/2026-07-01-alerting/`.)

> *The Phase 7b dashboard-richness expansion and the Phase 6 opt-in DB history were deliberate
> product-owner scope calls made during development; both stay within the prime directive
> (fixed curated dashboard, not a configurable builder or TSDB).*

## Phase 8 — UI-managed HTTP/endpoint monitors (pull / health-check + warm-up) ✓ (done 2026-07-01)
Let an operator add a **plain HTTP target** from the UI (not just `targets.yml`): name + URL → the
existing poller GETs it expecting 2xx → alive/DOWN, debounced. Doubles as a **warm-up** pinger.
**Shipped:** `POST /api/targets/http` `{name,url}` creates a target with a new `TargetSource.UI`
(kept until explicitly removed — persistent/STATIC-like, *not* heartbeat-expiring). `DELETE
/api/targets/{id}` removes UI + registered targets; static targets stay `409`. `ServiceView` gained
a `source` field so the wall shows a remove (×) only on non-STATIC tiles. **Opt-in file
persistence** via `ticker.ui-targets-store-path` (default off = in-memory, lost on restart; set → a
JSON file that survives restart and degrades gracefully on any IO error — a flat file, consistent
with the no-DB stance). The wall gained a collapsible "Add HTTP monitor" form (name + URL), fully
i18n'd (ko/en); server `{code}` errors localize inline (`TARGET_NAME_TAKEN` → 409, `INVALID_REQUEST`
→ 400).
**Deferred (YAGNI for a liveness board):** per-target poll interval — the poller uses one global
`ticker.poll.interval`, and per-target scheduling is a large lift; and a configurable expected
status — the HTTP checker already treats any 2xx as healthy. No scripting/assertions, by design.

## Phase 9 — Programmatic (code-level) configuration ✓ (done 2026-07-01)
Everything set via `application.yml` is also configurable **in code**, the way most Spring starters
allow. **Shipped:** a `fun interface TickerConfigurer` bean. The auto-config collects all
`TickerConfigurer` beans (`ObjectProvider`, `@Order`-aware), seeds a mutable `TickerConfig` from the
bound properties + defaults, applies each configurer, then builds `TargetRegistry` / `MetricAlertStore`
from the result. API: `addTarget(name, type, url, tags)`,
`configureAlert(key, threshold? / enabled? / cooldownSeconds? / forSeconds?)`, `putAlertRule(rule)`.
Code is additive and YAML wins on a target-name collision; `MetricAlertStore` now takes a seed list so
code-added rules surface (and its `all()` iterates the seed, not just the built-in defaults).
**Deferred:** code-level dashboard-group config — it entangles `MetricFetcher`/`DetailController`/history,
and the dashboard is already a comprehensive server-curated default; revisit if a user needs it.
**Done:** `ticker-server-sample` ships a `@Bean TickerConfigurer` that adds a `code-configured` target
(no YAML entry) + tweaks the `cpu-process` alert (0.75 / 15s); verified live (target UP on the wall,
rule reflects the override); server-starter suite green (158 tests, JaCoCo 85.7%). README usage doc
still pending (no README yet — see release prep).

## Phase 10 — History archival to cold storage ✓ (done 2026-07-01)
Delivers guardrail #5's deferred "archive before delete". Opt-in via `ticker.history.archive.enabled`
+ `ticker.history.archive.dir`. Before the hourly retention prune deletes aged rows, it **exports them
to a gzip CSV** (`metric_sample-<ts>.csv.gz`, cols `target_id,metric_key,ts_millis,metric_value`) in the
archive dir, **verifies the written row count, and deletes only after the archive verifies** — a failed
write/verify skips the delete and retries next cycle, so data is never dropped un-archived. Backend-
agnostic (exports rows, so it also covers MySQL/PostgreSQL). Restore is a per-DB CSV import (`CSVREAD` /
`LOAD DATA` / `\copy`), documented in ARCHITECTURE. Verified: archive→verify→delete round-trips, and a
forced verify-failure leaves the rows intact. The archive dir is itself bounded (Logback-style rolling
cap: `archive.file-retention` default 90d + `archive.max-total-size-mb`), and ARCHITECTURE documents
RANGE-partition-by-`ts_millis` + drop-partition as the MySQL/PostgreSQL scale pattern (with
`init-schema=false`). **Deferred still:** S3/remote cold storage + a one-click restore endpoint
(local-dir archival covers the guardrail; remote is an ops-driven add-on).

## Maven Central publishing
Publish `ticker-core`, `ticker-client-spring-boot-starter`, and `ticker-server-spring-boot-starter` to Maven Central (`io.stevelabs`). Requires Sonatype OSSRH account, signing config, and DNS-verified domain (`stevelabs.io`). Local-publish path (`publishToMavenLocal`) is verified in Phase 0.5; the Central push is a separate release step done once the API is stable.
**Done when:** the three artifacts are available on Maven Central and the README has coordinates.

## Explicitly later / maybe never
Telegram / email notifiers · auth / RBAC · multi-collector federation · long *queryable*
history (use a real TSDB instead) · a required external datastore (MySQL as an opt-in history
backend is shipped; MySQL as a mandatory dependency is not a goal) · auto-remediation.
Only on explicit request — and check it's still in the tool's spirit (a *simple liveness
board*) first.
