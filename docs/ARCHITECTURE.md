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
- **AlertEngine** — detects transitions, applies debounce/cooldown, dispatches via `Notifier`.
- **API** — REST for the UI and for registration.

## Targets
A target has: `id`, `name`, `type` (`SPRING` | `HTTP`), `url`, optional `tags`,
`source` (`REGISTERED` | `STATIC`), and check config (interval / failure-threshold overrides).
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

## Metric whitelist (drill-down)
For `SPRING` targets, pull a fixed set for the detail view (via `/actuator/metrics/{name}` or
filtered `/actuator/prometheus`):
`jvm.memory.used` / `jvm.memory.max` (heap), `jvm.gc.pause`, `jvm.threads.live`,
`http.server.requests` (count + p95), `hikaricp.connections.active` / `.idle` / `.pending`,
`process.uptime`.
**Never** fetch `/actuator/env`, `/configprops`, or anything secret-bearing. The whitelist is
explicit and code-reviewed.

## Data model (in-memory)
- **TargetRegistry** — live list of targets (registered + static); held in memory. Registered
  targets self-heal across a restart via the client heartbeat.
- **HealthState** — per target: current state, consecutive-failure counter, and a bounded
  recent history window (last N samples — enough for the sparkline and incident context).
  Memory only; no DB rows.
- **AlertEvent** — transient; the `AlertEngine` tracks last-notified state and cooldown in
  memory. Not persisted.

### Deferred: optional embedded-H2 persistence
If poll-history ever needs to survive restart (e.g. for P5 detail charts across restarts),
persistence can be added as an **optional, off-by-default embedded H2 (file)** — JPA entities
(`HealthSample`, `AlertEvent`) pruned on a schedule. **MySQL is not a near-term target.**
Design it only if and when the need is confirmed.

## Retention (in-memory)
The recent history window per target is **bounded in size** (`ticker.history.window`, default
last 100 samples). Older samples are evicted when the window fills — no DB, no prune job
required. This is enough for the sparkline and short-term incident context.

(The collector's *own application logs* use standard Logback rolling — separate and free.)

### Deferred: archival + cold storage
If/when optional embedded-H2 persistence is added (see Data model above), a scheduled prune
job and optional write-then-archive-to-S3 path become relevant. Until then there is no DB
to archive from.

The ordering rule when/if implemented: select aged rows → write+verify cold storage (local or
S3) → *only then* delete from the DB. A failed upload keeps the rows and retries next run.
Credentials (bucket/prefix/region may live in properties; **access keys must not** — use an
IAM role or the AWS default credential chain). The AWS SDK is
`@ConditionalOnProperty(s3.enabled)` so it is never pulled in by default.

## REST API
| Method | Path | Purpose |
|---|---|---|
| `POST`   | `/api/targets`        | self-register / upsert a target |
| `GET`    | `/api/targets`        | list registered + static targets |
| `DELETE` | `/api/targets/{id}`   | remove (registered only) |
| `GET`    | `/api/services`       | **UI feed:** every target + current state + tiny recent sparkline series |
| `GET`    | `/api/services/{id}`  | detail: full recent history + whitelisted metrics |
| `GET`    | `/actuator/health`    | the collector's **own** health (for the watchdog) |

The UI polls `/api/services` once per cycle. Keep the payload small — sparkline = last N points only.

## Alerting
Two alert types are planned; **incident alerts are implemented** (Phase 4). Both are delivered
via the `AlertSender` interface (`SlackSender` at MVP) and are purely property-driven — no code
changes to opt in.

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
- **MVP:** single Docker image. `npm --prefix frontend run build` outputs static assets bundled into the server starter jar's `resources/static`; Spring serves the SPA + `/api`. One container, no external DB required.
- **Config surface (env):** `TICKER_POLL_INTERVAL`, `TICKER_FAILURE_THRESHOLD`,
  `TICKER_HISTORY_WINDOW`, `TICKER_SLACK_WEBHOOK`, `SPRING_PROFILES_ACTIVE`.
  Slack alert types have their own `ticker.slack.*` properties — see **Alerting**.
  Secrets (webhooks) come from env only, never from committed properties.

## Key decisions & rationale
- **Pull to check, push to register.** The collector *scrapes* targets (so "scrape failed ->
  down" gives clean liveness), but targets *push their registration* (so apps only configure
  the collector URL — no central list to hand-maintain). Best of both; resolves the
  pull-vs-push tension directly.
- **Virtual threads for fan-out.** Polling N targets concurrently costs ~N virtual threads,
  not N platform threads — so "do I need a dedicated thread pool?" stops being a scaling worry.
- **In-memory, no DB by default.** Zero-dependency runs; the client heartbeat keeps the
  registry current across restarts. Embedded H2 file persistence is deferred — add it only
  if history-across-restart is confirmed as a need.
- **No auth at MVP.** It's internal and network-restricted. Add auth only when it leaves that
  boundary — a deliberate, separate decision.
- **Bounded history, not a TSDB.** We answer "is it up / what just happened," not "show me
  last quarter." Long history is a different tool on purpose.
