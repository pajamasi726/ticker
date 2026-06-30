package io.stevelabs.ticker.client

import io.stevelabs.ticker.core.RegistrationRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.web.client.RestClient
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** On startup, register with the collector; then re-register periodically (heartbeat) so a collector restart re-populates the wall. */
class TickerClientRegistrar(
    private val properties: TickerClientProperties,
    private val environment: Environment,
    private val restClient: RestClient,
    private val maxAttempts: Int = 3,
    private val retryDelayMs: Long = 2000,
) : DisposableBean {
    private val log = LoggerFactory.getLogger(TickerClientRegistrar::class.java)
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "ticker-heartbeat").apply { isDaemon = true } }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        val collectorUrl = properties.collectorUrl
        val url = properties.url
        if (collectorUrl == null || url == null) {
            log.warn("ticker.client enabled but collector-url or url is unset; self-registration skipped.")
            return
        }
        val name = properties.name ?: environment.getProperty("spring.application.name") ?: "unknown"
        val request = RegistrationRequest(name = name, type = properties.type, url = url, tags = properties.tags)
        val endpoint = "${collectorUrl.trimEnd('/')}/api/targets"

        register(endpoint, request) // initial registration, with retry

        val interval = properties.heartbeatInterval
        if (!interval.isZero && !interval.isNegative) {
            scheduler.scheduleAtFixedRate(
                { heartbeat(endpoint, request) },
                interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS,
            )
            log.info("Ticker heartbeat every {} -> {}", interval, endpoint)
        }
    }

    private fun register(endpoint: String, request: RegistrationRequest) {
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

    /** A single re-POST per beat — the interval IS the retry cadence, so failures just wait for the next beat (logged at debug, not warn). */
    private fun heartbeat(endpoint: String, request: RegistrationRequest) {
        try {
            restClient.post().uri(endpoint).body(request).retrieve().toBodilessEntity()
            log.debug("Heartbeat re-registered '{}' with {}", request.name, endpoint)
        } catch (e: Exception) {
            log.debug("Heartbeat for '{}' failed ({}); will retry next interval", request.name, e.message)
        }
    }

    override fun destroy() {
        scheduler.shutdownNow()
    }
}
