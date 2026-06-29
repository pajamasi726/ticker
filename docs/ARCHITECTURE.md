# Ticker — Architecture

## Overview
```
   Spring apps                       Collector (Spring Boot)              Browser
 +------------+  self-register     +---------------------------+      +----------+
 | app A      | --POST /api/targets|  TargetRegistry (JPA)     |      | React UI |
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
  self-registration (push) and/or `targets.yml` (static). Persisted (JPA) so registrations
  survive restart; static entries are re-seeded on boot.
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

## Data model (JPA)
- `Target` — as above; persisted registrations + cached last state.
- `HealthSample` — `targetId`, `timestamp`, `state`, `latencyMs`, small `detail`. Appended per poll.
- `AlertEvent` — `targetId`, `from`, `to`, `timestamp`, `notified`.

## Retention & archival
`HealthSample` rows accumulate as the poller runs. They are **DB rows, not log files**, so we
don't "roll" them like Logback — a scheduled job ages them out, optionally archiving to
compressed cold storage first. (The collector's *own application logs* use standard Logback
rolling — separate and free; this section is about health history.)

**Row lifecycle (two thresholds):**
1. **Hot** — in the DB, age <= `retention`. Queryable, shown on the dashboard.
2. **Archived** — past `retention`: exported to a compressed file, optionally uploaded to S3,
   then deleted from the DB. Kept for audit/compliance, not shown on the dashboard.
3. **Expired** (optional) — an archive older than `maxAge` is deleted. Unset = keep forever.

At default settings (24h, archival off) this is just a prune and disk is a non-issue. Archival
matters only when you want history **longer than the hot window** without the DB drifting into
a TSDB. Framing: this is really **audit/compliance retention** (an operational record for N
months), so "short hot DB + long cold S3" is the right shape.

```properties
ticker.history.retention=24h                    # how long rows stay queryable in the DB

ticker.archive.enabled=false                     # off = retention just prunes (simplest)
ticker.archive.schedule=0 0 3 * * *              # cron; when the job runs (default 03:00)
ticker.archive.format=ndjson                     # ndjson (greppable) | parquet (Athena-friendly)
ticker.archive.compression=gzip                  # gzip | none
ticker.archive.local-dir=/var/ticker/archive      # used when S3 is off
ticker.archive.max-age=                          # blank = keep archives forever

ticker.archive.s3.enabled=false
ticker.archive.s3.bucket=
ticker.archive.s3.prefix=ticker/health/
ticker.archive.s3.region=ap-northeast-2
```

**Job order (non-negotiable):** select aged rows -> write+verify the archive (S3/local) ->
*only then* delete from the DB. If the write/upload fails, keep the rows and retry next run —
never delete on a half-finished archive. Files are date-partitioned, e.g.
`health-2026-06-25.ndjson.gz`.

**Credentials:** bucket/prefix/region may live in properties; **access keys must not.** On
ECS/EKS use an IAM role; otherwise the AWS default credential chain (env vars). Especially if
this goes open source — a committed key is public and permanent.

**Dependency:** the AWS SDK is an **optional, `@ConditionalOnProperty(s3.enabled)`** path so it
isn't dragged into every deployment. Default build stays dead simple; archival is opt-in.

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
Two independent alert types, both delivered through the same `Notifier` (Slack at MVP). Each is
enabled and configured purely via properties — drop in a webhook and go. They do different jobs
and are tuned separately so neither drowns the other.

### Type 1 — Incident alerts (error / event-driven)
The "wake me up, something broke" channel — fires the moment state changes.
- **Trigger:** `UP/DEGRADED -> DOWN` and recovery `DOWN -> UP`. Optionally `-> DEGRADED`
  (config; off by default to reduce noise).
- **Debounce:** the state machine already requires `failureThreshold` consecutive failures
  before `DOWN`, so a single blip never alerts.
- **Cooldown:** after notifying for a target, suppress repeats for `alertCooldown` (e.g. 15m)
  unless the state changes again.
- **Payload:** service name, old -> new state, timestamp, downtime duration (on recovery), and
  a link to the detail view.

### Type 2 — Periodic digest (scheduled / heartbeat)
The "regular pulse" channel — posts a rollup on a schedule, even when all is well.
- **Trigger:** a cron schedule (e.g. daily 09:00, or every 6h) — not state changes.
- **Content:** counts (UP / DEGRADED / DOWN), the list of anything currently not-UP, optional
  uptime % over the window. One compact summary, not one message per service.
- **`onlyWhenIssues`:** if `true`, stay silent when everything is UP. Default `false` — a steady
  "all N healthy" beat doubles as the cheapest **watch-the-watcher**: if the 09:00 digest never
  arrives, the collector itself is down.

### Notifier & config
`Notifier.send(...)`; `SlackNotifier` posts via an incoming webhook. A single default webhook
covers both types; each type may override with its own webhook to land in a different channel
(Slack incoming webhooks are bound to one channel each). The interface leaves room for
Telegram/email later — don't build those until asked. Webhook URLs come from env/config and are
**never committed**.

```properties
ticker.slack.enabled=true
ticker.slack.webhook-url=${TICKER_SLACK_WEBHOOK}   # default channel for both types

