val ktorVersion = "3.2.2"
val jacksonVersion = "2.19.2"
val kotlinLoggingVersion = "3.0.5"
val logbackVersion = "1.5.18"
val logstashEncoderVersion = "8.1"
val mockOauthVersion = "2.2.1"
val mockkVersion = "1.14.5"

project.setProperty("mainClassName", "io.ktor.server.cio.EngineMain")

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
    kotlin("jvm") version "2.2.0"

    // Apply the application plugin to add support for building a CLI application in Java.
    application

    // Apply the Ktor plugin to create the application distribution
    id("io.ktor.plugin") version "3.2.2"
}

application {
    // Define the main class for the application
    mainClass.set(project.property("mainClassName").toString())
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.2")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("no.nav.security:token-validation-ktor-v3:5.0.30")
    implementation("no.nav.dagpenger:oauth2-klient:2025.04.26-14.51.bbf9ece5f5ec")
    implementation("com.auth0:java-jwt:4.5.0")

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

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

tasks {
    withType<ProcessResources> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    named<Test>("test") {
        // Use JUnit Platform for unit tests.
        useJUnitPlatform()
    }

    register("runServerTest", JavaExec::class) {
        systemProperties["RUNNING_LOCALLY"] = "true"
        systemProperties["TOKEN_X_WELL_KNOWN_URL"] = "tokenx.dev.nav.no"
        systemProperties["TOKEN_X_CLIENT_ID"] = "test:meldekort:meldekortservice"
        systemProperties["AZURE_APP_WELL_KNOWN_URL"] = "azure.dev.nav.no"
        systemProperties["AZURE_APP_CLIENT_ID"] = "test:meldekort:meldekortservice"
        systemProperties["MELDEKORTSERVICE_URL"] = "http://127.0.0.1:8090/meldekortservice/api"

        mainClass.set(project.property("mainClassName").toString())
        classpath = sourceSets["main"].runtimeClasspath
    }
}
