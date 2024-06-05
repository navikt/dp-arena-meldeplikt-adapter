package no.nav.dagpenger.arenameldepliktadapter.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import java.time.Duration
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

fun getEnv(propertyName: String): String? {
    return System.getProperty(propertyName, System.getenv(propertyName))
}

fun isCurrentlyRunningOnNais(): Boolean {
    return System.getenv("NAIS_APP_NAME") != null
}

fun ApplicationCallPipeline.installServerContentNegotiation() {
    install(ServerContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}

fun HttpClientConfig<*>.defaultHttpClientConfig() {
    install(HttpTimeout) {
        connectTimeoutMillis = Duration.ofSeconds(60).toMillis()
        requestTimeoutMillis = Duration.ofSeconds(60).toMillis()
        socketTimeoutMillis = Duration.ofSeconds(60).toMillis()
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    expectSuccess = false
}

lateinit var httpClient: HttpClient
fun defaultHttpClient(): HttpClient {
    if (!::httpClient.isInitialized) {
        httpClient = HttpClient(CIO) {
            defaultHttpClientConfig()
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
