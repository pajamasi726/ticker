package io.stevelabs.ticker.clientsample

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TickerClientSampleApplication

fun main(args: Array<String>) {
    runApplication<TickerClientSampleApplication>(*args)
}
