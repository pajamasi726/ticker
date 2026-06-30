pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.21"
        kotlin("plugin.spring") version "2.3.21"
        id("org.springframework.boot") version "4.1.0"
        id("io.spring.dependency-management") version "1.1.7"
    }
    repositories { gradlePluginPortal(); mavenCentral() }
}

rootProject.name = "ticker"

include("ticker-core")
include("ticker-client-spring-boot-starter")
include("ticker-server-spring-boot-starter")
include("ticker-server-sample")
include("ticker-client-sample")
