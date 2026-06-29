package io.stevelabs.ticker.client

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment

/**
 * Phase-0 skeleton: on startup, resolve what this app would register as and log it.
 * The actual HTTP POST to {collector-url}/api/targets arrives in ROADMAP Phase 2.
 */
class TickerClientRegistrar(
    private val properties: TickerClientProperties,
    private val environment: Environment,
) {
    private val log = LoggerFactory.getLogger(TickerClientRegistrar::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        val name = properties.name ?: environment.getProperty("spring.application.name") ?: "unknown"
        val collectorUrl = properties.collectorUrl
        if (collectorUrl == null) {
            log.warn("ticker.client is enabled but ticker.client.collector-url is not set; self-registration skipped.")
            return
        }
        log.info(
            "Ticker client ready: '{}' (type={}, tags={}) -> collector {} (self-registration POST arrives in a later phase).",
            name, properties.type, properties.tags, collectorUrl,
        )
    }
}
