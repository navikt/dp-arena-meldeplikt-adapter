package no.nav.dagpenger.arenameldepliktadapter.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
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

fun isCurrentlyRunningLocally(): Boolean {
    return getEnv("RUNNING_LOCALLY").toBoolean()
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

fun decodeToken(authString: String?): DecodedJWT? {
    if (authString == null) {
        return null
    }

    val token = authString.replace("Bearer ", "")

    if (token.isBlank()) {
        return null
    }

    return try {
        JWT.decode(token)
    } catch (e: Exception) {
        null
    }
}

fun extractSubject(decodedToken: DecodedJWT?): String? {
    if (isCurrentlyRunningLocally()) {
        return "01020312345"
    }

    if (decodedToken == null) {
        return null
    }

    val pid: Claim = decodedToken.getClaim("pid")
    val sub: Claim = decodedToken.getClaim("sub")

    if (!pid.isNull) {
        return pid.asString()
    } else if (!sub.isNull) {
        return sub.asString()
    }

    return null
}
