plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation(project(":ticker-client-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")
}

tasks.bootBuildImage { imageName.set("ticker-client-sample:latest") }
tasks.jar { enabled = false }      // single executable artifact (the bootJar)
tasks.test { useJUnitPlatform() }
