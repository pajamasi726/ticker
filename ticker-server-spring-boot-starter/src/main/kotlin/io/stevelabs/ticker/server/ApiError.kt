package io.stevelabs.ticker.server

/** Small error envelope — errors return {code, message}, never a stack trace (CLAUDE.md convention). */
data class ApiError(val code: String, val message: String)
