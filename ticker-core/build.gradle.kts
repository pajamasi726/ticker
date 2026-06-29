// Maven Central publishing (manual, later): add the `signing` plugin + a Central Portal
// publisher (e.g. com.vanniktech.maven.publish), a GPG key, and a User Token — all from the
// environment, never committed. Namespace io.stevelabs is verified via a DNS TXT on stevelabs.io.

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("Ticker — internal service liveness board (SBA-style). Module: ${project.name}.")
                url.set("https://github.com/stevelabs/ticker")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers { developer { id.set("stevelabs"); name.set("SteveLabs") } }
                scm {
                    url.set("https://github.com/stevelabs/ticker")
                    connection.set("scm:git:https://github.com/stevelabs/ticker.git")
                }
            }
        }
    }
}
