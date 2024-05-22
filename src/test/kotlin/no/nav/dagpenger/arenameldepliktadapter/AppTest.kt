/*
 * This source file was generated by the Gradle 'init' task
 */
package no.nav.dagpenger.arenameldepliktadapter

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AppTest {

    companion object {

        const val TOKENX_ISSUER_ID = "tokenx"
        const val AZUREAD_ISSUER_ID = "azureAd"
        const val REQUIRED_AUDIENCE = "default"

        val personString = """
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

        var mockOAuth2Server = MockOAuth2Server()

        @BeforeAll
        @JvmStatic
        fun setup() {
            mockOAuth2Server = MockOAuth2Server()
            mockOAuth2Server.start(8091)
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            mockOAuth2Server.shutdown()
        }
    }

    @Test
    fun testInternalApi() = testApplication {
        environment {
            config = setOidcConfig()
        }
        val testHttpClient = createClient {

        }
        application {
            main(testHttpClient)
        }

        var response = testHttpClient.get("/internal/isalive")
        assertEquals(HttpStatusCode.OK, response.status)

        response = testHttpClient.get("/internal/isready")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testMeldekort() = testApplication {
        environment {
            config = setOidcConfig()
        }
        val testHttpClient = createClient {

        }
        application {
            main(testHttpClient)
        }
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
            AZUREAD_ISSUER_ID,
            "myclient",
            DefaultOAuth2TokenCallback(
                audience = listOf(REQUIRED_AUDIENCE)
            )
        ).serialize()

        val response = testHttpClient.get("/meldekort/$ident") {
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
        assertEquals(LocalDate.parse("2024-04-20"), rapporteringsperioder[0].kanSendesFra)
        assertEquals(true, rapporteringsperioder[0].kanSendes)
        assertEquals(true, rapporteringsperioder[0].kanKorrigeres)
    }

    private fun setOidcConfig(): MapApplicationConfig {
        System.setProperty("MELDEKORTSERVICE_URL", "https://meldekortservice")
        System.setProperty("AZURE_APP_CLIENT_ID", AZUREAD_ISSUER_ID)
        System.setProperty("AZURE_APP_CLIENT_SECRET", "SECRET")
        System.setProperty("AZURE_APP_JWK", "")
        System.setProperty(
            "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT",
            mockOAuth2Server.tokenEndpointUrl(AZUREAD_ISSUER_ID).toString()
        )
        System.setProperty("AZURE_APP_WELL_KNOWN_URL", mockOAuth2Server.wellKnownUrl(AZUREAD_ISSUER_ID).toString())

        return MapApplicationConfig(
            "no.nav.security.jwt.issuers.size" to "2",
            "no.nav.security.jwt.issuers.0.issuer_name" to TOKENX_ISSUER_ID,
            "no.nav.security.jwt.issuers.0.discoveryurl" to mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID).toString(),
            "no.nav.security.jwt.issuers.0.accepted_audience" to REQUIRED_AUDIENCE,
            "no.nav.security.jwt.issuers.1.issuer_name" to AZUREAD_ISSUER_ID,
            "no.nav.security.jwt.issuers.1.discoveryurl" to mockOAuth2Server.wellKnownUrl(AZUREAD_ISSUER_ID).toString(),
            "no.nav.security.jwt.issuers.1.accepted_audience" to REQUIRED_AUDIENCE,
            "ktor.environment" to "local"
        )
    }
}
