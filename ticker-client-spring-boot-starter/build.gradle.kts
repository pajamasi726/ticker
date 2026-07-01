// Spring Boot 4.x client starter. Published to Maven Central (io.stevelabs, Apache-2.0) via
// com.vanniktech.maven.publish; the common POM + signing are configured once in the root build.
// For apps on Spring Boot 3.2+, use the sibling ticker-client-spring-boot3-starter instead.

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

// Emit Java 17 bytecode so the starter loads on Java 17+ hosts. The registrar uses no Java 21
// APIs; virtual threads live only in the (Java 21) collector, not in this client.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}
tasks.withType<JavaCompile> { options.release = 17 }  // keep compileJava in sync with Kotlin's 17 target

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0") }
}

dependencies {
    api(project(":ticker-core"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-web")
    implementation("org.slf4j:slf4j-api")
    // Spring Boot binds this starter's Kotlin @ConfigurationProperties data class via CONSTRUCTOR
    // binding, which needs kotlin-reflect at runtime. Bundle it so binding works in ANY consumer
    // (incl. Java apps, or Kotlin apps without reflect); otherwise binding falls back to setters and
    // fails with "No setter found for property: collector-url".
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // No JSON library on the client: the registrar hand-builds its tiny JSON payload, so the
    // starter carries no Jackson-version dependency (Boot 3 = Jackson 2, Boot 4 = Jackson 3).
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }
