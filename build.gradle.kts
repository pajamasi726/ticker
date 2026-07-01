import org.gradle.testing.jacoco.tasks.JacocoReport

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
    }
}
