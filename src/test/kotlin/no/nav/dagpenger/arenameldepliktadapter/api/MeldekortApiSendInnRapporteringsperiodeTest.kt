package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.module.kotlin.readValue
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
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollRequest
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollResponse
import no.nav.dagpenger.arenameldepliktadapter.models.Periode
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.models.RapporteringsperiodeStatus
import no.nav.dagpenger.arenameldepliktadapter.utils.UUIDv7
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MeldekortApiSendInnRapporteringsperiodeTest : TestBase() {
    private val personId = 1234L
    private val ident = "01020312345"
    private val meldekortId = 1234567890L
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
    fun `sendinn uten token skal gi Unauthorized`() = setUpTestApplication {
        val response = client.post("/sendinn") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `sendinn med token skal fungere`() = setUpTestApplication {
        val dager = mutableListOf<Dag>()
        for (i in 0..13) {
            dager.add(
                Dag(
                    LocalDate.now().plusDays(i.toLong()),
                    when (i) {
                        0 -> listOf(Aktivitet(UUIDv7.newUuid(), Aktivitet.AktivitetsType.Arbeid, 7.5))
                        4 -> listOf(Aktivitet(UUIDv7.newUuid(), Aktivitet.AktivitetsType.Fravaer, null))
                        7 -> listOf(Aktivitet(UUIDv7.newUuid(), Aktivitet.AktivitetsType.Utdanning, null))
                        11 -> listOf(Aktivitet(UUIDv7.newUuid(), Aktivitet.AktivitetsType.Syk, null))
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
}
