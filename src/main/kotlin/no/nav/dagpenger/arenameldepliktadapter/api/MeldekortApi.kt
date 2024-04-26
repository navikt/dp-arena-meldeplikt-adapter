package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.meldekortApi() {
    route("/meldekort") {
        get {
            val httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    jackson()
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = 5000
                    requestTimeoutMillis = 10000
                    socketTimeoutMillis = 10000
                }
                expectSuccess = false
            }

            val ordsUrl = getEnv("ORDS_URI")
            val ordsClientId = getEnv("CLIENT_ID")
            val ordsClientSecret = getEnv("CLIENT_SECRET")

            val ARENA_ORDS_TOKEN_PATH = "/api/oauth/token"

            /*
            val response = httpClient.post("$ordsUrl$ARENA_ORDS_TOKEN_PATH?grant_type=client_credentials") {
                val base = "${ordsClientId}:${ordsClientSecret}"
                headers.append("Accept", "application/json; charset=UTF-8")
                headers.append("Authorization", "Basic ${Base64.getEncoder().encodeToString(base.toByteArray())}")
            }

            val token: AccessToken = response.body()
             */

            httpClient.close()

            call.respondText("Meldekort")
        }
    }
}

fun getEnv(propertyName: String): String? {
    return System.getProperty(propertyName, System.getenv(propertyName))
}

data class AccessToken(
    @JsonProperty("access_token")
    val accessToken: String?,
    @JsonProperty("token_type")
    val tokenType: String?,
    @JsonProperty("expires_in")
    val expiresIn: Int?
)

