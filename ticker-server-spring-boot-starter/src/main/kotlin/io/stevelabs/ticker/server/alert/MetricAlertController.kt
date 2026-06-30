package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.ApiError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RuleUpdate(
    val enabled: Boolean? = null,
    val threshold: Double? = null,
    val cooldownSeconds: Long? = null,
)

@RestController
@RequestMapping("/api/alerts")
class MetricAlertController(private val rules: MetricAlertStore) {

    @GetMapping("/rules")
    fun listRules(): List<MetricAlertRule> = rules.all()

    @PutMapping("/rules/{key}")
    fun updateRule(
        @PathVariable key: String,
        @RequestBody body: RuleUpdate,
    ): ResponseEntity<Any> {
        val updated = try {
            rules.update(key, body.enabled, body.threshold, body.cooldownSeconds)
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
}
