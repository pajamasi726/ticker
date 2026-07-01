// Spring Boot 3.x variant of the client starter.
//
// Same source as `ticker-client-spring-boot-starter` (shared via srcDir below), but compiled
// against the Spring Boot 3.x BOM and emitting Java 17 bytecode — so apps still on Boot 3.2+
// (Java 17+) can self-register with a Boot 4 collector. The two starters are independent
// artifacts: each is built and tested against its own BOM, so a change to one cannot break the
// other. The collector (server) stays on Boot 4 / Java 21 regardless.
//
// Maven Central publishing (manual, later): add the `signing` plugin + a Central Portal
// publisher, a GPG key, and a User Token — all from the environment, never committed.

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

// Java 17 bytecode: Boot 3.x apps commonly run on Java 17.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}
tasks.withType<JavaCompile> { options.release = 17 }  // keep compileJava in sync with Kotlin's 17 target

// Share the Boot 4 starter's source verbatim — one copy of the logic, compiled here against the
// Boot 3 BOM. If a source ever needs to diverge by Boot version, split just that file.
sourceSets {
    main {
        kotlin.srcDir("../ticker-client-spring-boot-starter/src/main/kotlin")
        resources.srcDir("../ticker-client-spring-boot-starter/src/main/resources")
    }
    test {
        kotlin.srcDir("../ticker-client-spring-boot-starter/src/test/kotlin")
    }
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.0") }
}

// Spring Boot 3.5's BOM manages an OLDER Kotlin version. Left alone, that older Kotlin lands on
// this module's classpath and the Kotlin 2.3 Gradle plugin's classpath-snapshot transform blows up
// (NoSuchMethodError BuildUtilKt.clearJarCaches). Pin every Kotlin artifact to the plugin's version
// so the compile classpath matches the compiler.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.3.21")
    }
}

dependencies {
    api(project(":ticker-core"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-web")
    implementation("org.slf4j:slf4j-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // No JSON library: the registrar hand-builds its payload (see the Boot 4 starter).
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }
