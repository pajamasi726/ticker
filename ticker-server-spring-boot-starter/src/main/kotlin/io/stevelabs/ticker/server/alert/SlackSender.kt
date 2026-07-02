package io.stevelabs.ticker.server.alert

import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient

/**
 * Posts a Slack incoming-webhook message rendered with Block Kit: a colour-coded attachment bar
 * (red/green/amber), a bold title, a two-column field grid, and a dim context footer (trend
 * sparkline / board link). The plain [AlertMessage.fallback] rides along as the top-level `text`,
 * which Slack uses for notification previews. Never throws — a failed alert must not break the
 * alert scan — and never logs the webhook URL (guardrail #5).
 */
class SlackSender(
    private val restClient: RestClient,
    private val webhookUrl: String,
) : AlertSender {
    private val log = LoggerFactory.getLogger(SlackSender::class.java)

    override fun send(message: AlertMessage) {
        try {
            restClient.post().uri(webhookUrl).body(payload(message)).retrieve().toBodilessEntity()
            log.debug("Posted Slack alert: {}", message.fallback)
        } catch (e: Exception) {
            log.warn("Failed to post Slack alert ({}): {}", message.fallback, e.javaClass.simpleName)
        }
    }

    private fun payload(message: AlertMessage): Map<String, Any> {
        val blocks = mutableListOf<Map<String, Any>>(
            mapOf("type" to "section", "text" to mrkdwn(message.title)),
        )
        if (message.fields.isNotEmpty()) {
            blocks += mapOf(
                "type" to "section",
                "fields" to message.fields.take(10).map { (label, value) -> mrkdwn("*$label*\n$value") },
            )
        }
        message.context?.let {
            blocks += mapOf("type" to "context", "elements" to listOf(mrkdwn(it)))
        }
        // No top-level "text": Slack renders it IN ADDITION to the attachment, duplicating the title.
        // The attachment-level "fallback" still feeds notification previews / no-attachment clients.
        return mapOf(
            "attachments" to listOf(
                mapOf(
                    "color" to message.severity.color,
                    "fallback" to message.fallback,
                    "blocks" to blocks,
                ),
            ),
        )
    }

    private fun mrkdwn(text: String): Map<String, Any> = mapOf("type" to "mrkdwn", "text" to text)
}
