import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

// Root: shared coordinates + test-coverage (JaCoCo) for every JVM module.
subprojects {
    group = "io.stevelabs"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }

    apply(plugin = "jacoco")

    // Wire coverage only for modules that actually compile+test (the java/kotlin plugin).
    plugins.withId("java") {
        tasks.named<Test>("test") {
            finalizedBy("jacocoTestReport")
        }
        tasks.named<JacocoReport>("jacocoTestReport") {
            dependsOn("test")
            reports {
                xml.required.set(true)   // for CI / tooling
                html.required.set(true)  // build/reports/jacoco/test/html/index.html
            }
        }

        // Enforce a floor on the core logic module only (samples/core are thin). `check` fails below it.
        if (name == "ticker-server-spring-boot-starter") {
            tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
                dependsOn("test")
                violationRules {
                    rule { limit { counter = "LINE"; minimum = "0.80".toBigDecimal() } }
                }
            }
            tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
        }
    }
}
