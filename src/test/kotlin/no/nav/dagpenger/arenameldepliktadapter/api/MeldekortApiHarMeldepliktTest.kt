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

class MeldekortApiHarMeldepliktTest : TestBase() {
    private val ident = "01020312345"

    @Test
    fun `harmeldeplikt uten token skal gi Unauthorized`() = setUpTestApplication {
        val response = client.get("/harmeldeplikt") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `harmeldeplikt som får true i meldepliktListe skal returnere true`() = setUpTestApplication {
        val meldestatus = MeldestatusResponse(
            arenaPersonId = 1L,
            personIdent = ident,
            formidlingsgruppe = "DAGP",
            harMeldtSeg = true,
            meldepliktListe = listOf(
                MeldestatusResponse.Meldeplikt(
                    true,
                    MeldestatusResponse.Periode(
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(10)
                    ),
                    "Iverksatt vedtak"
                )
            ),
            meldegruppeListe = emptyList()
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

        val response = client.get("/harmeldeplikt") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("true", response.bodyAsText())
    }

    @Test
    fun `harmeldeplikt som får false i meldepliktListe skal returnere false`() = setUpTestApplication {
        val meldestatus = MeldestatusResponse(
            arenaPersonId = 1L,
            personIdent = ident,
            formidlingsgruppe = "DAGP",
            harMeldtSeg = true,
            meldepliktListe = listOf(
                MeldestatusResponse.Meldeplikt(
                    false,
                    MeldestatusResponse.Periode(
                        LocalDateTime.now(),
                    ),
                    "Iverksatt vedtak"
                )
            ),
            meldegruppeListe = emptyList()
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

        val response = client.get("/harmeldeplikt") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("false", response.bodyAsText())
    }
}
