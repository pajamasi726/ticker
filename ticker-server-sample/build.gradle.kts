plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation(project(":ticker-server-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")  // @SpringBootApplication + runApplication not transitively exposed by starter
    implementation("org.springframework.boot:spring-boot-starter-actuator")  // actuator not pulled in by starter
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}

tasks.bootBuildImage { imageName.set("ticker:latest") }
tasks.jar { enabled = false }      // single executable artifact (the bootJar)
tasks.test { useJUnitPlatform() }
