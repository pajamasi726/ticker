package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.ApiError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RuleUpdate(
    val enabled: Boolean? = null,
    val threshold: Double? = null,
    val cooldownSeconds: Long? = null,
    val forSeconds: Long? = null,
)

data class SilenceRequest(val minutes: Long)
data class SilenceView(val active: Boolean, val until: java.time.Instant?)

@RestController
@RequestMapping("/api/alerts")
class MetricAlertController(
    private val rules: MetricAlertStore,
    private val silence: AlertSilence = AlertSilence(),
) {

    @GetMapping("/rules")
    fun listRules(): List<MetricAlertRule> = rules.all()

    @PutMapping("/rules/{key}")
    fun updateRule(
        @PathVariable key: String,
        @RequestBody body: RuleUpdate,
    ): ResponseEntity<Any> {
        val updated = try {
            rules.update(key, body.enabled, body.threshold, body.cooldownSeconds, body.forSeconds)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body<Any>(ApiError("INVALID_THRESHOLD", e.message ?: "Invalid threshold"))
        }
        return if (updated == null) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body<Any>(ApiError("RULE_NOT_FOUND", "No alert rule with key '$key'"))
        } else {
            ResponseEntity.ok<Any>(updated)
        }
    }

    @GetMapping("/recent")
    fun recentFires(): List<AlertFire> = rules.recent()

    // --- Deploy/maintenance silence window: deploys and incidents are different events. ---
    // A pipeline calls POST before rolling instances; anything still DOWN when the window ends is
    // announced then, so a silence can never swallow a real outage.

    @GetMapping("/silence")
    fun silenceStatus(): SilenceView = SilenceView(silence.isActive(), silence.until)

    @PostMapping("/silence")
    fun startSilence(@RequestBody body: SilenceRequest): ResponseEntity<Any> {
        if (body.minutes <= 0 || body.minutes > 24 * 60) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body<Any>(ApiError("INVALID_SILENCE", "minutes must be between 1 and 1440"))
        }
        val until = silence.silenceFor(body.minutes)
        return ResponseEntity.ok<Any>(SilenceView(active = true, until = until))
    }

    @DeleteMapping("/silence")
    fun clearSilence(): SilenceView {
        silence.clear()
        return SilenceView(active = false, until = null)
    }
}
