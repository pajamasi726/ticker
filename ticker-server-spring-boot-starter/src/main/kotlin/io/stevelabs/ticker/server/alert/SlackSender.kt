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
            // Redact the webhook URL (a credential) but keep the actual cause — a typo'd webhook
            // (404 no_service / invalid_payload) must be diagnosable from this line alone.
            val cause = (e.message ?: e.javaClass.simpleName).replace(webhookUrl, "<webhook-url>")
            log.warn("Failed to post Slack alert ({}): {} — check the webhook URL / Slack status.", message.fallback, cause)
        }
    }

    private fun payload(message: AlertMessage): Map<String, Any> {
        // One labelled item per line under the headline — scannable top-to-bottom. (Slack's
        // two-column "fields" grid reads jumbled for this kind of data, so we don't use it.)
        val body = buildString {
            append(message.title)
            for ((label, value) in message.fields) append("\n*$label:*  $value")
        }
        val blocks = mutableListOf<Map<String, Any>>(
            mapOf("type" to "section", "text" to mrkdwn(body)),
        )
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
