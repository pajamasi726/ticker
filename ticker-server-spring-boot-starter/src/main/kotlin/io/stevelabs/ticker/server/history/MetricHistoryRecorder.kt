package io.stevelabs.ticker.server.history

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.state.HealthStateStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant

class MetricHistoryRecorder(
    private val store: HealthStateStore,
    private val metricSource: MetricSource,
    private val repo: MetricHistoryRepository,
    private val archiver: HistoryArchiver,
    private val props: HistoryProperties,
) {
    private val log = LoggerFactory.getLogger(MetricHistoryRecorder::class.java)

    @Scheduled(fixedRateString = "\${ticker.history.sample-interval:15s}")
    fun record() {
        val now = System.currentTimeMillis()
        store.snapshot(Instant.now())
            .filter { it.target.type == ServiceType.SPRING }
            .forEach { th ->
                val samples = metricSource.fetch(th.target)
                    .flatMap { it.widgets }
                    .mapNotNull { widget -> widget.value?.let { widget.key to it } }
                if (samples.isNotEmpty()) {
                    try {
                        repo.saveAll(th.target.id, samples, now)
                    } catch (e: Exception) {
                        log.debug("Failed to save metric history for '{}': {}", th.target.id, e.message)
                    }
                }
            }
    }

    @Scheduled(fixedRate = 3_600_000L)
    fun prune() {
        val cutoff = System.currentTimeMillis() - props.retention.toMillis()
        if (props.archive.enabled) {
            val rows = repo.selectBefore(cutoff)
            if (rows.isNotEmpty()) {
                try {
                    val file = archiver.archive(rows, System.currentTimeMillis())
                    if (!archiver.verify(file, rows.size)) {
                        log.warn(
                            "History archive verify failed for {} rows ({}). NOT pruning — will retry next cycle.",
                            rows.size,
                            file,
                        )
                        return
                    }
                    log.info("Archived {} metric_sample rows to {} before prune.", rows.size, file)
                } catch (e: Exception) {
                    log.warn(
                        "History archive failed ({}). NOT pruning — will retry next cycle.",
                        e.message,
                    )
                    return
                }
            }
        }
        val pruned = repo.prune(cutoff)
        log.debug("Pruned {} metric_sample rows", pruned)
    }
}
