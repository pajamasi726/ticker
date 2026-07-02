package io.stevelabs.ticker.server.alert

/** Colour of the attachment bar in Slack (mirrors the UI's state colours). */
enum class AlertSeverity(val color: String) {
    DOWN("#e5484d"),
    RECOVERED("#2ecc71"),
    WARNING("#e0a106"),
}

/**
 * A structured alert. Rich senders (Slack Block Kit) render a coloured bar, a short headline, and
 * one labelled item PER LINE (scannable top-to-bottom — no prose, no grids); [fallback] is the
 * plain one-liner used for notification previews, logs, and tests.
 */
data class AlertMessage(
    val severity: AlertSeverity,
    /** Short headline, Slack mrkdwn allowed — e.g. `🔴 *orders-api* is DOWN`. */
    val title: String,
    /** Label → value pairs rendered one per line as `*Label:* value` (mrkdwn values allowed). */
    val fields: List<Pair<String, String>> = emptyList(),
    /** Small dim footer line — the board link. */
    val context: String? = null,
    val fallback: String,
)
