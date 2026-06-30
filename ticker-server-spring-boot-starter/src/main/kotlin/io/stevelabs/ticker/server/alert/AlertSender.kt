package io.stevelabs.ticker.server.alert

/** Sends an alert message somewhere. The seam that keeps AlertService unit-testable without HTTP. */
interface AlertSender {
    fun send(text: String)
}
