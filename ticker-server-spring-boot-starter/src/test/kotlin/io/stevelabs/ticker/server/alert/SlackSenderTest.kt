package io.stevelabs.ticker.server.alert

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient

class SlackSenderTest {
    private lateinit var server: MockWebServer
    private lateinit var restClient: RestClient

    @BeforeEach fun setup() {
        server = MockWebServer()
        server.start()
        restClient = RestClient.builder().build()
    }

    @AfterEach fun teardown() {
        server.shutdown()
    }

    @Test fun `posts a text payload to the webhook`() {
        server.enqueue(MockResponse().setResponseCode(200))
        SlackSender(restClient, server.url("/hook").toString()).send("🔴 payment-api is DOWN")
        val req = server.takeRequest()
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.path).isEqualTo("/hook")
        val body = req.body.readUtf8()
        assertThat(body).contains("\"text\"")
        assertThat(body).contains("payment-api is DOWN")
    }

    @Test fun `a non-2xx response does not throw`() {
        server.enqueue(MockResponse().setResponseCode(500))
        SlackSender(restClient, server.url("/hook").toString()).send("test") // must not throw
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun `a connection failure does not throw and never leaks the webhook URL into logs`() {
        // Capture log output for SlackSender's logger
        val logger = LoggerFactory.getLogger(SlackSender::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            // Shut down the server so any connection attempt fails immediately
            server.shutdown()
            val webhookUrl = server.url("/secret-token-abc123").toString()
            SlackSender(restClient, webhookUrl).send("alert text") // must not throw

            // Assert no logged message contains the webhook URL (guardrail #5: no secret in logs)
            val loggedMessages = appender.list.map { it.formattedMessage }
            loggedMessages.forEach { msg ->
                assertThat(msg).doesNotContain("secret-token-abc123")
                assertThat(msg).doesNotContain(webhookUrl)
            }
        } finally {
            logger.detachAppender(appender)
        }
    }
}
