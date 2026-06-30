package io.stevelabs.ticker.server.alert

import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient

/** Posts a Slack incoming-webhook message. Never throws — a failed alert must not break the alert scan. */
class SlackSender(
    private val restClient: RestClient,
    private val webhookUrl: String,
) : AlertSender {
    private val log = LoggerFactory.getLogger(SlackSender::class.java)

    override fun send(text: String) {
        try {
            restClient.post().uri(webhookUrl).body(mapOf("text" to text)).retrieve().toBodilessEntity()
            log.debug("Posted Slack alert: {}", text)
        } catch (e: Exception) {
            log.warn("Failed to post Slack alert ({}): {}", text, e.javaClass.simpleName)
        }
    }
}
