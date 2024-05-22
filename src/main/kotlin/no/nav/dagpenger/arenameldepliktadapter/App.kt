package no.nav.dagpenger.arenameldepliktadapter

import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.dagpenger.arenameldepliktadapter.api.internalApi
import no.nav.dagpenger.arenameldepliktadapter.api.meldekortApi
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultHttpClient

fun Application.main(httpClient: HttpClient = defaultHttpClient()) {
    routing {
        internalApi()
        meldekortApi(httpClient)
    }
}
