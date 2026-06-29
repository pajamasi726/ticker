package io.stevelabs.ticker.server.poll

import io.stevelabs.ticker.server.check.HealthChecker
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.util.concurrent.ExecutorService

class Poller(
    private val registry: TargetRegistry,
    private val checkers: List<HealthChecker>,
    private val store: HealthStateStore,
    private val executor: ExecutorService,
) {
    private val log = LoggerFactory.getLogger(Poller::class.java)

    @Scheduled(fixedRateString = "\${ticker.poll.interval:10s}")
    fun pollAll() {
        val targets = registry.all()
        if (targets.isEmpty()) return
        val now = Instant.now()
        val futures = targets.map { t ->
            executor.submit {
                val checker = checkers.firstOrNull { it.supports(t.type) }
                if (checker == null) { log.warn("No HealthChecker for {} ({})", t.name, t.type); return@submit }
                store.record(t.id, checker.check(t), now)
            }
        }
        futures.forEach { runCatching { it.get() }.onFailure { log.warn("poll task failed", it) } }
    }
}
