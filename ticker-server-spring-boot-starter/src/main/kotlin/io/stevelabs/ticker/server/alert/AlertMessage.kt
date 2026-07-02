package io.stevelabs.ticker.server.alert

/** Colour of the attachment bar in Slack (mirrors the UI's state colours). */
enum class AlertSeverity(val color: String) {
    DOWN("#e5484d"),
    RECOVERED("#2ecc71"),
    WARNING("#e0a106"),
}

/**
 * A structured alert. Rich senders (Slack Block Kit) render title/fields/context with a coloured
 * bar; [fallback] is the plain one-liner used for notification previews, logs, and tests.
 */
data class AlertMessage(
    val severity: AlertSeverity,
    /** Headline, Slack mrkdwn allowed — e.g. `🔴 *orders-api* is DOWN`. */
    val title: String,
    /** Label → value pairs rendered as a two-column field grid (mrkdwn values allowed). */
    val fields: List<Pair<String, String>> = emptyList(),
    /** Small dim footer line — trend sparkline, board link, etc. */
    val context: String? = null,
    val fallback: String,
)

/** Tiny dependency-free text sparkline (▁▂▃▄▅▆▇█); null samples render as '·'. */
object TextSparkline {
    private val bars = charArrayOf('▁', '▂', '▃', '▄', '▅', '▆', '▇', '█')

    fun of(values: List<Double?>): String {
        val present = values.filterNotNull()
        if (present.isEmpty()) return ""
        val max = present.max().takeIf { it > 0 } ?: 1.0
        return values.joinToString("") { v ->
            if (v == null) "·"
            else bars[((v / max) * (bars.size - 1)).toInt().coerceIn(0, bars.size - 1)].toString()
        }
    }
}
