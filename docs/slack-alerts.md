# Slack alerts — webhook setup & wiring

Ticker sends two kinds of alerts to one Slack **incoming webhook**: incident alerts
(🔴 DOWN / 🟢 recovered, debounced + cooldown) and metric-threshold alerts (⚠️, per-rule cooldown).

Messages are rendered as colour-coded Block Kit cards (red / green / amber bar): a short headline,
then **one labelled item per line** — scannable top-to-bottom, nothing repeated:

```
🔴 orders-api is DOWN            🟢 orders-api recovered        ⚠️ orders-api — CPU (process)
Instance:  eb919f6499d7:8081     Instance:  eb919f6499d7:8081   Instance:  701e311941b8:8081
IP:  172.20.0.3                  Downtime:  2m 11s              IP:  172.20.0.4
URL:  http://orders-1:8081                                      Value:  86%
                                                                Threshold:  > 80% (sustained 30s)
```

- Set `ticker.alert.board-url` (e.g. `https://ops.acme.com/ticker`) and every card carries an
  **Open Ticker board** footer link.

Multi-instance targets always name the instance, so `recovered` for one replica can't read as an
all-clear for the app. One webhook = **one channel** (chosen when the webhook is created below);
to alert a different channel, create another webhook and swap the URL.

## Deploys are not incidents

Two mechanisms keep deploy noise out of the channel:

1. **Graceful shutdown deregisters** (`ticker.client.deregister-on-shutdown`, default on) — a rolling
   or blue/green replacement removes its old instances from the wall instead of flipping them DOWN.
   Only crashes (no graceful stop) alert — which is exactly the signal you want.
2. **Silence window for everything else** (big-bang deploys, SIGKILL platforms, maintenance):

   ```bash
   # pipeline: before rolling instances
   curl -X POST -H 'Content-Type: application/json' \
     -d '{"minutes":10}' http://<collector>/api/alerts/silence
   # status / cancel
   curl http://<collector>/api/alerts/silence
   curl -X DELETE http://<collector>/api/alerts/silence
   ```

   While active, incident + metric dispatch is suppressed (state tracking keeps running, and
   suppressions are logged). **Anything still DOWN when the window ends is announced then** —
   a silence can never swallow a real outage. Recoveries of already-announced incidents still go
   out during a window, so the channel is never left with a dangling 🔴.

## 1. Create the webhook (one-time, ~2 minutes)

1. Open **https://api.slack.com/apps** (sign in) and click **Create New App**.

   ![Your Apps page](images/slack/01-your-apps.png)

2. Choose **From scratch**.

   ![Create an app modal](images/slack/02-create-an-app.png)

3. Name it (e.g. `Ticker`), pick your workspace, **Create App**.

   ![Name and workspace](images/slack/03-name-and-workspace.png)

4. You land on the app's settings page.

   ![App settings](images/slack/04-app-settings.png)

5. In the left menu open **Incoming Webhooks** — it starts **Off**.

   ![Incoming Webhooks off](images/slack/05-incoming-webhooks-off.png)

6. Flip **Activate Incoming Webhooks** to **On**, then click **Add New Webhook to Workspace**.

   ![Incoming Webhooks on](images/slack/06-incoming-webhooks-on.png)

7. Slack asks which channel the app may post to.

   ![Authorize](images/slack/07-authorize-channel.png)

8. Pick the channel (a dedicated `#alert`-style channel is ideal) and **Allow**.

   ![Channel picked](images/slack/08-channel-picked.png)

9. Copy the generated **Webhook URL** (`https://hooks.slack.com/services/T…/B…/…`).

   ![Webhook created](images/slack/09-webhook-url.png)

> **The URL is a credential** — anyone holding it can post to that channel. Keep it out of git,
> chat, and screenshots (the URL above is blurred for exactly that reason). If it ever leaks,
> revoke it on this same page (🗑) and add a new one.

## 2. Wire it into Ticker (the URL comes from the environment — never commit it)

Either reference an env var from yaml, or skip yaml entirely (relaxed binding maps the env var):

```yaml
ticker:
  alert:
    enabled: true
    slack-webhook-url: ${SLACK_WEBHOOK_URL:}   # blank (env absent) counts as unset → alerts log-inert
```
```bash
# plain run / docker
export TICKER_ALERT_ENABLED=true
export TICKER_ALERT_SLACK_WEBHOOK_URL='https://hooks.slack.com/services/…'

docker run -d -p 8080:8080 \
  -e TICKER_ALERT_ENABLED=true \
  -e TICKER_ALERT_SLACK_WEBHOOK_URL="$(cat /path/to/slack-webhook)" \
  ticker:latest
```

```yaml
# kubernetes: put the URL in a Secret, not the manifest
env:
  - name: TICKER_ALERT_ENABLED
    value: "true"
  - name: TICKER_ALERT_SLACK_WEBHOOK_URL
    valueFrom:
      secretKeyRef: { name: ticker-alerts, key: slack-webhook-url }
```

No webhook set (with alerting enabled) → alerts are inert and the collector logs a single warning.
Related knobs: `ticker.alert.cooldown` (incident re-alert suppression, default 15m) and per-rule
`cooldownSeconds` / `forSeconds` on metric rules (editable in the UI's 🔔 popover or via
`PUT /api/alerts/rules/{key}`).

## 3. Verify end-to-end

1. **Webhook itself** — one curl, expect `ok` and a message in the channel:
   ```bash
   curl -X POST -H 'Content-Type: application/json' \
     -d '{"text":"Ticker webhook test"}' "$TICKER_ALERT_SLACK_WEBHOOK_URL"
   ```
2. **Incident path** — stop one monitored instance (`docker kill …` / `kubectl delete pod …`);
   after `failure-threshold × poll.interval` (default 3×10s) the 🔴 arrives with the instance
   label; start it again for the 🟢.
3. **Metric path** — temporarily drop a threshold so it must fire, then restore it:
   ```bash
   curl -X PUT -H 'Content-Type: application/json' \
     -d '{"threshold":0.001,"forSeconds":0}' http://<collector>/api/alerts/rules/cpu-process
   # …⚠️ arrives within ticker.alert.metric-interval…
   curl -X PUT -H 'Content-Type: application/json' \
     -d '{"threshold":0.80,"forSeconds":30}' http://<collector>/api/alerts/rules/cpu-process
   ```
   Recent fires are also visible without Slack at `GET /api/alerts/recent` and in the UI.

> Guardrail reminder: debounce + cooldown are deliberate — Ticker never alerts on a single failed
> poll, and repeated breaches are rate-limited so the channel stays readable.
