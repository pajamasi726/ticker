package io.stevelabs.ticker.sample

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TickerServerSampleApplication

fun main(args: Array<String>) {
    runApplication<TickerServerSampleApplication>(*args)
}
