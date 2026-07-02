package io.stevelabs.ticker.server.alert

import java.time.Instant

/**
 * A deploy/maintenance silence window: while active, alert DISPATCH is suppressed (state tracking
 * keeps running). Deploys and incidents are different events — a pipeline calls
 * `POST /api/alerts/silence {"minutes":10}` before rolling instances so the channel only ever
 * carries real incidents. Anything still DOWN when the window ends is announced then
 * (Alertmanager-style), so a silence can never swallow a real outage.
 */
class AlertSilence {
    @Volatile
    var until: Instant? = null
        private set

    fun isActive(now: Instant = Instant.now()): Boolean = until?.isAfter(now) == true

    fun silenceFor(minutes: Long, now: Instant = Instant.now()): Instant {
        val u = now.plusSeconds(minutes * 60)
        until = u
        return u
    }

    fun clear() {
        until = null
    }
}
