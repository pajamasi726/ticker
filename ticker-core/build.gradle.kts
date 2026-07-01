// Published to Maven Central under io.stevelabs (Apache-2.0) via com.vanniktech.maven.publish.
// The common POM + signing are configured once in the root build; credentials + GPG key come
// from env only (never committed). The plugin adds the sources + javadoc jars automatically.

plugins {
    kotlin("jvm")
    `java-library`
    id("com.vanniktech.maven.publish")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

// Emit Java 17 bytecode: ticker-core is consumed by BOTH client starters, including the
// Spring Boot 3.x one that may run on a Java 17 JVM. (Compiled by the JDK 21 toolchain, but
// targeting 17 so the jar loads on Java 17+.)
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}
tasks.withType<JavaCompile> { options.release = 17 }  // keep compileJava in sync with Kotlin's 17 target

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
