package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.arenameldepliktadapter.models.Aktivitetstidslinje
import no.nav.dagpenger.arenameldepliktadapter.models.Dag
import no.nav.dagpenger.arenameldepliktadapter.models.Meldekort
import no.nav.dagpenger.arenameldepliktadapter.models.Periode
import no.nav.dagpenger.arenameldepliktadapter.models.Person
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import no.nav.dagpenger.arenameldepliktadapter.utils.getEnv
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config
import java.time.Duration

fun Route.meldekortApi() {
    route("/meldekort/{ident}") {
        get {
            val httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    jackson { defaultObjectMapper }
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = Duration.ofSeconds(60).toMillis()
                    requestTimeoutMillis = Duration.ofSeconds(60).toMillis()
                    socketTimeoutMillis = Duration.ofSeconds(60).toMillis()
                }
                expectSuccess = false
            }

            val tokenProvider = azureAdTokenSupplier(getEnv("MELDEKORTSERVICE_SCOPE") ?: "")
            val ident = call.parameters["ident"]

            if (ident.isNullOrBlank() || ident.length != 11) {
                call.respond(HttpStatusCode.BadRequest)
            }

            val response = httpClient.get(getEnv("MELDEKORTSERVICE_URL") + "/v2/meldekort") {
                header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
                header(HttpHeaders.Accept,  ContentType.Application.Json)
                header(HttpHeaders.ContentType,  ContentType.Application.Json)
                header("ident", ident)
            }

            val status = response.status
            val person: Person = defaultObjectMapper.readValue<Person>(response.bodyAsText())

            val rapporteringsperioder = person.meldekortListe?.filter { meldekort ->
                meldekort.hoyesteMeldegruppe in arrayOf("ARBS", "DAGP")
                        && meldekort.beregningstatus in arrayOf("OPPRE", "SENDT")
            }?.map { meldekort ->
                Rapporteringsperiode(
                    ident!!,
                    meldekort.meldekortId,
                    Periode(
                        meldekort.fraDato,
                        meldekort.tilDato,
                        meldekort.tilDato.minusDays(1)
                    ),
                    Aktivitetstidslinje(),
                    kanKorrigeres(meldekort, person.meldekortListe)
                )
            } ?: emptyList()

            httpClient.close()

            println("######")
            println(status)
            println(rapporteringsperioder)
            println(tokenProvider.invoke())
            println("######")

            call.respondText(defaultObjectMapper.writeValueAsString(rapporteringsperioder), ContentType.Application.Json)
        }
    }
}

private fun kanKorrigeres(meldekort: Meldekort, meldekortListe: List<Meldekort>): Boolean {
    return if (meldekort.kortType == "10" || meldekort.beregningstatus == "UBEHA") {
        false
    } else {
        meldekortListe.find { mk -> (meldekort.meldekortId != mk.meldekortId && meldekort.meldeperiode == mk.meldeperiode && mk.kortType == "10") } == null
    }
}

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
