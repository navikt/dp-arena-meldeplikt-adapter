package no.nav.dagpenger.arenameldepliktadapter

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockkStatic
import no.nav.dagpenger.arenameldepliktadapter.utils.isCurrentlyRunningOnNais
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

open class TestBase {

    companion object {

        const val TOKENX_ISSUER_ID = "tokenx"
        const val AZUREAD_ISSUER_ID = "azureAd"
        const val REQUIRED_AUDIENCE = "default"

        var mockOAuth2Server = MockOAuth2Server()

        @BeforeAll
        @JvmStatic
        fun setup() {
            mockkStatic(::isCurrentlyRunningOnNais)
            every { isCurrentlyRunningOnNais() } returns true

            mockOAuth2Server = MockOAuth2Server()
            mockOAuth2Server.start(8091)
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            mockOAuth2Server.shutdown()
        }
    }

    fun setUpTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            environment {
                config = setOidcConfig()
            }
            val testHttpClient = client
            application {
                main(testHttpClient)
            }

            block()
        }
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