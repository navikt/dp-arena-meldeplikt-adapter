package no.nav.dagpenger.arenameldepliktadapter.api

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Routing.internalApi(prometheusMeterRegistry: PrometheusMeterRegistry) {
    route("/internal") {
        get("/isalive") {
            call.respondText("Alive")
        }

        get("/isready") {
            call.respondText("Ready")
        }

        get("/metrics") {
            call.respondText(prometheusMeterRegistry.scrape())
        }
    }
}
