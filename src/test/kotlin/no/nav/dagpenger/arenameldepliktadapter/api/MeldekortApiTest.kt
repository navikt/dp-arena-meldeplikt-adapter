package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.dagpenger.arenameldepliktadapter.models.Aktivitet
import no.nav.dagpenger.arenameldepliktadapter.models.InnsendingResponse
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollResponse
import no.nav.dagpenger.arenameldepliktadapter.models.Periode
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.models.RapporteringsperiodeStatus
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
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

    private val meldekortdetaljer = """
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

    @Test
    fun testRapporteringsperioderUtenToken() = setUpTestApplication {
        val response = client.get("/rapporteringsperioder") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testRapporteringsperioderUtenMeldeplikt() = setUpTestApplication {
        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldekort") {
                        call.response.status(HttpStatusCode.NoContent)
                    }
                }
            }
        }

        val token = issueToken("01020312345")

        val response = client.get("/rapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun testRapporteringsperioder() = setUpTestApplication {
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

        val token = issueToken("01020312345")

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
        assertEquals(RapporteringsperiodeStatus.TilUtfylling, rapporteringsperioder[0].status)
        assertEquals(null, rapporteringsperioder[0].bruttoBelop)
        assertEquals(null, rapporteringsperioder[0].registrertArbeidssoker)
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
                    get("/v2/meldekortdetaljer") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(meldekortdetaljer)
                    }
                }
            }
        }

        val token = issueToken("01020312345")

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
        assertEquals(RapporteringsperiodeStatus.Innsendt, rapporteringsperioder[0].status)
        assertEquals(0.0, rapporteringsperioder[0].bruttoBelop)
        assertEquals(null, rapporteringsperioder[0].registrertArbeidssoker)

        val aktivitetsdager = rapporteringsperioder[0].dager
        assertEquals(14, aktivitetsdager.size)

        assertEquals(0, aktivitetsdager[0].dagIndex)
        assertEquals(13, aktivitetsdager[13].dagIndex)

        assertEquals(LocalDate.parse("2024-04-08"), aktivitetsdager[0].dato)
        assertEquals(0, aktivitetsdager[0].aktiviteter.size)

        assertEquals(LocalDate.parse("2024-04-09"), aktivitetsdager[1].dato)
        assertEquals(1, aktivitetsdager[1].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.Arbeid, aktivitetsdager[1].aktiviteter[0].type)
        assertEquals(7.5, aktivitetsdager[1].aktiviteter[0].timer)

        assertEquals(LocalDate.parse("2024-04-10"), aktivitetsdager[2].dato)
        assertEquals(1, aktivitetsdager[2].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.Syk, aktivitetsdager[2].aktiviteter[0].type)
        assertEquals(null, aktivitetsdager[2].aktiviteter[0].timer)

        assertEquals(LocalDate.parse("2024-04-11"), aktivitetsdager[3].dato)
        assertEquals(1, aktivitetsdager[3].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.Fravaer, aktivitetsdager[3].aktiviteter[0].type)
        assertEquals(null, aktivitetsdager[3].aktiviteter[0].timer)

        assertEquals(LocalDate.parse("2024-04-12"), aktivitetsdager[4].dato)
        assertEquals(1, aktivitetsdager[4].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.Utdanning, aktivitetsdager[4].aktiviteter[0].type)
        assertEquals(null, aktivitetsdager[4].aktiviteter[0].timer)

        assertEquals(LocalDate.parse("2024-04-13"), aktivitetsdager[5].dato)
        assertEquals(4, aktivitetsdager[5].aktiviteter.size)
        assertEquals(Aktivitet.AktivitetsType.Arbeid, aktivitetsdager[5].aktiviteter[0].type)
        assertEquals(8.0, aktivitetsdager[5].aktiviteter[0].timer)
        assertEquals(Aktivitet.AktivitetsType.Syk, aktivitetsdager[5].aktiviteter[1].type)
        assertEquals(null, aktivitetsdager[5].aktiviteter[1].timer)
        assertEquals(Aktivitet.AktivitetsType.Utdanning, aktivitetsdager[5].aktiviteter[2].type)
        assertEquals(null, aktivitetsdager[5].aktiviteter[2].timer)
        assertEquals(Aktivitet.AktivitetsType.Fravaer, aktivitetsdager[5].aktiviteter[3].type)
        assertEquals(null, aktivitetsdager[5].aktiviteter[3].timer)

        assertEquals(LocalDate.parse("2024-04-21"), aktivitetsdager[13].dato)
        assertEquals(0, aktivitetsdager[13].aktiviteter.size)

        assertEquals(RapporteringsperiodeStatus.Ferdig, rapporteringsperioder[1].status)
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

        val token = issueToken("01020312345")

        val response = client.get("/korrigertMeldekort/1234567890") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(meldekortserviceResponse, response.bodyAsText())
    }

    @Test
    fun testSendInnRapporteringsperiodeUtenToken() = setUpTestApplication {
        val response = client.post("/sendinn") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testSendInnRapporteringsperiode() = setUpTestApplication {
        val id = 1234567890L
        val rapporteringsperiode = Rapporteringsperiode(
            id,
            Periode(
                LocalDate.now(),
                LocalDate.now()
            ),
            emptyList(),
            LocalDate.now(),
            true,
            true,
            RapporteringsperiodeStatus.TilUtfylling,
            0.0,
            true
        )

        val kontrollResponse = MeldekortkontrollResponse(
            id,
            "OK",
            emptyList(),
            emptyList()
        )

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldekortdetaljer") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(meldekortdetaljer)
                    }
                }
            }
            hosts("https://meldekortkontroll-api") {
                routing {
                    post("") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(kontrollResponse))
                    }
                }
            }
        }

        val token = issueToken("01020312345")

        val response = client.post("/sendinn") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(defaultObjectMapper.writeValueAsString(rapporteringsperiode))
        }

        val innsendingResponse = defaultObjectMapper.readValue<InnsendingResponse>(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(id, innsendingResponse.id)
        assertEquals("OK", innsendingResponse.status)
        assertEquals(emptyList(), innsendingResponse.feil)
    }
}
