package no.nav.dagpenger.arenameldepliktadapter.utils

import mu.KLogger
import mu.KMarkerFactory
import mu.KotlinLogging


// Se https://docs.nais.io/observability/logging/how-to/team-logs/ for konfigurasjon
object Sikkerlogg {
    private val sikkerLogger: KLogger = KotlinLogging.logger("team-logs-logger")
    private val sikkerMarker = KMarkerFactory.getMarker("TEAM_LOGS")

    fun debug(
        throwable: Throwable? = null,
        loggstatement: () -> Any?,
    ) {
        sikkerLogger.debug(sikkerMarker, throwable, loggstatement)
    }

    fun info(
        throwable: Throwable? = null,
        loggstatement: () -> Any?,
    ) {
        sikkerLogger.info(sikkerMarker, throwable, loggstatement)
    }

    fun warn(
        throwable: Throwable? = null,
        loggstatement: () -> Any?,
    ) {
        sikkerLogger.warn(sikkerMarker, throwable, loggstatement)
    }

    fun error(
        throwable: Throwable? = null,
        loggstatement: () -> Any?,
    ) {
        sikkerLogger.error(sikkerMarker, throwable, loggstatement)
    }
}
