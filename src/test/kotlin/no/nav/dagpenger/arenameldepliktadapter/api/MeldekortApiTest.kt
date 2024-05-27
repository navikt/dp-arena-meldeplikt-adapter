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
import no.nav.dagpenger.arenameldepliktadapter.models.Aktivitet
import no.nav.dagpenger.arenameldepliktadapter.models.Dag
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.models.RapporteringsperiodeStatus
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
    fun testRapporteringsperioderUtenToken() = setUpTestApplication {
        val response = client.get("/rapporteringsperioder") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testRapporteringsperiode() = setUpTestApplication {
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

        val response = client.get("/rapporteringsperioder") {
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
        assertEquals(0, rapporteringsperioder[0].dager[0].dagIndex)
        assertEquals(LocalDate.parse("2024-04-08"), rapporteringsperioder[0].dager[0].dato)
        assertEquals(13, rapporteringsperioder[0].dager[13].dagIndex)
        assertEquals(LocalDate.parse("2024-04-21"), rapporteringsperioder[0].dager[13].dato)
        assertEquals(LocalDate.parse("2024-04-20"), rapporteringsperioder[0].kanSendesFra)
        assertEquals(true, rapporteringsperioder[0].kanSendes)
        assertEquals(true, rapporteringsperioder[0].kanKorrigeres)
        assertEquals(null, rapporteringsperioder[0].bruttoBelop)
        assertEquals(RapporteringsperiodeStatus.TilUtfylling, rapporteringsperioder[0].status)
    }

    @Test
    fun testSendteRapporteringsperioderUtenToken() = setUpTestApplication {
        val response = client.get("/sendterapporteringsperioder") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testSendteRapporteringsperioder() = setUpTestApplication {
        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/historiskemeldekort") {
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

        val response = client.get("/sendterapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val rapporteringsperioder = defaultObjectMapper.readValue<List<Rapporteringsperiode>>(response.bodyAsText())
        assertEquals(2, rapporteringsperioder.size)
        assertEquals(1234567890, rapporteringsperioder[0].id)
        assertEquals(LocalDate.parse("2024-04-08"), rapporteringsperioder[0].periode.fraOgMed)
        assertEquals(LocalDate.parse("2024-04-21"), rapporteringsperioder[0].periode.tilOgMed)
        assertEquals(14, rapporteringsperioder[0].dager.size)
        assertEquals(0, rapporteringsperioder[0].dager[0].dagIndex)
        assertEquals(LocalDate.parse("2024-04-08"), rapporteringsperioder[0].dager[0].dato)
        assertEquals(13, rapporteringsperioder[0].dager[13].dagIndex)
        assertEquals(LocalDate.parse("2024-04-21"), rapporteringsperioder[0].dager[13].dato)
        assertEquals(LocalDate.parse("2024-04-20"), rapporteringsperioder[0].kanSendesFra)
        assertEquals(false, rapporteringsperioder[0].kanSendes)
        assertEquals(true, rapporteringsperioder[0].kanKorrigeres)
        assertEquals("0.0", rapporteringsperioder[0].bruttoBelop)
        assertEquals(RapporteringsperiodeStatus.Innsendt, rapporteringsperioder[0].status)

        assertEquals(RapporteringsperiodeStatus.Ferdig, rapporteringsperioder[1].status)
    }

    @Test
    fun testAktivitetsdagerUtenToken() = setUpTestApplication {
        val response = client.get("/aktivitetsdager/1234567890") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testAktivitetsdager() = setUpTestApplication {
        val meldekortserviceResponse = """
            {
                "id": "",
                "personId": "",
                "fodselsnr": "",
                "meldekortId": "",
                "meldeperiode": "202421",
                "meldegruppe": "",
                "arkivnokkel": "",
                "kortType": "",
                "sporsmal": {
                    "meldekortDager": [
                        {
                            "dag": 2,
                            "arbeidetTimerSum": 7.5,
                            "syk": false,
                            "annetFravaer": false,
                            "kurs": false
                        },
                        {
                            "dag": 3,
                            "arbeidetTimerSum": 0,
                            "syk": true,
                            "annetFravaer": false,
                            "kurs": false
                        },
                        {
                            "dag": 4,
                            "arbeidetTimerSum": 0,
                            "syk": false,
                            "annetFravaer": true,
                            "kurs": false
                        },
                        {
                            "dag": 5,
                            "arbeidetTimerSum": 0,
                            "syk": false,
                            "annetFravaer": false,
                            "kurs": true
                        },
                        {
                            "dag": 6,
                            "arbeidetTimerSum": 8,
                            "syk": true,
                            "annetFravaer": true,
                            "kurs": true
                        },
                        {
                            "dag": 14,
                            "arbeidetTimerSum": 0,
                            "syk": false,
                            "annetFravaer": false,
                            "kurs": false
                        }
                    ]
                }
            }
        """.trimIndent()

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldekortdetaljer") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(meldekortserviceResponse)
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

        val response = client.get("/aktivitetsdager/1234567890") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        val aktivitetsdager = defaultObjectMapper.readValue<List<Dag>>(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(14, aktivitetsdager.size)

        assertEquals(LocalDate.parse("2024-05-20"), aktivitetsdager[0].dato)
        assertEquals(0, aktivitetsdager[0].aktiviteter.size)

        assertEquals(LocalDate.parse("2024-05-21"), aktivitetsdager[1].dato)
        assertEquals(1, aktivitetsdager[1].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.Arbeid, aktivitetsdager[1].aktiviteter[0].type)
        assertEquals("7.5", aktivitetsdager[1].aktiviteter[0].timer)

        assertEquals(LocalDate.parse("2024-05-22"), aktivitetsdager[2].dato)
        assertEquals(1, aktivitetsdager[2].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.Syk, aktivitetsdager[2].aktiviteter[0].type)
        assertEquals(null, aktivitetsdager[2].aktiviteter[0].timer)

        assertEquals(LocalDate.parse("2024-05-23"), aktivitetsdager[3].dato)
        assertEquals(1, aktivitetsdager[3].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.FerieEllerFravaer, aktivitetsdager[3].aktiviteter[0].type)
        assertEquals(null, aktivitetsdager[3].aktiviteter[0].timer)

        assertEquals(LocalDate.parse("2024-05-24"), aktivitetsdager[4].dato)
        assertEquals(1, aktivitetsdager[4].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.Utdanning, aktivitetsdager[4].aktiviteter[0].type)
        assertEquals(null, aktivitetsdager[4].aktiviteter[0].timer)

        assertEquals(LocalDate.parse("2024-05-25"), aktivitetsdager[5].dato)
        assertEquals(4, aktivitetsdager[5].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.Arbeid, aktivitetsdager[5].aktiviteter[0].type)
        assertEquals("8.0", aktivitetsdager[5].aktiviteter[0].timer)
        assertEquals(Aktivitet.AktivitetsType.Syk, aktivitetsdager[5].aktiviteter[1].type)
        assertEquals(null, aktivitetsdager[5].aktiviteter[1].timer)
        assertEquals(Aktivitet.AktivitetsType.Utdanning, aktivitetsdager[5].aktiviteter[2].type)
        assertEquals(null, aktivitetsdager[5].aktiviteter[2].timer)
        assertEquals(Aktivitet.AktivitetsType.FerieEllerFravaer, aktivitetsdager[5].aktiviteter[3].type)
        assertEquals(null, aktivitetsdager[5].aktiviteter[3].timer)

        assertEquals(LocalDate.parse("2024-06-02"), aktivitetsdager[13].dato)
        assertEquals(0, aktivitetsdager[13].aktiviteter.size)
    }

    @Test
    fun testKorrigertMeldekortUtenToken() = setUpTestApplication {
        val response = client.get("/korrigertMeldekort/1234567890") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testKorrigertMeldekort() = setUpTestApplication {
        val meldekortserviceResponse = "Svar fra Meldekortservice"

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/korrigertMeldekort") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(meldekortserviceResponse)
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

        val response = client.get("/korrigertMeldekort/1234567890") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(meldekortserviceResponse, response.bodyAsText())
    }
}
