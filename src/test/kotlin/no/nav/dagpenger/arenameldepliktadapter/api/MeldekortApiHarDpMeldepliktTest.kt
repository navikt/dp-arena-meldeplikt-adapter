package no.nav.dagpenger.arenameldepliktadapter.api

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.dagpenger.arenameldepliktadapter.models.MeldestatusResponse
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MeldekortApiHarDpMeldepliktTest : TestBase() {
    private val ident = "01020312345"

    @Test
    fun `hardpmeldeplikt uten token skal gi Unauthorized`() = setUpTestApplication {
        val response = client.get("/hardpmeldeplikt") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `hardpmeldeplikt som får DAGP skal returnere true`() = setUpTestApplication {
        val meldestatus = MeldestatusResponse(
            arenaPersonId = 1L,
            personIdent = ident,
            formidlingsgruppe = "DAGP",
            harMeldtSeg = true,
            meldepliktListe = emptyList(),
            meldegruppeListe = listOf(
                MeldestatusResponse.Meldegruppe(
                    "ARBS",
                    MeldestatusResponse.Periode(
                        LocalDateTime.now().minusDays(15),
                        LocalDateTime.now().minusDays(1)
                    ),
                    "Iverksatt vedtak"
                ),
                MeldestatusResponse.Meldegruppe(
                    "DAGP",
                    MeldestatusResponse.Periode(
                        LocalDateTime.now()
                    ),
                    "Iverksatt vedtak"
                )
            )
        )

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    post("/v2/meldestatus") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(meldestatus))
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
    fun `hardpmeldeplikt som ikke får DAGP skal returnere false`() = setUpTestApplication {
        val meldestatus = MeldestatusResponse(
            arenaPersonId = 1L,
            personIdent = ident,
            formidlingsgruppe = "DAGP",
            harMeldtSeg = true,
            meldepliktListe = emptyList(),
            meldegruppeListe = listOf(
                MeldestatusResponse.Meldegruppe(
                    "ARBS",
                    MeldestatusResponse.Periode(
                        LocalDateTime.now().minusDays(15),
                    ),
                    "Iverksatt vedtak"
                )
            )
        )

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    post("/v2/meldestatus") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(meldestatus))
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
    fun `hardpmeldeplikt som får DAGP skal returnere true med Azure-token`() = setUpTestApplication {
        val meldestatus = MeldestatusResponse(
            arenaPersonId = 1L,
            personIdent = ident,
            formidlingsgruppe = "DAGP",
            harMeldtSeg = true,
            meldepliktListe = emptyList(),
            meldegruppeListe = listOf(
                MeldestatusResponse.Meldegruppe(
                    "DAGP",
                    MeldestatusResponse.Periode(
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(10)
                    ),
                    "Iverksatt vedtak"
                )
            )
        )

        externalServices {
            hosts("https://meldekortservice") {
                routing {
                    post("/v2/meldestatus") {
                        call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        call.respond(defaultObjectMapper.writeValueAsString(meldestatus))
                    }
                }
            }
        }

        val token = issueAzureToken()

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
    fun `hardpmeldeplikt med Azure-token men uten ident-header skal gi BadRequest`() = setUpTestApplication {
        val token = issueAzureToken()

        val response = client.get("/hardpmeldeplikt") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("ident mangler", response.bodyAsText())
    }
}
