package no.nav.dagpenger.arenameldepliktadapter.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.internalApi() {
    route("/internal") {
        get("/isalive") {
            call.respondText("Alive")
        }

        get("/isready") {
            call.respondText("Ready")
        }
    }
}
