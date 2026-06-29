package io.stevelabs.ticker.server.check

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.target.Target

interface HealthChecker {
    fun supports(type: ServiceType): Boolean
    fun check(target: Target): CheckResult
}

internal fun elapsedMs(startNanos: Long): Int = ((System.nanoTime() - startNanos) / 1_000_000).toInt()
