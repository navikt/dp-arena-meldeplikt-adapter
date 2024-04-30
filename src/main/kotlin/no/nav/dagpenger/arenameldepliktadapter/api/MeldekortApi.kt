package no.nav.dagpenger.arenameldepliktadapter.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.xml.xml
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config
import java.time.Duration

fun Route.meldekortApi() {
    route("/meldekort/{fnr}") {
        get {
            val httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    xml()
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = Duration.ofSeconds(60).toMillis()
                    requestTimeoutMillis = Duration.ofSeconds(60).toMillis()
                    socketTimeoutMillis = Duration.ofSeconds(60).toMillis()
                }
                expectSuccess = false
            }

            val tokenProvider = dpProxyTokenProvider()

            val response = httpClient.get(getEnv("DP_PROXY_URL") + "/v2/meldeplikt/meldekort") {
                header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
                header(HttpHeaders.Accept, "application/xml")
                // header(HttpHeaders.XRequestId, requestId)
                // header(HttpHeaders.XCorrelationId, eksternId)
                /*
                if (meldekortId != null) {
                    header("meldekortId", meldekortId)
                }
                if (fnr != null) {
                    header("fnr", fnr)
                }
                */
                header("fnr", call.parameters["fnr"])
            }

            httpClient.close()

            call.respondText(response.bodyAsText())
        }
    }
}

fun getEnv(propertyName: String): String? {
    return System.getProperty(propertyName, System.getenv(propertyName))
}

fun dpProxyTokenProvider(): () -> String = azureAdTokenSupplier(getEnv("DP_PROXY_SCOPE") ?: "")

private fun azureAdTokenSupplier(scope: String): () -> String = {
    runBlocking { azureAdClient.clientCredentials(scope).accessToken }
}

private val azureAdClient: CachedOauth2Client by lazy {
    val config = HashMap<String, String>()
    config["AZURE_APP_CLIENT_ID"] = getEnv("AZURE_APP_CLIENT_ID") ?: ""
    config["AZURE_APP_CLIENT_SECRET"] = getEnv("AZURE_APP_CLIENT_SECRET") ?: ""
    config["AZURE_APP_JWK"] = getEnv("AZURE_APP_JWK") ?: ""
    config["AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"] = getEnv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT") ?: ""
    config["AZURE_APP_WELL_KNOWN_URL"] = getEnv("AZURE_APP_WELL_KNOWN_URL") ?: ""

    val azureAdConfig = OAuth2Config.AzureAd(config)
    CachedOauth2Client(
        tokenEndpointUrl = azureAdConfig.tokenEndpointUrl,
        authType = azureAdConfig.clientSecret(),
    )
}
