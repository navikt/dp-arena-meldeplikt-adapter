package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.arenameldepliktadapter.models.Dag
import no.nav.dagpenger.arenameldepliktadapter.models.Meldekort
import no.nav.dagpenger.arenameldepliktadapter.models.Periode
import no.nav.dagpenger.arenameldepliktadapter.models.Person
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.utils.decodeToken
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import no.nav.dagpenger.arenameldepliktadapter.utils.extractSubject
import no.nav.dagpenger.arenameldepliktadapter.utils.getEnv
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config
import java.time.LocalDate

fun Routing.meldekortApi(httpClient: HttpClient) {
    authenticate {
        route("/meldekort") {
            get {
                val decodedToken = decodeToken(call.request.header(HttpHeaders.Authorization))
                val ident = extractSubject(decodedToken)

                if (ident.isNullOrBlank() || ident.length != 11) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                var retries = 0
                var response: HttpResponse

                do {
                    response = sendHttpRequest(httpClient, ident)
                    retries++
                } while (response.status != HttpStatusCode.OK && retries < 3)

                val person: Person = defaultObjectMapper.readValue<Person>(response.bodyAsText())

                val rapporteringsperioder = person.meldekortListe?.filter { meldekort ->
                    meldekort.hoyesteMeldegruppe in arrayOf("ARBS", "DAGP")
                            && meldekort.beregningstatus in arrayOf("OPPRE", "SENDT")
                }?.map { meldekort ->
                    val kanSendesFra = meldekort.tilDato.minusDays(1)

                    Rapporteringsperiode(
                        meldekort.meldekortId,
                        Periode(
                            meldekort.fraDato,
                            meldekort.tilDato
                        ),
                        List(14) { index -> Dag(meldekort.fraDato.plusDays(index.toLong()), emptyList()) },
                        kanSendesFra,
                        !LocalDate.now().isBefore(kanSendesFra),
                        kanKorrigeres(meldekort, person.meldekortListe)
                    )
                } ?: emptyList()

                call.respondText(
                    defaultObjectMapper.writeValueAsString(rapporteringsperioder),
                    ContentType.Application.Json
                )
            }
        }
    }
}

private suspend fun sendHttpRequest(httpClient: HttpClient, ident: String): HttpResponse {
    val tokenProvider = azureAdTokenSupplier(getEnv("MELDEKORTSERVICE_SCOPE") ?: "")

    return httpClient.get(getEnv("MELDEKORTSERVICE_URL") + "/v2/meldekort") {
        header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header("ident", ident)
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
