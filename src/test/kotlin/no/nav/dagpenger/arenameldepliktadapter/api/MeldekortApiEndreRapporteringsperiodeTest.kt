package no.nav.dagpenger.arenameldepliktadapter.api

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
import kotlin.test.Test
import kotlin.test.assertEquals

class MeldekortApiEndreRapporteringsperiodeTest : TestBase() {
    private val ident = "01020312345"

    @Test
    fun `endrerapporteringsperiode uten token skal gi Unauthorized`() = setUpTestApplication {
        val response = client.get("/endrerapporteringsperiode/1234567890") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `endrerapporteringsperiode med token skal fungere`() = setUpTestApplication {
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
}
