# Ticker — Architecture

## Overview
```
   Spring apps                       Collector (Spring Boot)              Browser
 +------------+  self-register     +---------------------------+      +----------+
 | app A      | --POST /api/targets|  TargetRegistry (in-mem)  |      | React UI |
 | (actuator) | ---------------->  |        |                  |      | status   |
 +------------+                    |        v                  | GET  | wall +   |
 +------------+                    |  Poller --virtual threads-+------| detail   |
 | app B      |                    |   |- SpringHealthChecker  | /api |          |
 +------------+   +---------+      |   +- HttpHealthChecker    |      +----------+
 +------------+   |targets. |--load|        |                  |
 | nginx /etc |   |  yml    |  --> |        v                  |      +----------+
 +------------+   +---------+      |  HealthState + History    |      |  Slack   |
   (HTTP check)                    |        |                  |      +----------+
                                   |        v                  |           ^
                                   |  AlertEngine -------------+-----------+
                                   +---------------------------+
```

## Modules
- **`ticker-core`** — shared types (`ServiceType`, etc.). Published to Maven Central.
- **`ticker-client-spring-boot-starter`** — auto-configuration client starter. Add as a dependency to any monitored Spring Boot app; activated by `ticker.client.enabled=true`. Reads `ticker.client.collector-url` and logs self-registration on startup (HTTP POST to `/api/targets` active in Phase 2). Published to Maven Central.
- **`ticker-server-spring-boot-starter`** — collector REST API + all server-side components, bundled with the React UI (built `frontend/` assets are included in the jar). Activated by `ticker.server.enabled=true`. Published to Maven Central.
- **`ticker-server-sample`** — runnable `@SpringBootApplication` that pulls in the server starter. The entry point for `./gradlew :ticker-server-sample:bootRun` and `bootBuildImage` (`ticker:latest`). Not published.

## Components
- **TargetRegistry** — source of truth for what we monitor. Targets come from
  self-registration (push) and/or `targets.yml` (static). Held in memory; clients
  re-register on a heartbeat so the registry self-heals across a collector restart.
  Static entries are re-seeded on boot.
- **Poller** — scheduled orchestrator. Every `pollInterval`, fans out one check per target
  over a **virtual-thread executor** (cheap concurrency for many targets; avoids dedicating
  platform threads). Per-check timeout.
- **HealthChecker** — strategy interface; one impl per target type. `SpringHealthChecker`
  (actuator) and `HttpHealthChecker` (generic 2xx) at MVP. New types = new impls (see the
  `add-monitored-service` skill).
