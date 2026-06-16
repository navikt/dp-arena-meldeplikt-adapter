package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.models.RapporteringsperiodeStatus
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MeldekortApiRapporteringsperioderTest : TestBase() {
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
    fun `rapporteringsperioder skal gi InternalServerError hvis ikke kan hente data`() = setUpTestApplication {
        // Setter ikke ExternalServices

        val token = issueToken(ident)

        val response = client.get("/rapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `rapporteringsperioder skal gi InternalServerError hvis får uforventet status`() = setUpTestApplication {
        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldekort") {
                        call.response.status(HttpStatusCode.NotFound)
                    }
                }
            }
        }

        val token = issueToken(ident)

        val response = client.get("/rapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `rapporteringsperioder uten token skal gi Unauthorized`() = setUpTestApplication {
        val response = client.get("/rapporteringsperioder") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `rapporteringsperioder uten meldeplikt skal gi NoContent`() = setUpTestApplication {
        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldekort") {
                        call.response.status(HttpStatusCode.NoContent)
                    }
                }
            }
        }

        val token = issueToken(ident)

        val response = client.get("/rapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `rapporteringsperioder skal bruke ident fra TokenX selv om det finnes ident i header`() =
        testRapporteringsperioder {
            val token = issueToken(ident)

            client.get("/rapporteringsperioder") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, ContentType.Application.Json)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header("ident", "01020312346")
            }
        }

    @Test
    fun `rapporteringsperioder med TokenX skal fungere`() = testRapporteringsperioder {
        val token = issueToken(ident)

        client.get("/rapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }
    }

    @Test
    fun `rapporteringsperioder med Azure-token men uten ident-header skal gi BadRequest`() = setUpTestApplication {
        val token = issueAzureToken()

        val response = client.get("/rapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("ident mangler", response.bodyAsText())
    }

    @Test
    fun `rapporteringsperioder med Azure-token skal fungere`() = testRapporteringsperioder {
        val token = issueAzureToken()

        client.get("/rapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("ident", ident)
        }
    }

    private fun testRapporteringsperioder(block: suspend ApplicationTestBuilder.() -> HttpResponse) =
        setUpTestApplication {
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

            val response = block()

            assertEquals(HttpStatusCode.OK, response.status)

            val rapporteringsperioder = defaultObjectMapper.readValue<List<Rapporteringsperiode>>(response.bodyAsText())
            assertEquals(1, rapporteringsperioder.size)
            assertEquals(1234567890, rapporteringsperioder[0].id)
            assertEquals(LocalDate.parse("2024-04-08"), rapporteringsperioder[0].periode.fraOgMed)
            assertEquals(LocalDate.parse("2024-04-21"), rapporteringsperioder[0].periode.tilOgMed)
            assertEquals(14, rapporteringsperioder[0].dager.size)
            assertEquals(0, rapporteringsperioder[0].dager[0].dagIndex)
            assertEquals(LocalDate.parse("2024-04-08"), rapporteringsperioder[0].dager[0].dato)
            assertEquals(13, rapporteringsperioder[0].dager[13].dagIndex)
            assertEquals(LocalDate.parse("2024-04-21"), rapporteringsperioder[0].dager[13].dato)
            assertEquals(LocalDate.parse("2024-04-20"), rapporteringsperioder[0].kanSendesFra)
            assertEquals(true, rapporteringsperioder[0].kanSendes)
            assertEquals(true, rapporteringsperioder[0].kanEndres)
            assertEquals(RapporteringsperiodeStatus.TilUtfylling, rapporteringsperioder[0].status)
            assertEquals(null, rapporteringsperioder[0].bruttoBelop)
            assertEquals(null, rapporteringsperioder[0].registrertArbeidssoker)
            assertEquals(null, rapporteringsperioder[0].begrunnelseEndring)
        }
}