# Type 1 — incident (error)
ticker.slack.incident.enabled=true
ticker.slack.incident.webhook-url=                # optional: separate channel
ticker.slack.incident.notify-on=DOWN,RECOVERY     # DOWN | RECOVERY | DEGRADED

# Type 2 — periodic digest
ticker.slack.digest.enabled=false
ticker.slack.digest.webhook-url=                  # optional: separate channel
ticker.slack.digest.schedule=0 0 9 * * *          # cron; default daily 09:00
ticker.slack.digest.only-when-issues=false
```
Both types are `@ConditionalOnProperty` — beyond the incident channel everything is off by
default, so the tool stays quiet and simple until you opt in.

## The watchdog (don't skip)
The collector is a single point of failure for *its own* alerting. Mitigate:
- k8s `livenessProbe` / `readinessProbe` on `/actuator/health`, `restartPolicy: Always`.
- One **external** check that the collector itself is up (a cron `curl` from elsewhere, an
  Uptime-Kuma entry, or a cloud uptime check) -> independent alert if the collector is
  unreachable.
- The README must state this explicitly.

## Deployment
- **MVP:** single Docker image. `npm --prefix frontend run build` outputs static assets bundled into the server starter jar's `resources/static`; Spring serves the SPA + `/api`. One container, `dev` = H2.
- **Prod:** `prod` profile = MySQL (JDBC URL + creds via env). Still one app container plus
  the MySQL it talks to.
- **Config surface (env):** `TICKER_POLL_INTERVAL`, `TICKER_FAILURE_THRESHOLD`,
  `TICKER_HISTORY_WINDOW`, `TICKER_SLACK_WEBHOOK`, DB settings, `SPRING_PROFILES_ACTIVE`.
  Slack alert types and history archival have their own `ticker.slack.*` / `ticker.archive.*`
  properties — see **Alerting** and **Retention & archival**. Secrets (webhooks, AWS keys) come
  from env/IAM role only, never from committed properties.

## Key decisions & rationale
- **Pull to check, push to register.** The collector *scrapes* targets (so "scrape failed ->
  down" gives clean liveness), but targets *push their registration* (so apps only configure
  the collector URL — no central list to hand-maintain). Best of both; resolves the
  pull-vs-push tension directly.
- **Virtual threads for fan-out.** Polling N targets concurrently costs ~N virtual threads,
  not N platform threads — so "do I need a dedicated thread pool?" stops being a scaling worry.
- **H2 dev / MySQL prod, one JPA layer.** Zero-dependency local runs; familiar prod store; no
  new datastore to learn or operate.
- **No auth at MVP.** It's internal and network-restricted. Add auth only when it leaves that
  boundary — a deliberate, separate decision.
- **Bounded history, not a TSDB.** We answer "is it up / what just happened," not "show me
  last quarter." Long history is a different tool on purpose.
