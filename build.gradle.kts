val ktorVersion = "2.3.10"
val logbackVersion = "1.5.6"

sourceSets {
    this.getByName("main"){
        this.kotlin.srcDir("src/main/kotlin")
        this.resources.srcDir("src/main/resources")
    }
    this.getByName("test"){
        this.kotlin.srcDir("src/test/kotlin")
        this.resources.srcDir("src/test/resources")
    }
}

plugins {
    // Apply the org.jetbrains.kotlin.jvm plugin to add support for Kotlin.
    kotlin("jvm") version "1.9.23"

    // Apply the application plugin to add support for building a CLI application in Java.
    application

    // Apply the Ktor plugin to create the application distribution
    id("io.ktor.plugin") version "2.3.10"
}

application {
    // Define the main class for the application
    mainClass.set("no.nav.dagpenger.arenameldepliktadapter.AppKt")
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

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
