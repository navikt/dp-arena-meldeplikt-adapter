package no.nav.dagpenger.arenameldepliktadapter.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.meldekortApi() {
    route("/meldekort") {
        get {
            call.respondText("Meldekort")
        }
    }
}
