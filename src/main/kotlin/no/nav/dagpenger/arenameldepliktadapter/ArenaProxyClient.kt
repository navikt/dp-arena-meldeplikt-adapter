package no.nav.dagpenger.arenameldepliktadapter

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import mu.KotlinLogging
import no.nav.dagpenger.arenameldepliktadapter.models.ArenaMeldekort
import no.nav.dagpenger.arenameldepliktadapter.models.DatadelingRequest
import no.nav.dagpenger.arenameldepliktadapter.utils.Sikkerlogg
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import no.nav.dagpenger.arenameldepliktadapter.utils.getEnv
import no.nav.dagpenger.arenameldepliktadapter.utils.hentAzureToken

private val logger = KotlinLogging.logger {}

class ArenaProxyClient(
    private val httpClient: HttpClient,
) {
    suspend fun hentInnsendteMeldekort(request: DatadelingRequest): List<ArenaMeldekort> {
        logger.info("Henter innsendte meldekort fra dp-proxy")
        Sikkerlogg.info { "Henter innsendte meldekort fra dp-proxy med request: $request" }

        val dpProxyBaseUrl = getEnv("DP_PROXY_URL") ?: throw RuntimeException("DP_PROXY_URL er ikke satt")
        val urlString = "$dpProxyBaseUrl/proxy/v1/arena/innsendtemeldekort"

        val scope = getEnv("DP_PROXY_SCOPE") ?: throw RuntimeException("DP_PROXY_SCOPE er ikke satt")

        return runCatching {
            val response = httpClient.post(urlString) {
                headers {
                    append(HttpHeaders.Accept, "application/json")
                    append(HttpHeaders.Authorization, "Bearer ${hentAzureToken(scope)}")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(defaultObjectMapper.writeValueAsString(request))
            }
            if (response.status.value !in 200..299) {
                throw RuntimeException("dp-proxy svarte ${response.status.value} ${response.status.description}")
            }

            val responseBody = response.bodyAsText()
            Sikkerlogg.info { "Hentet innsendte meldekort fra dp-proxy: $responseBody" }

            defaultObjectMapper.readValue<List<ArenaMeldekort>>(responseBody)
        }.getOrElse {
            Sikkerlogg.error(it) { "Kunne ikke hente innsendte meldekort fra url: $urlString for request $request" }
            throw it
        }
    }
}
