package io.stevelabs.ticker.server.check

enum class CheckOutcome { SUCCESS, DEGRADED, FAILURE }

data class CheckResult(val outcome: CheckOutcome, val latencyMs: Int, val detail: String? = null)
