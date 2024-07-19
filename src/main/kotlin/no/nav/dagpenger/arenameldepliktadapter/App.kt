package no.nav.dagpenger.arenameldepliktadapter

import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.basic
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.dagpenger.arenameldepliktadapter.api.internalApi
import no.nav.dagpenger.arenameldepliktadapter.api.meldekortApi
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultHttpClient
import no.nav.dagpenger.arenameldepliktadapter.utils.isCurrentlyRunningLocally
import no.nav.security.token.support.v2.tokenValidationSupport

fun Application.main(httpClient: HttpClient = defaultHttpClient()) {
    val config = this.environment.config

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics()
        )
    }

    install(Authentication) {
        if (isCurrentlyRunningLocally()) {
            basic {
                skipWhen { true }
            }
        } else {
            tokenValidationSupport(config = config)
        }
    }

    // Prøvde ContentNegotiation (se commit dd9f94b), men synes det er bedre uten den

    routing {
        internalApi(appMicrometerRegistry)
        meldekortApi(httpClient)
    }
}
