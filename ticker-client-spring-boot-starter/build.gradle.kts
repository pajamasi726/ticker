// Maven Central publishing (manual, later): add the `signing` plugin + a Central Portal
// publisher (e.g. com.vanniktech.maven.publish), a GPG key, and a User Token — all from the
// environment, never committed. Namespace io.stevelabs is verified via a DNS TXT on stevelabs.io.

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
    `maven-publish`
    id("io.spring.dependency-management")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0") }
}

dependencies {
    api(project(":ticker-core"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-web")
    implementation("org.slf4j:slf4j-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation("tools.jackson.module:jackson-module-kotlin")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }

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
