val ktorVersion = "2.3.12"
val jacksonVersion = "2.17.2"
val logbackVersion = "1.5.6"
val mockOauthVersion = "2.1.8"
val mockkVersion = "1.13.11"

project.setProperty("mainClassName", "io.ktor.server.netty.EngineMain")

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
    id("io.ktor.plugin") version "2.3.12"
}

application {
    // Define the main class for the application
    mainClass.set(project.property("mainClassName").toString())
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
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.2")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("no.nav.security:token-validation-ktor-v2:5.0.1")
    implementation("com.github.navikt.dp-biblioteker:oauth2-klient:2024.07.05-16.15.2dc72cf50576")
    implementation("com.auth0:java-jwt:4.4.0")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauthVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    implementation(libs.kotlin.logging)

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
        systemProperties["TOKEN_X_WELL_KNOWN_URL"] = "tokenx.dev.nav.no"
        systemProperties["TOKEN_X_CLIENT_ID"] = "test:meldekort:meldekortservice"
        systemProperties["MELDEKORTSERVICE_URL"] = "http://127.0.0.1:8090/meldekortservice/api"

        mainClass.set(project.property("mainClassName").toString())
        classpath = sourceSets["main"].runtimeClasspath
    }
}
