package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MeldekortApiTest : TestBase() {

    private val personString = """
            {
                "personId": 5134902,
                "etternavn": "AGURKTID",
                "fornavn": "KONSEKVENT",
                "maalformkode": "NO",
                "meldeform": "EMELD",
                "meldekortListe": [
                    {
                        "meldekortId": 1234567890,
                        "kortType": "05",
                        "meldeperiode": "202415",
                        "fraDato": "2024-04-08",
                        "tilDato": "2024-04-21",
                        "hoyesteMeldegruppe": "ARBS",
                        "beregningstatus": "OPPRE",
                        "forskudd": false,
                        "bruttoBelop": 0.0
                    },
                    {
                        "meldekortId": 1234567891,
                        "kortType": "09",
                        "meldeperiode": "202417",
                        "fraDato": "2024-04-22",
                        "tilDato": "2024-05-05",
                        "hoyesteMeldegruppe": "AAP",
                        "beregningstatus": "OPPRE",
                        "forskudd": false,
                        "bruttoBelop": 0.0
                    },
                    {
                        "meldekortId": 1234567892,
                        "kortType": "10",
                        "meldeperiode": "202419",
                        "fraDato": "2024-05-06",
                        "tilDato": "2024-05-19",
                        "hoyesteMeldegruppe": "ARBS",
                        "beregningstatus": "FERDI",
                        "forskudd": false,
                        "bruttoBelop": 0.0
                    }
                ],
                "fravaerListe": []
                }
                """.trimIndent()

    @Test
    fun testMeldekortUtenToken() = setUpTestApplication {
        val response = client.get("/meldekort") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testMeldekort() = setUpTestApplication {
        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldekort") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(personString)
                    }
                }
            }
        }

        val ident = "01020312345"
        val token = mockOAuth2Server.issueToken(
            TOKENX_ISSUER_ID,
            "myclient",
            DefaultOAuth2TokenCallback(
                audience = listOf(REQUIRED_AUDIENCE),
                claims = mapOf("pid" to ident)
            )
        ).serialize()

        val response = client.get("/meldekort") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val rapporteringsperioder = defaultObjectMapper.readValue<List<Rapporteringsperiode>>(response.bodyAsText())
        assertEquals(1, rapporteringsperioder.size)
        assertEquals(1234567890, rapporteringsperioder[0].id)
        assertEquals(LocalDate.parse("2024-04-08"), rapporteringsperioder[0].periode.fraOgMed)
        assertEquals(LocalDate.parse("2024-04-21"), rapporteringsperioder[0].periode.tilOgMed)
        assertEquals(14, rapporteringsperioder[0].dager.size)
        assertEquals(LocalDate.parse("2024-04-08"), rapporteringsperioder[0].dager[0].dato)
        assertEquals(emptyList(), rapporteringsperioder[0].dager[0].aktiviteter)
        assertEquals(LocalDate.parse("2024-04-21"), rapporteringsperioder[0].dager[13].dato)
        assertEquals(emptyList(), rapporteringsperioder[0].dager[13].aktiviteter)
        assertEquals(LocalDate.parse("2024-04-20"), rapporteringsperioder[0].kanSendesFra)
        assertEquals(true, rapporteringsperioder[0].kanSendes)
        assertEquals(true, rapporteringsperioder[0].kanKorrigeres)
    }
}
