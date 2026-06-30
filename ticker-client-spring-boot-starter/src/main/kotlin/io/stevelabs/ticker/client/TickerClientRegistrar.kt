package io.stevelabs.ticker.client

import io.stevelabs.ticker.core.RegistrationRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.web.client.RestClient

/** On startup, POST this app's registration to {collector-url}/api/targets (ROADMAP Phase 2). */
class TickerClientRegistrar(
    private val properties: TickerClientProperties,
    private val environment: Environment,
    private val restClient: RestClient,
    private val maxAttempts: Int = 3,
    private val retryDelayMs: Long = 2000,
) {
    private val log = LoggerFactory.getLogger(TickerClientRegistrar::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        val collectorUrl = properties.collectorUrl
        val url = properties.url
        if (collectorUrl == null || url == null) {
            log.warn("ticker.client enabled but collector-url or url is unset; self-registration skipped.")
            return
        }
        val name = properties.name ?: environment.getProperty("spring.application.name") ?: "unknown"
        register(collectorUrl, RegistrationRequest(name = name, type = properties.type, url = url, tags = properties.tags))
    }

    private fun register(collectorUrl: String, request: RegistrationRequest) {
        val endpoint = "${collectorUrl.trimEnd('/')}/api/targets"
        repeat(maxAttempts) { attempt ->
            try {
                restClient.post().uri(endpoint).body(request).retrieve().toBodilessEntity()
                log.info("Registered '{}' with collector {}", request.name, endpoint)
                return
            } catch (e: Exception) {
                if (attempt == maxAttempts - 1) {
                    log.error("Failed to register '{}' with {} after {} attempts: {}", request.name, endpoint, maxAttempts, e.message)
                } else {
                    log.warn("Registration attempt {} for '{}' failed ({}); retrying...", attempt + 1, request.name, e.message)
                    if (retryDelayMs > 0) {
                        try {
                            Thread.sleep(retryDelayMs)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return
                        }
                    }
                }
            }
        }
    }
}
