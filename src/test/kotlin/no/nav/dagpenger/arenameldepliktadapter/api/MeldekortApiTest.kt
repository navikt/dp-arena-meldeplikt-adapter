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
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.dagpenger.arenameldepliktadapter.models.Aktivitet
import no.nav.dagpenger.arenameldepliktadapter.models.Dag
import no.nav.dagpenger.arenameldepliktadapter.models.InnsendingResponse
import no.nav.dagpenger.arenameldepliktadapter.models.KortType
import no.nav.dagpenger.arenameldepliktadapter.models.Meldegruppe
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollRequest
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollResponse
import no.nav.dagpenger.arenameldepliktadapter.models.MeldestatusRequest
import no.nav.dagpenger.arenameldepliktadapter.models.MeldestatusResponse
import no.nav.dagpenger.arenameldepliktadapter.models.Periode
import no.nav.dagpenger.arenameldepliktadapter.models.Person
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.models.RapporteringsperiodeStatus
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MeldekortApiTest : TestBase() {

    private val personId = 1234L
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

    private val meldekortdetaljer = """
            {
                "id": "",
                "personId": $personId,
                "fodselsnr": "$ident",
                "meldekortId": $meldekortId,
                "meldeperiode": "202421",
                "meldegruppe": "",
                "arkivnokkel": "",
                "kortType": "KORT_TYPE",
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
                },
                "begrunnelse": "Bla bla"
            }
        """.trimIndent()

    @Test
    fun testKasterExceptionHvisIkkeKanHenteResponse() = setUpTestApplication {
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
    fun testKasterExceptionVedUforventetHttpStatus() = setUpTestApplication {
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
    fun testHarDpMeldepliktUtenToken() = setUpTestApplication {
        val response = client.get("/hardpmeldeplikt") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testHarDpMeldepliktMedDAGP() = setUpTestApplication {
        val meldegrupper = listOf(
            Meldegruppe(
                ident,
                "ARBS",
                LocalDate.now(),
                null,
                LocalDate.now(),
                "J",
                "Aktivert med ingen ytelser",
                null
            ),
            Meldegruppe(
                ident,
                "DAGP",
                LocalDate.now(),
                LocalDate.now(),
                LocalDate.now(),
                "J",
                "Iverksatt vedtak",
                1L
            )
        )

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldegrupper") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(meldegrupper))
                    }
                }
            }
        }

        val token = issueToken(ident)

        val response = client.get("/hardpmeldeplikt") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("true", response.bodyAsText())
    }

    @Test
    fun testHarDpMeldepliktUtenDAGP() = setUpTestApplication {
        val meldegrupper = listOf(
            Meldegruppe(
                ident,
                "ARBS",
                LocalDate.now(),
                null,
                LocalDate.now(),
                "J",
                "Aktivert med ingen ytelser",
                null
            )
        )

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldegrupper") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(meldegrupper))
                    }
                }
            }
        }

        val token = issueToken(ident)

        val response = client.get("/hardpmeldeplikt") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("false", response.bodyAsText())
    }

    @Test
    fun testHarDpMeldepliktMedDAGPOgIdentIHeader() = setUpTestApplication {
        val meldegrupper = listOf(
            Meldegruppe(
                ident,
                "DAGP",
                LocalDate.now(),
                LocalDate.now(),
                LocalDate.now(),
                "J",
                "Iverksatt vedtak",
                1L
            )
        )

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldegrupper") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(meldegrupper))
                    }
                }
            }
        }

        val token = issueToken(ident)

        val response = client.get("/hardpmeldeplikt") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("ident", ident)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("true", response.bodyAsText())
    }

    @Test
    fun testHarMeldepliktUtenToken() = setUpTestApplication {
        val response = client.get("/harmeldeplikt") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testHarMeldepliktTrue() = setUpTestApplication {
        val meldegrupper = listOf(
            Meldegruppe(
                ident,
                "ARBS",
                LocalDate.now(),
                null,
                LocalDate.now(),
                "J",
                "Aktivert med ingen ytelser",
                null
            ),
        )

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldegrupper") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(meldegrupper))
                    }
                }
            }
        }

        val token = issueToken(ident)

        val response = client.get("/harmeldeplikt") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("true", response.bodyAsText())
    }

    @Test
    fun testHarMeldepliktFalse() = setUpTestApplication {
        val meldegrupper = emptyList<Meldegruppe>()

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    get("/v2/meldegrupper") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(meldegrupper))
                    }
                }
            }
        }

        val token = issueToken(ident)

        val response = client.get("/harmeldeplikt") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("false", response.bodyAsText())
    }

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

        val token = issueToken(ident)

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

        val token = issueToken(ident)

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
        assertEquals(true, rapporteringsperioder[0].kanEndres)
        assertEquals(RapporteringsperiodeStatus.TilUtfylling, rapporteringsperioder[0].status)
        assertEquals(null, rapporteringsperioder[0].bruttoBelop)
        assertEquals(null, rapporteringsperioder[0].registrertArbeidssoker)
        assertEquals(null, rapporteringsperioder[0].begrunnelseEndring)
    }

    @Test
    fun testPersonUtenToken() = setUpTestApplication {
        val response = client.get("/person") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testPerson() = setUpTestApplication {
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

        val token = issueToken(ident)

        val response = client.get("/sendterapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val rapporteringsperioder = defaultObjectMapper.readValue<List<Rapporteringsperiode>>(response.bodyAsText())
        // Må filtrere bort meldekort som ikke har meldegruppe ARBS eller DAGP
        // Hvis det finnes 2 meldekort med samme periode, må vi ta kun det siste (korrigert)
        assertEquals(4, rapporteringsperioder.size)
        assertEquals(1234567890, rapporteringsperioder[0].id)
        assertEquals(1234567892, rapporteringsperioder[1].id)
        assertEquals(1234567893, rapporteringsperioder[2].id)
        assertEquals(1234567894, rapporteringsperioder[3].id)

        assertEquals(LocalDate.parse("2024-04-08"), rapporteringsperioder[0].periode.fraOgMed)
        assertEquals(LocalDate.parse("2024-04-21"), rapporteringsperioder[0].periode.tilOgMed)
        assertEquals(14, rapporteringsperioder[0].dager.size)
        assertEquals(0, rapporteringsperioder[0].dager[0].dagIndex)
        assertEquals(LocalDate.parse("2024-04-08"), rapporteringsperioder[0].dager[0].dato)
        assertEquals(13, rapporteringsperioder[0].dager[13].dagIndex)
        assertEquals(LocalDate.parse("2024-04-21"), rapporteringsperioder[0].dager[13].dato)
        assertEquals(LocalDate.parse("2024-04-20"), rapporteringsperioder[0].kanSendesFra)
        assertEquals(false, rapporteringsperioder[0].kanSendes)
        assertEquals(true, rapporteringsperioder[0].kanEndres)
        assertEquals(RapporteringsperiodeStatus.Innsendt, rapporteringsperioder[0].status)
        assertEquals(null, rapporteringsperioder[0].mottattDato)
        assertEquals(0.0, rapporteringsperioder[0].bruttoBelop)
        assertEquals(null, rapporteringsperioder[0].registrertArbeidssoker)
        assertEquals("Bla bla", rapporteringsperioder[0].begrunnelseEndring)

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

        assertEquals(RapporteringsperiodeStatus.Endret, rapporteringsperioder[1].status)
        assertEquals("2024-05-19", rapporteringsperioder[1].mottattDato?.format(DateTimeFormatter.ISO_DATE))

        assertEquals(RapporteringsperiodeStatus.Ferdig, rapporteringsperioder[2].status)
        assertEquals("2024-05-20", rapporteringsperioder[2].mottattDato?.format(DateTimeFormatter.ISO_DATE))

        assertEquals(RapporteringsperiodeStatus.Feilet, rapporteringsperioder[3].status)
        assertEquals("2024-06-01", rapporteringsperioder[3].mottattDato?.format(DateTimeFormatter.ISO_DATE))
    }

    @Test
    fun testEndreRapporteringsperiodetUtenToken() = setUpTestApplication {
        val response = client.get("/endrerapporteringsperiode/1234567890") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testEndreRapporteringsperiode() = setUpTestApplication {
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

        val token = issueToken(ident)

        val response = client.get("/endrerapporteringsperiode/1234567890") {
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
        val dager = mutableListOf<Dag>()
        for (i in 0..13) {
            dager.add(
                Dag(
                    LocalDate.now().plusDays(i.toLong()),
                    when (i) {
                        0 -> listOf(Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Arbeid, 7.5))
                        4 -> listOf(Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Fravaer, null))
                        7 -> listOf(Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Utdanning, null))
                        11 -> listOf(Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Syk, null))
                        else -> emptyList()
                    },
                    i
                )
            )
        }

        val rapporteringsperiode = Rapporteringsperiode(
            meldekortId,
            KortType.ELEKTRONISK,
            Periode(
                LocalDate.now(),
                LocalDate.now().plusDays(14)
            ),
            dager,
            LocalDate.now(),
            true,
            true,
            RapporteringsperiodeStatus.TilUtfylling,
            null,
            0.0,
            true,
            null
        )

        val kontrollResponse = MeldekortkontrollResponse(
            meldekortId,
            "OK",
            emptyList(),
            emptyList()
        )

        var request = ""
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
                        request = call.receiveText()
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(kontrollResponse))
                    }
                }
            }
        }

        val token = issueToken(ident)

        val response = client.post("/sendinn") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(defaultObjectMapper.writeValueAsString(rapporteringsperiode))
        }

        val innsendingResponse = defaultObjectMapper.readValue<InnsendingResponse>(response.bodyAsText())

        // Sjekk response
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(meldekortId, innsendingResponse.id)
        assertEquals("OK", innsendingResponse.status)
        assertEquals(emptyList(), innsendingResponse.feil)

        // Sjekke request til Meldekortkontroll
        val meldekortkontrollRequest = defaultObjectMapper.readValue<MeldekortkontrollRequest>(request)
        assertEquals(meldekortId, meldekortkontrollRequest.meldekortId)
        assertEquals(ident, meldekortkontrollRequest.fnr)
        assertEquals(personId, meldekortkontrollRequest.personId)
        assertEquals("DP", meldekortkontrollRequest.kilde)
        assertEquals(LocalDate.now(), meldekortkontrollRequest.meldedato)
        assertEquals(rapporteringsperiode.periode.fraOgMed, meldekortkontrollRequest.periodeFra)
        assertEquals(rapporteringsperiode.periode.tilOgMed, meldekortkontrollRequest.periodeTil)
        assertEquals(true, meldekortkontrollRequest.annetFravaer)
        assertEquals(true, meldekortkontrollRequest.arbeidet)
        assertEquals(rapporteringsperiode.registrertArbeidssoker, meldekortkontrollRequest.arbeidssoker)
        assertEquals(true, meldekortkontrollRequest.kurs)
        assertEquals(true, meldekortkontrollRequest.syk)
        assertEquals(null, meldekortkontrollRequest.begrunnelse)

        assertEquals(7.5, meldekortkontrollRequest.meldekortdager[0].arbeidTimer)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[0].kurs)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[0].syk)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[0].annetFravaer)

        assertEquals(0.0, meldekortkontrollRequest.meldekortdager[4].arbeidTimer)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[4].kurs)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[4].syk)
        assertEquals(true, meldekortkontrollRequest.meldekortdager[4].annetFravaer)

        assertEquals(0.0, meldekortkontrollRequest.meldekortdager[7].arbeidTimer)
        assertEquals(true, meldekortkontrollRequest.meldekortdager[7].kurs)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[7].syk)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[7].annetFravaer)

        assertEquals(0.0, meldekortkontrollRequest.meldekortdager[11].arbeidTimer)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[11].kurs)
        assertEquals(true, meldekortkontrollRequest.meldekortdager[11].syk)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[11].annetFravaer)

        assertEquals(0.0, meldekortkontrollRequest.meldekortdager[13].arbeidTimer)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[13].kurs)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[13].syk)
        assertEquals(false, meldekortkontrollRequest.meldekortdager[13].annetFravaer)
    }

    @Test
    fun testHentMeldestatusUtenToken() = setUpTestApplication {
        val response = client.post("/meldestatus") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testHentMeldestatusMedUgildigIdent() = setUpTestApplication {
        val meldekortstatusRequest = MeldestatusRequest(
            null,
            "test",
            null,
        )

        val response = client.post("/meldestatus") {
            header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(defaultObjectMapper.writeValueAsString(meldekortstatusRequest))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testHentMeldestatusMedTommeIdentOgArenaId() = setUpTestApplication {
        val meldekortstatusRequest = MeldestatusRequest(
            null,
            null,
            null,
        )

        val response = client.post("/meldestatus") {
            header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(defaultObjectMapper.writeValueAsString(meldekortstatusRequest))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testHentMeldestatusReturnsData() = setUpTestApplication {
        val arenaPersonId = 123456789L

        val meldekortstatusRequest = MeldestatusRequest(
            arenaPersonId,
            ident,
            LocalDate.now(),
        )

        val meldestatusResponse = MeldestatusResponse(
            arenaPersonId,
            ident,
            "DAGP",
            true,
            listOf(
                MeldestatusResponse.Meldeplikt(
                    true,
                    MeldestatusResponse.Periode(
                        LocalDateTime.now().minusDays(10),
                    ),
                    "",
                    MeldestatusResponse.Endring(
                        "R123456",
                        LocalDateTime.now().minusDays(7),
                        "E654321",
                        LocalDateTime.now()
                    ),
                )
            ),
            listOf(
                MeldestatusResponse.Meldegruppe(
                    "ATTF",
                    MeldestatusResponse.Periode(
                        LocalDateTime.now().minusDays(10),
                    ),
                    "Bla bla",
                    MeldestatusResponse.Endring(
                        "R123456",
                        LocalDateTime.now().minusDays(7),
                        "E654321",
                        LocalDateTime.now()
                    ),
                )
            )
        )

        var request: MeldestatusRequest? = null
        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    post("/v2/meldestatus") {
                        request = defaultObjectMapper.readValue<MeldestatusRequest>(call.receiveText())
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(meldestatusResponse))
                    }
                }
            }
        }

        val response = client.post("/meldestatus") {
            header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(defaultObjectMapper.writeValueAsString(meldekortstatusRequest))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(meldekortstatusRequest, request)
        assertEquals(meldestatusResponse, defaultObjectMapper.readValue<MeldestatusResponse>(response.bodyAsText()))
    }
}
