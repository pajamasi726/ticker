package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.detail.MetricRef
import io.stevelabs.ticker.server.detail.Unit
import java.time.Instant

enum class Comparator { GT, LT }

data class MetricAlertRule(
    val key: String,
    val label: String,
    val metric: MetricRef,
    val statistic: String = "VALUE",
    val over: MetricRef? = null,
    val comparator: Comparator,
    val threshold: Double,
    val unit: Unit,
    val cooldownSeconds: Long = 300,
    val enabled: Boolean = true,
) {
    /** Pure decision — no Spring, no I/O. Quantity is null when the metric could not be resolved. */
    fun breaches(quantity: Double?): Boolean {
        if (quantity == null) return false
        return when (comparator) {
            Comparator.GT -> quantity > threshold
            Comparator.LT -> quantity < threshold
        }
    }

    companion object {
        val DEFAULTS: List<MetricAlertRule> = listOf(
            MetricAlertRule(
                key = "cpu-process",
                label = "CPU (process)",
                metric = MetricRef("process.cpu.usage"),
                comparator = Comparator.GT,
                threshold = 0.80,
                unit = Unit.PERCENT,
            ),
            MetricAlertRule(
                key = "cpu-system",
                label = "CPU (system)",
                metric = MetricRef("system.cpu.usage"),
                comparator = Comparator.GT,
                threshold = 0.90,
                unit = Unit.PERCENT,
            ),
            MetricAlertRule(
                key = "heap-used",
                label = "Heap used",
                metric = MetricRef("jvm.memory.used", mapOf("area" to "heap")),
                over = MetricRef("jvm.memory.max", mapOf("area" to "heap")),
                comparator = Comparator.GT,
                threshold = 0.85,
                unit = Unit.PERCENT,
            ),
            MetricAlertRule(
                key = "disk-free",
                label = "Disk free",
                metric = MetricRef("disk.free"),
                over = MetricRef("disk.total"),
                comparator = Comparator.LT,
                threshold = 0.10,
                unit = Unit.PERCENT,
            ),
            MetricAlertRule(
                key = "gc-overhead",
                label = "GC overhead",
                metric = MetricRef("jvm.gc.overhead"),
                comparator = Comparator.GT,
                threshold = 0.25,
                unit = Unit.PERCENT,
            ),
            MetricAlertRule(
                key = "files-open",
                label = "Open files",
                metric = MetricRef("process.files.open"),
                over = MetricRef("process.files.max"),
                comparator = Comparator.GT,
                threshold = 0.80,
                unit = Unit.PERCENT,
            ),
        )
    }
}

data class AlertFire(
    val targetId: String,
    val targetName: String,
    val ruleKey: String,
    val label: String,
    val value: Double,
    val threshold: Double,
    val unit: Unit,
    val at: Instant,
)
