package no.nav.dagpenger.arenameldepliktadapter

import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.basic
import io.ktor.server.routing.routing
import no.nav.dagpenger.arenameldepliktadapter.api.internalApi
import no.nav.dagpenger.arenameldepliktadapter.api.meldekortApi
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultHttpClient
import no.nav.dagpenger.arenameldepliktadapter.utils.isCurrentlyRunningOnNais
import no.nav.security.token.support.v2.tokenValidationSupport

fun Application.main(httpClient: HttpClient = defaultHttpClient()) {
    val config = this.environment.config

    install(Authentication) {
        if (isCurrentlyRunningOnNais()) {
            tokenValidationSupport(config = config)
        } else {
            basic {
                skipWhen { true }
            }
        }
    }

    routing {
        internalApi()
        meldekortApi(httpClient)
    }
}
