package io.stevelabs.ticker.server.poll

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.check.CheckOutcome
import io.stevelabs.ticker.server.check.CheckResult
import io.stevelabs.ticker.server.check.HealthChecker
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.state.ServiceState
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Executors

class PollerTest {
    private fun stub(type: ServiceType, outcome: CheckOutcome) = object : HealthChecker {
        override fun supports(t: ServiceType) = t == type
        override fun check(target: Target) = CheckResult(outcome, 5)
    }

    @Test fun `pollAll checks every target and updates the store`() {
        val registry = TargetRegistry(listOf(
            TargetDefinition("up", ServiceType.SPRING, "http://up"),
            TargetDefinition("down", ServiceType.HTTP, "http://down"),
        ))
        val props = PollProperties(failureThreshold = 1)   // 1 failure -> DOWN immediately
        val store = HealthStateStore(registry, props)
        val poller = Poller(
            registry,
            listOf(stub(ServiceType.SPRING, CheckOutcome.SUCCESS), stub(ServiceType.HTTP, CheckOutcome.FAILURE)),
            store,
            Executors.newVirtualThreadPerTaskExecutor(),
            props,
        )

        poller.pollAll()

        val byName = store.snapshot().associateBy { it.target.name }
        assertThat(byName.getValue("up").state).isEqualTo(ServiceState.UP)
        assertThat(byName.getValue("down").state).isEqualTo(ServiceState.DOWN)
    }

    @Test fun `pollAll records FAILURE when a check hangs past the bounded await`() {
        // timeout=200ms → awaitMs = max(400, 1000) = 1000ms; stub sleeps 1500ms → must timeout
        val props = PollProperties(failureThreshold = 1, timeout = Duration.ofMillis(200))
        val registry = TargetRegistry(listOf(
            TargetDefinition("slow", ServiceType.HTTP, "http://slow"),
        ))
        val store = HealthStateStore(registry, props)

        val slowChecker = object : HealthChecker {
            override fun supports(t: ServiceType) = t == ServiceType.HTTP
            override fun check(target: Target): CheckResult {
                Thread.sleep(1500)
                return CheckResult(CheckOutcome.SUCCESS, 1500)
            }
        }

        val poller = Poller(
            registry,
            listOf(slowChecker),
            store,
            Executors.newVirtualThreadPerTaskExecutor(),
            props,
        )

        poller.pollAll()

        val byName = store.snapshot().associateBy { it.target.name }
        assertThat(byName.getValue("slow").state).isEqualTo(ServiceState.DOWN)
    }
}