- **HealthState** — per-target state machine + recent sample history.
- **MetricFetcher** — resolves the server-owned dashboard definition (`ticker.detail.dashboard`) against
  live `/actuator/metrics/{name}` calls for a `SPRING` target, fanned out over virtual threads. Returns
  grouped resolved widgets; absent groups are omitted gracefully. Only whitelisted metric paths are ever
  called (guardrail #4).
- **AlertEngine** — detects state-machine transitions, applies debounce/cooldown, dispatches via `AlertSender`.
- **MetricAlertStore** — holds the in-memory set of per-metric threshold rules (6 recommended defaults:
  CPU/system-CPU/heap/disk-free/GC-overhead/open-files; UI-editable at runtime). Tracks per-(target, rule)
  last-fired for cooldown. Also holds a bounded recent-fires log (last 50 `AlertFire` entries).
- **MetricAlertService** — `@Scheduled` evaluator (default interval 30s, `ticker.alert.metric-interval`).
  For each enabled rule and each `SPRING` target, resolves the metric value via `MetricFetcher`, fires via
  `AlertSender` + records to `MetricAlertStore` when the threshold is breached continuously for the
  configured `for` duration and cooldown has elapsed. Opt-in, gated by `ticker.alert.enabled=true`.
  Runs alongside incident and digest alerting (guardrail #3).
- **MetricHistoryRecorder** — `@Scheduled` recorder (default 15s, `ticker.history.sample-interval`). When
  `ticker.history.enabled=true`, writes resolved dashboard widget values to a `metric_sample` table (embedded
  H2 file by default; MySQL option) via Spring JDBC. A separate hourly scheduled task prunes samples older
  than `ticker.history.retention` (default 7d).
- **API** — REST for the UI and for registration.

## Targets
A target has: `id`, `name`, `type` (`SPRING` | `HTTP`), `url`, optional `tags`,
`source` (`STATIC` | `REGISTERED` | `UI`), and check config (interval / failure-threshold overrides).
- **SPRING:** `url` is the app base; the checker hits `{url}/actuator/health` plus a metric
  whitelist (below).
- **HTTP:** `url` is any endpoint; the checker does `GET`, success = 2xx within timeout. This
  is how nginx and non-Spring services get "is it alive."

## Registration (push) + static (pull-config)
**Push — preferred; the app only needs the collector URL:**
```
POST /api/targets
{ "name": "payment-api", "type": "SPRING", "url": "http://payment-api:8080", "tags": ["payments"] }
```
On the app side, the **`ticker-client-spring-boot-starter`** (a published Spring Boot starter, not a hand-rolled snippet) handles this automatically. Add it as a dependency, set `ticker.client.enabled=true` and `ticker.client.collector-url=<collector>`, and registration is wired. The starter logs self-registration on startup; the HTTP POST to `/api/targets` is active in Phase 2. Re-registering on each boot is fine (upsert by name+url).

**Static (`targets.yml`) — for things that can't self-register (nginx, third-party):**
```yaml
targets:
  - name: edge-nginx
    type: HTTP
    url: http://edge-nginx/healthz     # stub_status, a health path, any 2xx endpoint
```

## Health state machine
States: `UNKNOWN` (never successfully polled / stale) -> `UP` <-> `DEGRADED` <-> `DOWN`.
- Each poll yields `success | degraded | failure`.
- A `consecutiveFailures` counter; the target becomes `DOWN` only when it reaches
  `failureThreshold` (default 3). Resets on success.
- `DEGRADED` = reachable but actuator reports a non-UP component, or latency over
  `degradedLatencyMs`. (Tune later; start simple.)
- `UNKNOWN` if no sample within `stalenessWindow` (e.g. 3x interval) — catches "the poller
  never ran for this target."

## Dashboard definition and metric whitelist (drill-down)
The collector owns a **server-curated dashboard definition** (`ticker.detail.dashboard`, a
`@ConfigurationProperties` bean, overridable). It describes ~9 grouped sections of ~50 widgets
rendered as gauges, live charts, or numbers:

| Group | Sample metrics |
|---|---|
| Basic | `process.uptime`, `process.start.time`, `process.cpu.usage`, `system.cpu.usage`, `system.load.average.1m`, `process.files.open`, `disk.free` |
| JVM Memory (heap) | `jvm.memory.used/max` `area:heap`, G1 Eden/Old/Survivor pools |
| JVM Memory (non-heap) | `jvm.memory.used` `area:nonheap`, Metaspace, Compressed Class Space, Code Cache, buffers |
| GC | `jvm.gc.pause`, `jvm.gc.memory.allocated`, `jvm.gc.memory.promoted`, `jvm.gc.overhead` |
| Threads | `jvm.threads.live/daemon/peak/states` |
| Classes & HTTP | `jvm.classes.loaded`, `jvm.compilation.time`, `http.server.requests` (count/latency/active/by-outcome) |
| Logback | `logback.events` by level |
| Data Sources | `hikaricp.connections.*` (omitted when absent) |
| Web | `tomcat.sessions.*`, `tomcat.threads.*` (omitted when absent) |

`MetricFetcher` resolves each widget via `GET {url}/actuator/metrics/{name}` (with fixed tag filters)
over virtual threads. A group whose every widget fails to resolve is omitted entirely.

**Never** fetch `/actuator/env`, `/actuator/configprops`, `/actuator/heapdump`, or anything
secret-bearing. The whitelist is explicit, code-reviewed, and enforced by a guardrail-#4 test.

## Data model (in-memory + opt-in persisted history)
- **TargetRegistry** — live list of targets (registered + static); held in memory. Registered
  targets self-heal across a restart via the client heartbeat.
- **HealthState** — per target: current state, consecutive-failure counter, and a bounded
  recent history window (last N samples — enough for the sparkline and incident context).
  Memory only; no DB rows.
- **AlertEvent** — transient; the `AlertEngine` tracks last-notified state and cooldown in
  memory. Not persisted.
- **MetricAlertStore** — in-memory `ConcurrentHashMap` of `MetricAlertRule` (seeded from 6
  recommended defaults). Also holds the last-50 `AlertFire` log. Not persisted across restarts.

### Opt-in: persisted metric history (`ticker.history.enabled`, default `false`)
When enabled, dashboard widget values are written to a `metric_sample` table:
```sql
metric_sample(
  target_id  VARCHAR(128) NOT NULL,
  metric_key VARCHAR(128) NOT NULL,   -- dashboard widget key (cpu-process, heap-used, …)
  ts_millis  BIGINT       NOT NULL,   -- epoch millis
  value      DOUBLE       NOT NULL,
  PRIMARY KEY (target_id, metric_key, ts_millis)
)
```
Default storage: **embedded H2 file** (`ticker.history.h2-path`, auto-created). MySQL option:
set `spring.datasource.*` from env and run `docs/history-schema-mysql.sql`. DB credentials
come from env only, never committed (guardrail #5). Disabled → no DB, no schema, no recorder.

### Deferred: archival to cold storage
When/if implemented: select aged rows → write+verify cold storage (local or S3) → *only then*
delete from DB. A failed upload keeps rows and retries next run. AWS keys from IAM role /
default credential chain; never committed properties. `@ConditionalOnProperty(s3.enabled)`.

## Retention
**In-memory sparkline window:** bounded per target (`ticker.history.window`, default last 100
samples). Older samples are evicted when the window fills — no DB, no prune job. This is enough
for the status wall sparkline and short-term incident context.

**Opt-in persisted metric history:** when `ticker.history.enabled=true`, samples accumulate in
`metric_sample` and a separate hourly `@Scheduled` prune deletes rows older than
`ticker.history.retention` (default 7d). Downsampled query results (≤ 240 buckets) are returned
by `GET /api/services/{id}/metric-history` for the drill-down range picker.

(The collector's *own application logs* use standard Logback rolling — separate and free.)

## REST API
| Method | Path | Purpose |
|---|---|---|
| `POST`   | `/api/targets`                                  | self-register / upsert a target (client heartbeat → `REGISTERED`) |
| `POST`   | `/api/targets/http`                             | add a UI HTTP monitor `{name,url}` → `TargetSource.UI` (dup → 409 `TARGET_NAME_TAKEN`) |
| `GET`    | `/api/targets`                                  | list static + registered + UI targets |
| `DELETE` | `/api/targets/{id}`                             | remove a UI or registered target (static → 409) |
| `GET`    | `/api/services`                                 | **UI feed:** every target + current state + `source` + sparkline |
| `GET`    | `/api/services/{id}/detail`                     | grouped dashboard detail (SPRING: resolved widgets; HTTP: empty groups) |
| `GET`    | `/api/services/{id}/metric-breakdown`           | tag-breakdown for a whitelisted metric (`?metric=&tag=&filter=`); guardrail #4 enforced |
| `GET`    | `/api/services/{id}/metric-history`             | server-side avg-downsampled history (`?key=&range=5m\|15m\|1h\|6h\|24h\|7d`); requires `ticker.history.enabled` |
| `GET`    | `/api/alerts/rules`                             | list all metric-alert rules (opt-in, `ticker.alert.enabled`) |
| `PUT`    | `/api/alerts/rules/{key}`                       | update a rule's threshold / cooldown / enabled / for-duration |
| `GET`    | `/api/alerts/recent`                            | recent-fires log (last 50 `AlertFire` entries) |
| `GET`    | `/actuator/health`                              | the collector's **own** health (for the watchdog — guardrail #1) |

The UI polls `/api/services` once per cycle. Keep the payload small — sparkline = last N points only.

## Alerting
Three alert types are supported; all share the `AlertSender` interface (`SlackSender` at MVP)
and are purely property-driven — no code changes to opt in.

> **Guardrail #3 — not the sole alert path.** This board runs *alongside* your existing
> on-call alerting (PagerDuty, OpsGenie, etc.), not replacing it. Until proven, never treat
> "no Ticker alert" as confirmation that a production payment flow is healthy.

### Type 1 — Incident alerts (implemented, Phase 4)
An `AlertService` (scheduled, same cadence as the Poller) takes a snapshot from `HealthStateStore`
each cycle, diffs each target's state vs the previous cycle via `AlertDecider`, and posts to
Slack on `DOWN` entry or recovery. Key properties:

- **Off by default.** No alert beans are loaded unless `ticker.alert.enabled=true`.
- **Debounce (two layers):** the health state machine requires `failureThreshold` consecutive
  failures before `DOWN`, so a single blip never reaches `AlertService`. The alert layer adds a
  `cooldown` (default 15 m) that suppresses re-alerting for the same target within the window.
- **Inert without a webhook.** If `ticker.alert.enabled=true` but no webhook URL is configured,
  `AlertService` logs a one-time warning and skips sending — it does not throw.
- **Webhook from env only.** `ticker.alert.slack-webhook-url` maps to env var
  `TICKER_ALERT_SLACK_WEBHOOK_URL`; never committed to properties files.

Config surface:
```properties
ticker.alert.enabled=false
ticker.alert.slack-webhook-url=        # env TICKER_ALERT_SLACK_WEBHOOK_URL; never committed
ticker.alert.cooldown=15m
```

`SlackSender` is a separate `@ConditionalOnProperty(ticker.alert.slack-webhook-url)` bean so
the `slackWebhookUrl!!` dereference in `AlertAutoConfiguration` is safe.

### Type 2 — Periodic digest (deferred)
A scheduled rollup (counts + anything not-UP) with `onlyWhenIssues` and an optional separate
webhook/channel. Not implemented yet; add in a future phase when the need is confirmed.

### Type 3 — Metric-threshold alerts (implemented, Phase 7c)
Per-metric threshold alerts evaluated by `MetricAlertService` on a configurable schedule
(default 30s, `ticker.alert.metric-interval`). Six recommended default rules:

| Key | Metric | Condition | Default threshold |
|---|---|---|---|
| `cpu-process` | `process.cpu.usage` | > | 80% |
| `cpu-system` | `system.cpu.usage` | > | 90% |
| `heap-used` | `jvm.memory.used/max` (heap) | > | 85% |
| `disk-free` | `disk.free/disk.total` | < | 10% |
| `gc-overhead` | `jvm.gc.overhead` | > | 25% |
| `files-open` | `process.files.open/max` | > | 80% |

Rules are adjusted via `PUT /api/alerts/rules/{key}` (threshold / `for` duration / cooldown /
enabled) or directly from the drill-down UI (🔔 per widget). The evaluator fires only after
the threshold is breached continuously for the configured `for` duration (default 0s), then
dispatches via `AlertSender` with the captured value. Per-(target, rule) cooldown (default 300s)
suppresses re-alerting (guardrail #2). Recent fires visible at `GET /api/alerts/recent` even
without a Slack webhook. Gated by `ticker.alert.enabled=true`; metric names are guardrail-#4
whitelisted.

### AlertSender interface
`AlertSender.send(text: String)` is the seam that keeps `AlertService` unit-testable without
HTTP. `SlackSender` never throws — a failed Slack POST is caught and logged, so a transient
webhook error never breaks the alert scan. Webhook URLs come from env/config and are **never
committed**. The interface leaves room for Telegram/email later — don't build those until asked.

## The watchdog (don't skip)
The collector is a single point of failure for *its own* alerting. Mitigate:
- k8s `livenessProbe` / `readinessProbe` on `/actuator/health`, `restartPolicy: Always`.
- One **external** check that the collector itself is up (a cron `curl` from elsewhere, an
  Uptime-Kuma entry, or a cloud uptime check) -> independent alert if the collector is
  unreachable.
- The README must state this explicitly.

## Deployment
- **Single Docker image.** `npm --prefix frontend run build` outputs static assets bundled into the server
  starter jar's `resources/static`; Spring serves the SPA + `/api`. One container; no external DB required
  by default. The SPA shell is served `Cache-Control: no-store` so redeploys are never masked by a stale
  cached bundle (guardrail #1).
- **Config surface (env/properties):**
  - Core: `TICKER_POLL_INTERVAL`, `TICKER_FAILURE_THRESHOLD`, `TICKER_HISTORY_WINDOW`, `SPRING_PROFILES_ACTIVE`
  - Alerting: `ticker.alert.enabled`, `ticker.alert.slack-webhook-url` (env `TICKER_ALERT_SLACK_WEBHOOK_URL`),
    `ticker.alert.cooldown`, `ticker.alert.metric-interval`
  - Metric history: `ticker.history.enabled` (default `false`), `ticker.history.sample-interval` (default 15s),
    `ticker.history.retention` (default 7d), `ticker.history.h2-path`, `ticker.history.max-buckets` (default 240)
  - MySQL (opt-in): `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` — from
    env only, never committed (guardrail #5)
  - Secrets (webhooks, DB credentials) come from env only, never from committed properties.

## Key decisions & rationale
- **Pull to check, push to register.** The collector *scrapes* targets (so "scrape failed ->
  down" gives clean liveness), but targets *push their registration* (so apps only configure
  the collector URL — no central list to hand-maintain). Best of both; resolves the
  pull-vs-push tension directly.
- **Virtual threads for fan-out.** Polling N targets concurrently costs ~N virtual threads,
  not N platform threads — so "do I need a dedicated thread pool?" stops being a scaling worry.
- **In-memory by default; opt-in persisted metric history.** Zero-dependency runs; the client
  heartbeat keeps the registry current across restarts. Metric history persistence
  (`ticker.history.enabled`, off by default) adds an embedded H2 file (or MySQL) to store
  dashboard widget samples for the drill-down range picker (5m–7d). This is the sanctioned
  bounded retention — still not a TSDB or query language.
- **No auth at MVP.** It's internal and network-restricted. Add auth only when it leaves that
  boundary — a deliberate, separate decision.
- **Bounded history, not a TSDB.** We answer "is it up / what just happened," not "show me
  last quarter." Long history is a different tool on purpose.
