val ktorVersion = "2.3.11"
val jacksonVersion = "2.17.1"
val logbackVersion = "1.5.6"
val mockOauthVersion = "2.1.5"
val mockkVersion = "1.13.11"

sourceSets {
    this.getByName("main") {
        this.kotlin.srcDir("src/main/kotlin")
        this.resources.srcDir("src/main/resources")
    }
    this.getByName("test") {
        this.kotlin.srcDir("src/test/kotlin")
        this.resources.srcDir("src/test/resources")
    }
}

plugins {
    // Apply the org.jetbrains.kotlin.jvm plugin to add support for Kotlin.
    kotlin("jvm") version "2.0.0"

    // Apply the application plugin to add support for building a CLI application in Java.
    application

    // Apply the Ktor plugin to create the application distribution
    id("io.ktor.plugin") version "2.3.11"
}

application {
    // Define the main class for the application
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("no.nav.security:token-validation-ktor-v2:4.1.7")
    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:2024.05.15-10.36.c98cfe9cb526")
    implementation("com.auth0:java-jwt:4.4.0")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauthVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")

    // Use the Kotlin JUnit 5 integration.
    testImplementation(kotlin("test-junit5"))
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
