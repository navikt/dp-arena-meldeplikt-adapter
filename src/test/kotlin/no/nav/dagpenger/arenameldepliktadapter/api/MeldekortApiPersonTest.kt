package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.dagpenger.arenameldepliktadapter.models.Person
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class MeldekortApiPersonTest : TestBase() {
    private val ident = "01020312345"
    private val meldekortId = 1234567890L
    private val personString = """
        {
            "personId": 5134902,
            "etternavn": "TESTESSEN",
            "fornavn": "TEST",
            "maalformkode": "NO",
            "meldeform": "EMELD",
            "meldekortListe": [
                {
                    "meldekortId": $meldekortId,
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
                    "kortType": "05",
                    "meldeperiode": "202419",
                    "fraDato": "2024-05-06",
                    "tilDato": "2024-05-19",
                    "hoyesteMeldegruppe": "ARBS",
                    "beregningstatus": "OVERM",
                    "forskudd": false,
                    "mottattDato": "2024-05-19",
                    "bruttoBelop": 0.0
                },
                {
                    "meldekortId": 1234567893,
                    "kortType": "10",
                    "meldeperiode": "202419",
                    "fraDato": "2024-05-06",
                    "tilDato": "2024-05-19",
                    "hoyesteMeldegruppe": "ARBS",
                    "beregningstatus": "FERDI",
                    "forskudd": false,
                    "mottattDato": "2024-05-20",
                    "bruttoBelop": 0.0
                },
                {
                    "meldekortId": 1234567894,
                    "kortType": "05",
                    "meldeperiode": "202420",
                    "fraDato": "2024-05-20",
                    "tilDato": "2024-06-02",
                    "hoyesteMeldegruppe": "ARBS",
                    "beregningstatus": "FEIL",
                    "forskudd": false,
                    "mottattDato": "2024-06-01",
                    "bruttoBelop": 0.0
                }
            ],
            "fravaerListe": []
        }
    """.trimIndent()

    @Test
    fun `person uten token skal gi Unauthorized`() = setUpTestApplication {
        val response = client.get("/person") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `person med token skal fungere`() = setUpTestApplication {
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

        val token = issueToken(ident)

        val response = client.get("/person") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val person = defaultObjectMapper.readValue<Person>(response.bodyAsText())
        assertEquals(5134902, person.personId)
        assertEquals("TEST", person.fornavn)
        assertEquals("TESTESSEN", person.etternavn)
        assertEquals("NO", person.maalformkode)
        assertEquals("EMELD", person.meldeform)
    }
}
