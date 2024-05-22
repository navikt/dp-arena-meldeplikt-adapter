package no.nav.dagpenger.arenameldepliktadapter.api

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Routing.internalApi() {
    route("/internal") {
        get("/isalive") {
            call.respondText("Alive")
        }

        get("/isready") {
            call.respondText("Ready")
        }
    }
}
