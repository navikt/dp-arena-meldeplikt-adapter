package no.nav.dagpenger.arenameldepliktadapter

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import no.nav.dagpenger.arenameldepliktadapter.api.internalApi
import no.nav.dagpenger.arenameldepliktadapter.api.meldekortApi

fun main() {
    embeddedServer(
        Netty,
        port = 8080, // This is the port on which Ktor is listening
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(Routing) {
        internalApi()
        meldekortApi()
    }
}
