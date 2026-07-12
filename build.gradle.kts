import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

// Load the Kotlin plugins once, on the root project's classloader (apply false = don't apply them
// to the root itself), so every subproject shares a single Kotlin-plugin classloader. Without this,
// Gradle warns "Kotlin Gradle plugin loaded multiple times in different subprojects" and Kotlin's
// incremental-compilation classpath snapshotter can fail (BuildUtilKt.clearJarCaches /
// ClasspathEntrySnapshotter). Versions come from settings.gradle.kts pluginManagement.
plugins {
    kotlin("jvm") apply false
    kotlin("plugin.spring") apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

// Root: shared coordinates + test-coverage (JaCoCo) for every JVM module.
subprojects {
    group = "io.stevelabs"
    version = "0.6.1"   // 0.6.1: map v3 — orthogonal lane/highway edge routing + longest-path layering (stays readable at fleet scale)
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

    // Central Portal publishing (only for modules that apply the vanniktech plugin — the 4
    // library modules, not the samples). Common POM here; credentials + signing key come from
    // env only (ORG_GRADLE_PROJECT_mavenCentral{Username,Password} / signingInMemoryKey*), never
    // committed (guardrail #5). Coordinates default to group:name:version.
    plugins.withId("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral()
            // Sign only when a signing key is present (env ORG_GRADLE_PROJECT_signingInMemoryKey,
            // or a gradle property). Keeps local builds + publishToMavenLocal working keyless; the
            // real Central publish sets the key and signs. Central rejects unsigned uploads, so a
            // forgotten key fails loudly at upload rather than shipping something broken.
            if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                signAllPublications()
            }
            pom {
                name.set(project.name)
                description.set("Ticker — self-hosted service liveness board + curated Spring Boot / JVM metrics dashboard. Module: ${project.name}.")
                url.set("https://github.com/pajamasi726/ticker")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("pajamasi726")
                        name.set("Steve Ye")
                        email.set("steve@stevelabs.io")
                        url.set("https://stevelabs.io")
                    }
                }
                scm {
                    url.set("https://github.com/pajamasi726/ticker")
                    connection.set("scm:git:git://github.com/pajamasi726/ticker.git")
                    developerConnection.set("scm:git:ssh://git@github.com/pajamasi726/ticker.git")
                }
            }
        }
    }
}
