# Configuration reference

Every Ticker property, with its type, default, and what it does. All properties are standard Spring
`@ConfigurationProperties`: set them in `application.yml`, as environment variables (relaxed binding —
`ticker.server.public-url` ↔ `TICKER_SERVER_PUBLIC_URL`), or programmatically via a
[`TickerConfigurer` bean](../README.md#3-optional-configure-in-code). IDE autocomplete + inline docs
are bundled for all of them.

**On the Docker image** (`pajamasi726/ticker`) the same two non-code routes apply: `-e` env vars, or
mount a yaml at `/workspace/config/application.yml` (the app's working dir is `/workspace`, so
Spring's default `./config/` location finds it). List properties like `ticker.targets` are replaced,
not merged, by a mounted file. Examples: [README → Kick the tires](../README.md#0-kick-the-tires-docker-10-seconds).

**Convention: everything beyond the basics is off by default.** A collector with zero config gives you
the wall + drill-down, in-memory, no alerts, no DB.

---

## Collector (`ticker-server-spring-boot-starter`)

### `ticker.server.*` — the collector itself

| Property | Type | Default | Description |
|---|---|---|---|
| `ticker.server.enabled` | boolean | `true` | Activate the collector: REST API, bundled UI, poller. |
| `ticker.server.base-path` | String | *(none)* | Relocate the whole UI + API under a prefix, e.g. `/ticker` → UI at `/ticker/`, API at `/ticker/api/**`. For when a bare `/api` clashes behind a shared gateway. The collector's own `/actuator` deliberately stays put (stable path for external liveness probes). |
| `ticker.server.public-url` | String | *(none)* | The externally-reachable URL people open this Ticker at — scheme + host + port + path, e.g. `https://ops.acme.com/ticker`. Ticker can't discover its own domain/port-mapping/ingress prefix, so tell it once; used wherever it points humans back at itself (the "Open Ticker board" link in Slack alerts). Unset → links are omitted. Same idea as Grafana's `root_url`. |
| `ticker.server.registration-expiry` | Duration | `0` (off) | Opt-in: evict a self-registered instance whose heartbeat stopped for this long (e.g. `10m`). Off by default on purpose — a *crashed* instance should stay red on the wall (that's the board's job), and gracefully-stopped clients deregister themselves. Enable for autoscaling churn. |
| `ticker.server.exclude-self-requests` | boolean | `true` | Drop the collector's own monitoring traffic (`/actuator` self-poll + its `/api` UI polling, base-path aware) from its `http.server.requests`, so the "self" tile shows real traffic. |
| `ticker.server.admin-enabled` | boolean | `true` | The admin view (⚙) + its `/api/admin/**` endpoints: collector info, target registry, storage & backup management. Set `false` to hide the whole surface. |

### `ticker.poll.*` — health polling

| Property | Type | Default | Description |
|---|---|---|---|
| `ticker.poll.interval` | Duration | `10s` | How often every target is polled (virtual-thread fan-out). |
| `ticker.poll.timeout` | Duration | `5s` | Per-check connect/read timeout. |
| `ticker.poll.failure-threshold` | int | `3` | Consecutive failed polls before a target flips `DOWN` — the debounce that stops one blip from paging anyone (guardrail #2). |
| `ticker.poll.degraded-latency-ms` | long | `1000` | A successful check slower than this renders as `DEGRADED`. |
| `ticker.poll.staleness-multiplier` | int | `3` | If a target hasn't been polled for `interval × this`, its state shows as `UNKNOWN` rather than trusting stale data. |

### `ticker.targets` — static targets

| Property | Type | Default | Description |
|---|---|---|---|
| `ticker.targets` | list | `[]` | Things that can't self-register (nginx, external HTTP endpoints). Each entry: `name`, `url`, optional `type` (`SPRING` \| `HTTP`, default `HTTP`) and `tags`. A missing `name`/`url` fails startup with a message naming the entry. |
| `ticker.ui-targets-store-path` | String | *(none)* | Opt-in file persistence for monitors added from the UI (a flat JSON file — consistent with the no-DB stance). Unset → UI monitors are in-memory and lost on restart. |

```yaml
ticker:
  targets:
    - name: edge-nginx
      type: HTTP
      url: http://edge-nginx/healthz
      tags: [infra]
```

### `ticker.alert.*` — alerting (all off by default)

| Property | Type | Default | Description |
|---|---|---|---|
| `ticker.alert.enabled` | boolean | `false` | Master switch for alert **evaluation + dispatch** — incident alerts (🔴 DOWN / 🟢 recovered) and per-metric threshold alerts (⚠️). Off → nothing fires, but the rules store and the `/api/alerts/**` read/edit API stay available (the dashboard's threshold-based severity colouring uses them); edits made while off apply if alerting is enabled later. |
| `ticker.alert.slack-webhook-url` | String | *(none)* | Slack incoming webhook. **A credential** — reference it from the environment (`${SLACK_WEBHOOK_URL:}` or the `TICKER_ALERT_SLACK_WEBHOOK_URL` env var), never commit the value. Blank counts as unset. Unset + alerting enabled → alerts are inert and a single warning is logged. Setup walkthrough: [slack-alerts.md](slack-alerts.md). |
| `ticker.alert.cooldown` | Duration | `15m` | Minimum gap before re-alerting the *same* incident (flap suppression, guardrail #2). |
| `ticker.alert.metric-interval` | Duration | `30s` | How often metric-threshold rules are evaluated against live `SPRING` targets. |

Runtime (not yaml) alert controls — available even when `ticker.alert.enabled` is off:

| Endpoint | Description |
|---|---|
| `GET /api/alerts/rules` · `PUT /api/alerts/rules/{key}` | Read / edit threshold rules (`threshold`, `forSeconds`, `cooldownSeconds`, `enabled`) — same thing the UI's 🔔 popover does. Defaults ship for CPU >80%, system CPU >90%, heap >85%, disk-free <10%, GC overhead >25%, open files >80%. |
| `GET /api/alerts/recent` | Recent fires, visible without Slack. |
| `POST /api/alerts/silence` `{"minutes":10}` · `GET` · `DELETE` | Deploy/maintenance window: dispatch is suppressed while active; anything **still DOWN when the window ends is announced then**, so a silence can never swallow a real outage. |

Storage & admin runtime APIs (the admin view's backing endpoints — also curl-able):

| Endpoint | Description |
|---|---|
| `GET /api/history/stats` | History state: db, row count, data span, H2 file size, retention, archive totals. Answers `{enabled:false}` when history is off — never a 404. |
| `POST /api/history/backup` | Zero-downtime H2 snapshot to `backup.dir` (409 while one runs; 400 with a `mysqldump`/`pg_dump` hint on other DBs). |
| `GET /api/history/backups` · `GET /api/history/backups/{name}` | List / download backup zips (names strictly whitelisted — no traversal). |
| `GET /api/services/{id}/outbound` | The no-tracing service map, one hop: per called host — count, mean/max latency, 5xx — aggregated from the app's `http.client.requests`, with a wall-target link when the host matches unambiguously. |
| `GET /api/admin/info` · `GET /api/admin/targets` | Collector version/uptime/config facts (secrets as booleans only) and the target registry with per-instance heartbeats. Gated by `ticker.server.admin-enabled`. |

### `ticker.history.*` — opt-in persisted metric history

| Property | Type | Default | Description |
|---|---|---|---|
| `ticker.history.enabled` | boolean | `false` | Persist dashboard samples for the 5m–7d range picker. Off → fully in-memory, no DB, no schema. |
| `ticker.history.db` | enum | `H2` | `H2` (embedded file, zero-setup) \| `MYSQL` \| `POSTGRESQL`. |
| `ticker.history.h2-path` | String | `./data/ticker-history` | H2 file path (`db=H2` only). Use a durable volume in production. |
| `ticker.history.url` | String | *(none)* | JDBC URL, required for MySQL/PostgreSQL (e.g. `jdbc:mysql://host:3306/ticker`). |
| `ticker.history.username` / `password` | String | *(none)* | DB credentials — **environment only**, never committed (guardrail #5). |
| `ticker.history.init-schema` | boolean | `true` | Auto-create the schema at startup (`CREATE TABLE IF NOT EXISTS`). Set `false` when a DBA pre-provisions it (bundled DDL: `db/ticker-history-schema-<db>.sql`). |
| `ticker.history.sample-interval` | Duration | `15s` | How often the recorder samples whitelisted metrics. |
| `ticker.history.retention` | Duration | `7d` | Hourly prune deletes samples older than this. |
| `ticker.history.max-buckets` | int | `240` | Max downsampled points a range query returns. |
| `ticker.history.backup.dir` | String | *(next to the H2 file)* | Where online H2 backup zips land. Default (unset) = `<h2-path parent>/backups` — same volume as the data it snapshots, writable inside containers. |
| `ticker.history.backup.schedule` | String | *(none)* | Optional Spring cron (e.g. `0 0 4 * * *`) for automatic backups. Unset = manual only. |
| `ticker.history.backup.file-retention` | Duration | `0` (off) | Rolling cap: delete backup zips older than this. Off by default — a manual backup is never silently deleted. |
| `ticker.history.backup.max-total-size-mb` | long | `0` (unlimited) | Rolling cap: keep the backup dir under this size, deleting oldest first. |
| `ticker.history.archive.enabled` | boolean | `false` | Archive-before-prune: aged rows are exported to gzip CSV and **verified before** anything is deleted (guardrail #5 — a failed export retries, data is never dropped). |
| `ticker.history.archive.dir` | String | `./data/ticker-history-archive` | Where archive files land. |
| `ticker.history.archive.file-retention` | Duration | `90d` | Rolling cap: archive files older than this are deleted (Logback-style). |
| `ticker.history.archive.max-total-size-mb` | long | `0` (unlimited) | Rolling cap: keep the archive dir under this size, deleting oldest first. |

Operating notes (H2 disk growth, MySQL/PostgreSQL switch, partitioning, restore):
[README → Operating the history store](../README.md#operating-the-history-store-disk-retention-backup).

---

## Monitored app (`ticker-client-spring-boot-starter` / `…-boot3-starter`)

| Property | Type | Default | Description |
|---|---|---|---|
| `ticker.client.enabled` | boolean | `true` | Register with a collector on startup + heartbeat. |
| `ticker.client.collector-url` | String | *(none, required)* | Base URL of the collector, e.g. `http://ticker:8080` — include its base-path if one is set (`http://ticker:8080/ticker`). |
| `ticker.client.url` | String | own `http://<ip>:<server.port>` | Where the collector polls THIS instance. The default (own address) is right when N replicas share one config — each pod registers its own address. Set explicitly only for NAT/port-mapping/TLS. Never point replicas at one shared/load-balanced URL. |
| `ticker.client.name` | String | `spring.application.name` | Display name on the wall. Replicas SHARE it — the wall groups by name and distinguishes instances by `hostname:port`. |
| `ticker.client.type` | enum | `SPRING` | `SPRING` (actuator health + curated metrics) \| `HTTP` (plain GET, 2xx = up). |
| `ticker.client.tags` | list | `[]` | Free-form chips on the tile (team, env, …). |
| `ticker.client.heartbeat-interval` | Duration | `30s` | Periodic re-registration, so a restarted collector repopulates its wall. `<= 0` disables. |
| `ticker.client.deregister-on-shutdown` | boolean | `true` | Graceful shutdown removes this instance from the wall — rolling/blue-green deploys clean up after themselves (deploys are not incidents). A crash skips this and correctly stays visible as DOWN. |
| `ticker.client.exclude-actuator-requests` | boolean | `true` | Drop `/actuator` requests from this app's `http.server.requests`, so requests/sec · latency · error-rate show REAL traffic — not the collector's polling or k8s probes. |

Minimal client config is two lines (plus one for the full dashboard):

```yaml
spring.application.name: orders-api
ticker.client.collector-url: http://ticker:8080
# metrics powers the JVM drill-down — Boot's default exposes health only.
# Skip it and the tile still works; the client logs a WARN reminding you.
management.endpoints.web.exposure.include: health,metrics
```

---

## Putting it together

```yaml
# ---------- collector ----------
ticker:
  server:
    enabled: true
    base-path: /ticker                      # optional: UI → /ticker/, API → /ticker/api/**
    public-url: https://ops.acme.com/ticker # optional: link target in Slack alerts
    registration-expiry: 10m                # optional: autoscaling ghost cleanup
  poll:
    interval: 10s
    failure-threshold: 3
  alert:
    enabled: true
    slack-webhook-url: ${SLACK_WEBHOOK_URL:} # env-templated; blank = alerts stay log-inert
    cooldown: 15m
    metric-interval: 30s
  history:
    enabled: true
    db: H2
    h2-path: /var/lib/ticker/history
    retention: 7d
    archive:
      enabled: true
      dir: /var/lib/ticker/history-archive
  targets:
    - { name: edge-nginx, type: HTTP, url: http://edge-nginx/healthz, tags: [infra] }
```

```yaml
# ---------- each monitored app ----------
spring:
  application:
    name: orders-api
ticker:
  client:
    collector-url: http://ticker:8080/ticker   # collector's base-path included
    tags: [prod, order-team]
```
