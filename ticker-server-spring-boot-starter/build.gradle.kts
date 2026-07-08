// Maven Central publishing (manual, later): add the `signing` plugin + a Central Portal
// publisher (e.g. com.vanniktech.maven.publish), a GPG key, and a User Token — all from the
// environment, never committed. Namespace io.stevelabs is verified via a DNS TXT on stevelabs.io.

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
    id("com.vanniktech.maven.publish")
    id("io.spring.dependency-management")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0") }
}

dependencies {
    api(project(":ticker-core"))
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework:spring-jdbc")
    implementation("com.zaxxer:HikariCP")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Bundle the built SPA into this library jar's static/ (missing dist → no-op).
tasks.processResources {
    into("static") { from(rootProject.layout.projectDirectory.dir("frontend/dist")) }
}

tasks.test { useJUnitPlatform() }

// The admin view reports the collector's version from the jar manifest ("dev" when run from classes).
tasks.jar {
    manifest {
        attributes("Implementation-Title" to "ticker-server-spring-boot-starter", "Implementation-Version" to project.version)
    }
}
