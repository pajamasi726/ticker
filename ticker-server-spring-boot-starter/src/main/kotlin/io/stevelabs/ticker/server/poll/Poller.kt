package io.stevelabs.ticker.server.poll

import io.stevelabs.ticker.server.check.CheckOutcome
import io.stevelabs.ticker.server.check.CheckResult
import io.stevelabs.ticker.server.check.HealthChecker
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class Poller(
    private val registry: TargetRegistry,
    private val checkers: List<HealthChecker>,
    private val store: HealthStateStore,
    private val executor: ExecutorService,
    private val pollProperties: PollProperties,
) {
    private val log = LoggerFactory.getLogger(Poller::class.java)

    @Scheduled(fixedRateString = "\${ticker.poll.interval:10s}")
    fun pollAll() {
        val targets = registry.all()
        if (targets.isEmpty()) return
        val now = Instant.now()
        val awaitMs = (pollProperties.timeout.toMillis() * 2).coerceAtLeast(1000)

        val pending = targets.map { target ->
            target to executor.submit<CheckResult?> {
                val checker = checkers.firstOrNull { it.supports(target.type) }
                if (checker == null) {
                    log.warn("No HealthChecker for {} ({})", target.name, target.type)
                    null
                } else {
                    checker.check(target)
                }
            }
        }

        for ((target, future) in pending) {
            val result: CheckResult? = try {
                future.get(awaitMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                log.warn("Health check for {} exceeded {}ms; recording FAILURE", target.name, awaitMs)
                CheckResult(CheckOutcome.FAILURE, awaitMs.toInt(), "check timeout")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("Poller tick interrupted", e)
                return
            } catch (e: ExecutionException) {
                log.warn("Health check task failed for {}", target.name, e)
                CheckResult(CheckOutcome.FAILURE, 0, e.cause?.javaClass?.simpleName ?: "error")
            }
            if (result != null) store.record(target.id, result, now)
        }
    }
}
