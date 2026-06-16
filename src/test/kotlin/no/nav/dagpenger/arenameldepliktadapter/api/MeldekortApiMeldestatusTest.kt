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
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.dagpenger.arenameldepliktadapter.models.MeldestatusRequest
import no.nav.dagpenger.arenameldepliktadapter.models.MeldestatusResponse
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MeldekortApiMeldestatusTest : TestBase() {
    private val ident = "01020312345"

    @Test
    fun `meldestatus uten token skal gi Unauthorized`() = setUpTestApplication {
        val response = client.post("/meldestatus") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `meldestatus med ugyldig ident skal gi BadRequest`() = setUpTestApplication {
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
    fun `meldestatus som får NoContent skal gi NoContent`() = setUpTestApplication {
        val meldekortstatusRequest = MeldestatusRequest(
            personident = ident,
        )

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    post("/v2/meldestatus") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(HttpStatusCode.NoContent)
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

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `meldestatus med tomme ident og arenaId skal gi BadRequest`() = setUpTestApplication {
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
    fun `meldestatus med riktig request skal fungere`() = setUpTestApplication {
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
