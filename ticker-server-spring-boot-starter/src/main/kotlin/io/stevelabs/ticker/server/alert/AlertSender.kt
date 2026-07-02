package io.stevelabs.ticker.server.alert

/** Sends an alert somewhere. The seam that keeps the alert services unit-testable without HTTP. */
interface AlertSender {
    fun send(message: AlertMessage)
}
