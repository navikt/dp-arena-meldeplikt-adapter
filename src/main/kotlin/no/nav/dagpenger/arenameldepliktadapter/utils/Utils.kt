package no.nav.dagpenger.arenameldepliktadapter.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import java.time.Duration

fun getEnv(propertyName: String): String? {
    return System.getProperty(propertyName, System.getenv(propertyName))
}

val defaultObjectMapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

lateinit var httpClient: HttpClient
fun defaultHttpClient(): HttpClient {
    if (!::httpClient.isInitialized) {
        httpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = Duration.ofSeconds(60).toMillis()
                requestTimeoutMillis = Duration.ofSeconds(60).toMillis()
                socketTimeoutMillis = Duration.ofSeconds(60).toMillis()
            }
            expectSuccess = false
        }
    }

    return httpClient
}
