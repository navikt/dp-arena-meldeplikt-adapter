package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.arenameldepliktadapter.ArenaProxyClient
import no.nav.dagpenger.arenameldepliktadapter.models.Aktivitet
import no.nav.dagpenger.arenameldepliktadapter.models.ArenaMeldekort
import no.nav.dagpenger.arenameldepliktadapter.models.Dag
import no.nav.dagpenger.arenameldepliktadapter.models.DatadelingRequest
import no.nav.dagpenger.arenameldepliktadapter.models.InnsendingFeil
import no.nav.dagpenger.arenameldepliktadapter.models.InnsendingResponse
import no.nav.dagpenger.arenameldepliktadapter.models.KortType
import no.nav.dagpenger.arenameldepliktadapter.models.Meldekort
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortBeregningstatus
import no.nav.dagpenger.arenameldepliktadapter.models.Meldekortdetaljer
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollFravaer
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollRequest
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollResponse
import no.nav.dagpenger.arenameldepliktadapter.models.MeldestatusRequest
import no.nav.dagpenger.arenameldepliktadapter.models.MeldestatusResponse
import no.nav.dagpenger.arenameldepliktadapter.models.Periode
import no.nav.dagpenger.arenameldepliktadapter.models.Person
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.models.RapporteringsperiodeStatus
import no.nav.dagpenger.arenameldepliktadapter.utils.UUIDv7
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import no.nav.dagpenger.arenameldepliktadapter.utils.getEnv
import no.nav.dagpenger.arenameldepliktadapter.utils.hentAzureToken
import no.nav.dagpenger.arenameldepliktadapter.utils.hentIdentFraToken
import no.nav.dagpenger.arenameldepliktadapter.utils.hentTokenX
import no.nav.dagpenger.arenameldepliktadapter.utils.isAzureToken
import no.nav.dagpenger.arenameldepliktadapter.utils.tokenXExchanger
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}
private const val MAX_THREAD_QTY = 20

fun Routing.meldekortApi(httpClient: HttpClient) {
    val arenaProxyClient = ArenaProxyClient(httpClient)
    val meldekortserviceAudience =
        getEnv("MELDEKORTSERVICE_AUDIENCE") ?: throw IllegalStateException("MELDEKORTSERVICE_AUDIENCE er ikke satt")
    val azureScope = "api://${meldekortserviceAudience.replace(":", ".")}/.default"

    authenticate {
        route("/hardpmeldeplikt") {
            get {
                try {
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val identFromHeader = call.request.headers["ident"]
                    var ident: String
                    var token: String

                    if (isAzureToken(authString)) {
                        if (identFromHeader.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, "ident mangler")
                            return@get
                        }

                        ident = identFromHeader
                        token = hentAzureToken(azureScope)
                    } else {
                        val pair = hentTokenX(call.request.headers["Authorization"])
                        ident = pair.first
                        token = pair.second
                    }

                    val request = MeldestatusRequest(
                        personident = ident
                    )

                    val response = sendHttpRequestWithRetry(
                        sendPostRequestTilMeldekortservice(
                            httpClient,
                            token,
                            callId,
                            "/v2/meldestatus",
                            defaultObjectMapper.writeValueAsString(request)
                        )
                    )

                    val meldestatus = defaultObjectMapper.readValue<MeldestatusResponse>(response.bodyAsText())
                    val meldegruppeListe =
                        meldestatus.meldegruppeListe?.sortedBy { it.meldegruppeperiode?.fom } ?: emptyList()

                    var harMeldeplikt = "false"
                    val now = LocalDateTime.now()

                    meldegruppeListe.forEach {
                        if (it.meldegruppe == "DAGP"
                            && it.meldegruppeperiode != null
                            && (it.meldegruppeperiode.fom.isBefore(now) || it.meldegruppeperiode.fom.isEqual(now))
                            && (
                                    it.meldegruppeperiode.tom == null
                                            || it.meldegruppeperiode.tom.isAfter(now)
                                            || it.meldegruppeperiode.tom.isEqual(now)
                                    )
                        ) {
                            harMeldeplikt = "true"
                        }
                    }

                    call.respondText(harMeldeplikt)
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved henting av meldegrupper" }
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/harmeldeplikt") {
            get {
                try {
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val (ident, token) = hentTokenX(authString)

                    val request = MeldestatusRequest(
                        personident = ident
                    )

                    val response = sendHttpRequestWithRetry(
                        sendPostRequestTilMeldekortservice(
                            httpClient,
                            token,
                            callId,
                            "/v2/meldestatus",
                            defaultObjectMapper.writeValueAsString(request)
                        )
                    )

                    val meldestatus = defaultObjectMapper.readValue<MeldestatusResponse>(response.bodyAsText())
                    val meldepliktListe =
                        meldestatus.meldepliktListe?.sortedBy { it.meldepliktperiode?.fom } ?: emptyList()

                    var harMeldeplikt = "false"
                    val now = LocalDateTime.now()

                    meldepliktListe.forEach {
                        if (it.meldeplikt
                            && it.meldepliktperiode != null
                            && (it.meldepliktperiode.fom.isBefore(now) || it.meldepliktperiode.fom.isEqual(now))
                            && (
                                    it.meldepliktperiode.tom == null
                                            || it.meldepliktperiode.tom.isAfter(now)
                                            || it.meldepliktperiode.tom.isEqual(now)
                                    )
                        ) {
                            harMeldeplikt = "true"
                        }
                    }

                    call.respondText(harMeldeplikt)
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved henting av meldegrupper" }
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/rapporteringsperioder") {
            get {
                try {
                    logger.info { "Henter rapporteringsperioder" }
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)
                    val ident = call.request.headers["ident"]

                    if (isAzureToken(authString) && ident.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "ident mangler")
                        return@get
                    }

                    val response = hentRapporteringsperioder(ident, httpClient, callId, azureScope, authString)

                    if (response.status == HttpStatusCode.NoContent) {
                        call.response.status(HttpStatusCode.NoContent)
                        return@get
                    }

                    val person = defaultObjectMapper.readValue<Person>(response.bodyAsText())

                    val rapporteringsperioder = person.meldekortListe?.filter { meldekort ->
                        // Vi tar ikke bare DAGP meldekort her, men også ARBS fordi det er naturlig å forutsette at hvis bruker har DP nå, tilhører tidligere ARBS meldekort DP
                        meldekort.hoyesteMeldegruppe in arrayOf("ARBS", "DAGP")
                                && meldekort.beregningstatus in arrayOf("OPPRE", "SENDT")
                    }?.map { meldekort ->
                        val kanSendesFra = meldekort.tilDato.minusDays(1)

                        Rapporteringsperiode(
                            meldekort.meldekortId,
                            meldekort.kortType,
                            Periode(
                                meldekort.fraDato,
                                meldekort.tilDato
                            ),
                            List(14) { index ->
                                Dag(
                                    meldekort.fraDato.plusDays(index.toLong()),
                                    mutableListOf(),
                                    index
                                )
                            },
                            kanSendesFra,
                            !LocalDate.now().isBefore(kanSendesFra),
                            kanEndres(meldekort, person.meldekortListe),
                            RapporteringsperiodeStatus.TilUtfylling
                        )
                    } ?: emptyList()

                    call.respondText(
                        defaultObjectMapper.writeValueAsString(rapporteringsperioder),
                        ContentType.Application.Json
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved henting av rapporteringsperioder" }
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/person") {
            get {
                try {
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val response = sendHttpRequestWithRetry(
                        sendGetRequestTilMeldekortservice(httpClient, authString, callId, "/v2/meldekort")
                    )

                    if (response.status == HttpStatusCode.NoContent) {
                        call.response.status(HttpStatusCode.NoContent)
                        return@get
                    }

                    call.respondText(
                        response.bodyAsText(),
                        ContentType.Application.Json
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved henting av person" }
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/sendterapporteringsperioder") {
            get {
                try {
                    logger.info { "Henter innsendte rapporteringsperioder" }
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    call.response.header(
                        "Warning",
                        "299 dp-arena-meldeplikt-adapter " +
                                "\"Dette endepunktet henter innsendte rapporteringsperioder på en lite effektiv måte, og bør ikke brukes. Bruk heller /innsendte-rapporteringsperioder\"",
                    )

                    val ident = call.request.headers["ident"]
                    val antallMeldeperioder = call.request.queryParameters["antallMeldeperioder"]?.toIntOrNull() ?: 10

                    if (isAzureToken(authString) && ident.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "ident mangler")
                        return@get
                    }

                    val response =
                        hentHistoriskeMeldekort(ident, httpClient, callId, azureScope, authString, antallMeldeperioder)

                    if (response.status == HttpStatusCode.NoContent) {
                        call.response.status(HttpStatusCode.NoContent)
                        return@get
                    }

                    val person = defaultObjectMapper.readValue<Person>(response.bodyAsText())

                    // Vi tar ikke bare DAGP meldekort her, men også ARBS fordi det er naturlig å forutsette at hvis bruker har DP nå, tilhører tidligere ARBS meldekort DP
                    val meldekortListe = person.meldekortListe?.filter { meldekort ->
                        meldekort.hoyesteMeldegruppe in arrayOf(
                            "ARBS",
                            "DAGP"
                        )
                    }

                    var rapporteringsperioder: List<Rapporteringsperiode> = emptyList()
                    val threadsQty = if (antallMeldeperioder > MAX_THREAD_QTY) MAX_THREAD_QTY else antallMeldeperioder

                    val limitedDispatcher = Dispatchers.IO.limitedParallelism(threadsQty)
                    withContext(limitedDispatcher) {
                        if (meldekortListe == null) return@withContext

                        rapporteringsperioder = meldekortListe.map { meldekort ->
                            async {
                                val kanSendesFra = meldekort.tilDato.minusDays(1)

                                val meldekortdetaljer = hentMeldekortDetaljer(
                                    ident,
                                    httpClient,
                                    callId,
                                    azureScope,
                                    authString,
                                    meldekort.meldekortId
                                )

                                val aktivitetsdager = mapAktivitetsdager(meldekort.fraDato, meldekortdetaljer)

                                Rapporteringsperiode(
                                    meldekort.meldekortId,
                                    meldekort.kortType,
                                    Periode(
                                        meldekort.fraDato,
                                        meldekort.tilDato
                                    ),
                                    aktivitetsdager,
                                    kanSendesFra,
                                    false,
                                    kanEndres(meldekort, person.meldekortListe),
                                    when (meldekort.beregningstatus) {
                                        in arrayOf(
                                            "FERDI",
                                            "IKKE"
                                        ) -> RapporteringsperiodeStatus.Ferdig

                                        "OVERM" -> RapporteringsperiodeStatus.Endret
                                        "FEIL" -> RapporteringsperiodeStatus.Feilet
                                        else -> RapporteringsperiodeStatus.Innsendt
                                    },
                                    meldekort.mottattDato,
                                    meldekort.bruttoBelop.toDouble(),
                                    meldekortdetaljer.sporsmal?.arbeidssoker,
                                    meldekortdetaljer.begrunnelse
                                )
                            }
                        }.awaitAll()
                    }

                    call.respondText(
                        defaultObjectMapper.writeValueAsString(rapporteringsperioder),
                        ContentType.Application.Json
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved henting av innsendte rapporteringsperioder" }
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/innsendte-rapporteringsperioder") {
            post {
                try {
                    logger.info { "Henter innsendte rapporteringsperioder" }
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val requestText = call.receiveText()
                    if (requestText.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "Request body kan ikke være tom")
                        return@post
                    }

                    val request = runCatching { defaultObjectMapper.readValue<DatadelingRequest>(requestText) }
                        .getOrElse {
                            call.respond(HttpStatusCode.BadRequest, "Ugyldig request body")
                            return@post
                        }

                    if (!isAzureToken(authString) && request.personIdent != hentIdentFraToken(authString)) {
                        call.respond(HttpStatusCode.Unauthorized, "Ident i token er ikke lik ident i request")
                        return@post
                    }

                    val meldekortListe = arenaProxyClient.hentInnsendteMeldekort(request)

                    val rapporteringsperioder = meldekortListe.map { meldekort ->
                        val aktivitetsdager = mapAktivitetsdager(meldekort)

                        Rapporteringsperiode(
                            meldekort.id.toLong(),
                            KortType.valueOf(meldekort.type.value),
                            Periode(
                                meldekort.periode.fraOgMed,
                                meldekort.periode.tilOgMed,
                            ),
                            aktivitetsdager,
                            meldekort.kanSendesFra,
                            meldekort.kanSendes,
                            meldekort.kanEndres,
                            when (meldekort.beregningstatus) {
                                in arrayOf(
                                    MeldekortBeregningstatus.FERDI,
                                    MeldekortBeregningstatus.IKKE
                                ) -> RapporteringsperiodeStatus.Ferdig

                                MeldekortBeregningstatus.OVERM -> RapporteringsperiodeStatus.Endret
                                MeldekortBeregningstatus.FEIL -> RapporteringsperiodeStatus.Feilet
                                else -> RapporteringsperiodeStatus.Innsendt
                            },
                            meldekort.meldedato,
                            meldekort.belop?.toDouble(),
                            meldekort.registrertArbeidssoker,
                            meldekort.begrunnelse,
                        )
                    }

                    call.respondText(
                        defaultObjectMapper.writeValueAsString(rapporteringsperioder),
                        ContentType.Application.Json
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved henting av innsendte rapporteringsperioder" }
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/endrerapporteringsperiode/{meldekortId}") {
            get {
                try {
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val meldekortId = call.parameters["meldekortId"]
                    if (meldekortId.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "meldekortId mangler")
                        return@get
                    }

                    val response = sendHttpRequestWithRetry(
                        sendGetRequestTilMeldekortservice(
                            httpClient,
                            authString,
                            callId,
                            "/v2/korrigertMeldekort?meldekortId=$meldekortId"
                        )
                    )

                    call.respondText(response.bodyAsText())
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved henting av korrigert meldekort" }
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/sendinn") {
            post {
                try {
                    logger.info("Innsending")

                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val rapporteringsperiode = defaultObjectMapper.readValue<Rapporteringsperiode>(call.receiveText())
                    logger.info("Mottatt rapporteringsperiode (meldekort) med ID ${rapporteringsperiode.id}")

                    // Henter meldekortdetaljer og meldekortservice sjekker at ident stemmer med FNR i dette meldekortet
                    val responseDetaljer = sendHttpRequestWithRetry(
                        sendGetRequestTilMeldekortservice(
                            httpClient,
                            authString,
                            callId,
                            "/v2/meldekortdetaljer?meldekortId=${rapporteringsperiode.id}"
                        )
                    )
                    val meldekortdetaljer = defaultObjectMapper.readValue<Meldekortdetaljer>(
                        responseDetaljer.bodyAsText()
                    )
                    logger.info("Mottatt meldekortdetaljer for meldekort med ID ${rapporteringsperiode.id}")

                    // Mapper meldekortdager
                    val meldekortdager: List<MeldekortkontrollFravaer> = rapporteringsperiode.dager.map { dag ->
                        MeldekortkontrollFravaer(
                            dag.dato,
                            dag.finnesAktivitetMedType(Aktivitet.AktivitetsType.Syk),
                            dag.finnesAktivitetMedType(Aktivitet.AktivitetsType.Utdanning),
                            dag.finnesAktivitetMedType(Aktivitet.AktivitetsType.Fravaer),
                            dag.hentArbeidstimer()
                        )
                    }

                    // Oppretter MeldekortkontrollRequest
                    val meldekortkontrollRequest = MeldekortkontrollRequest(
                        meldekortId = meldekortdetaljer.meldekortId,
                        fnr = meldekortdetaljer.fodselsnr,
                        personId = meldekortdetaljer.personId,
                        kilde = "DP",
                        kortType = meldekortdetaljer.kortType,
                        meldedato = if (meldekortdetaljer.kortType == "KORRIGERT_ELEKTRONISK" && meldekortdetaljer.meldeDato != null) meldekortdetaljer.meldeDato else LocalDate.now(),
                        periodeFra = rapporteringsperiode.periode.fraOgMed,
                        periodeTil = rapporteringsperiode.periode.tilOgMed,
                        meldegruppe = meldekortdetaljer.meldegruppe,
                        annetFravaer = rapporteringsperiode.finnesDagMedAktivitetsType(Aktivitet.AktivitetsType.Fravaer),
                        arbeidet = rapporteringsperiode.finnesDagMedAktivitetsType(Aktivitet.AktivitetsType.Arbeid),
                        arbeidssoker = rapporteringsperiode.registrertArbeidssoker!!,
                        kurs = rapporteringsperiode.finnesDagMedAktivitetsType(Aktivitet.AktivitetsType.Utdanning),
                        syk = rapporteringsperiode.finnesDagMedAktivitetsType(Aktivitet.AktivitetsType.Syk),
                        begrunnelse = if (meldekortdetaljer.kortType == "KORRIGERT_ELEKTRONISK") rapporteringsperiode.begrunnelseEndring else null,
                        meldekortdager = meldekortdager
                    )

                    // Request til meldekortkontroll-api
                    val response = sendHttpRequestWithRetry(
                        sendPostRequestTilMeldekortkontroll(httpClient, authString, callId, meldekortkontrollRequest)
                    )
                    val meldekortkontrollResponse = defaultObjectMapper.readValue<MeldekortkontrollResponse>(
                        response.bodyAsText()
                    )
                    logger.info("Mottatt MeldekortkontrollResponse for meldekort med ID ${rapporteringsperiode.id}")

                    val innsendingResponse = InnsendingResponse(
                        meldekortkontrollResponse.meldekortId,
                        if (meldekortkontrollResponse.kontrollStatus in arrayOf("OK", "OKOPP")) "OK" else "FEIL",
                        meldekortkontrollResponse.feilListe.map { feil -> InnsendingFeil(feil.kode, feil.params) }
                    )

                    // Returnerer response fra meldekortkontroll-api
                    call.response.status(HttpStatusCode.OK)
                    call.respondText(
                        defaultObjectMapper.writeValueAsString(innsendingResponse),
                        ContentType.Application.Json
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved innsending" }
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/meldestatus") {
            post {
                try {
                    logger.info { "Henter meldestatus" }
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val request = call.receiveText()
                    val meldestatusRequest = defaultObjectMapper.readValue<MeldestatusRequest>(request)
                    val ident = meldestatusRequest.personident

                    if (ident != null && !ident.matches(Regex("[0-9]{11}"))) {
                        logger.error { "Person-ident må ha 11 sifre" }
                        call.respond(HttpStatusCode.BadRequest, "Person-ident må ha 11 sifre")
                        return@post
                    }

                    if (ident.isNullOrBlank() && meldestatusRequest.arenaPersonId == null) {
                        logger.error { "Minst ett av elementene arenaPersonId og personident må være utfyllt" }
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Minst ett av elementene arenaPersonId og personident må være utfyllt"
                        )
                        return@post
                    }

                    val response = sendHttpRequestWithRetry(
                        sendPostRequestTilMeldekortservice(
                            httpClient,
                            hentAzureToken(azureScope),
                            callId,
                            "/v2/meldestatus",
                            request
                        )
                    )

                    call.respondText(
                        response.bodyAsText(),
                        ContentType.Application.Json,
                        response.status
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved henting av meldestatus" }
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}

private suspend fun sendHttpRequestWithRetry(
    fn: suspend () -> HttpResponse
): HttpResponse {
    var retries = 0
    var response: HttpResponse?

    val okStatuses = arrayOf(HttpStatusCode.OK, HttpStatusCode.NoContent)

    do {
        if (retries > 0) delay(1000.milliseconds)

        response = try {
            fn.invoke()
        } catch (e: Exception) {
            logger.warn(e) { "Feil ved sending request. Forsøk ${retries + 1}" }
            null
        }

        retries++
    } while ((response == null || response.status !in okStatuses) && retries < 3)

    if (response == null) throw Exception("Kunne ikke få response etter $retries forsøk")
    if (response.status !in okStatuses) throw Exception("Uforventet HTTP status ${response.status.value} etter $retries forsøk")

    return response
}

private suspend fun hentRapporteringsperioder(
    ident: String?,
    httpClient: HttpClient,
    callId: String,
    azureScope: String,
    authString: String?
): HttpResponse = if (isAzureToken(authString)) {
    logger.info { "Henter rapporteringsperioder med Azure-token" }
    sendHttpRequestWithRetry(
        sendGetRequestTilMeldekortservice(
            httpClient,
            hentAzureToken(azureScope),
            ident,
            callId,
            "/v2/meldekort"
        )
    )
} else {
    logger.info { "Henter rapporteringsperioder med TokenX" }
    sendHttpRequestWithRetry(
        sendGetRequestTilMeldekortservice(httpClient, authString, callId, "/v2/meldekort")
    )
}

private suspend fun hentHistoriskeMeldekort(
    ident: String?,
    httpClient: HttpClient,
    callId: String,
    azureScope: String,
    authString: String?,
    antallMeldeperioder: Int
) = if (isAzureToken(authString)) {
    logger.info { "Henter historiske meldekort med Azure-token" }
    sendHttpRequestWithRetry(
        sendGetRequestTilMeldekortservice(
            httpClient,
            hentAzureToken(azureScope),
            ident,
            callId,
            "/v2/historiskemeldekort?antallMeldeperioder=$antallMeldeperioder"
        )
    )
} else {
    logger.info { "Henter historiske meldekort med TokenX" }
    sendHttpRequestWithRetry(
        sendGetRequestTilMeldekortservice(
            httpClient,
            authString,
            callId,
            "/v2/historiskemeldekort?antallMeldeperioder=$antallMeldeperioder"
        )
    )
}

private suspend fun hentMeldekortDetaljer(
    ident: String?,
    httpClient: HttpClient,
    callId: String,
    azureScope: String,
    authString: String?,
    meldekortId: Long
): Meldekortdetaljer {
    val responseDetaljer =
        if (isAzureToken(authString)) {
            sendHttpRequestWithRetry(
                sendGetRequestTilMeldekortservice(
                    httpClient,
                    hentAzureToken(azureScope),
                    ident,
                    callId,
                    "/v2/meldekortdetaljer?meldekortId=$meldekortId"
                )
            )
        } else {
            sendHttpRequestWithRetry(
                sendGetRequestTilMeldekortservice(
                    httpClient,
                    authString,
                    callId,
                    "/v2/meldekortdetaljer?meldekortId=$meldekortId"
                )
            )
        }

    return defaultObjectMapper.readValue<Meldekortdetaljer>(
        responseDetaljer.bodyAsText()
    )
}

private fun sendGetRequestTilMeldekortservice(
    httpClient: HttpClient,
    token: String,
    ident: String?,
    callId: String,
    path: String
): suspend () -> HttpResponse = {
    httpClient.get(getEnv("MELDEKORTSERVICE_URL") + path) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header(HttpHeaders.XRequestId, callId)
        if (!ident.isNullOrBlank()) header("ident", ident)
    }
}

private fun sendGetRequestTilMeldekortservice(
    httpClient: HttpClient,
    authString: String?,
    callId: String,
    path: String
): suspend () -> HttpResponse = {
    val (ident, token) = hentTokenX(authString)

    sendGetRequestTilMeldekortservice(httpClient, token, ident, callId, path)()
}

private fun sendPostRequestTilMeldekortservice(
    httpClient: HttpClient,
    token: String,
    callId: String,
    path: String,
    body: String? = null,
): suspend () -> HttpResponse = {
    httpClient.post(getEnv("MELDEKORTSERVICE_URL") + path) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        header(HttpHeaders.XRequestId, callId)
        if (body != null) setBody(body)
    }
}

private fun sendPostRequestTilMeldekortkontroll(
    httpClient: HttpClient,
    authString: String?,
    callId: String,
    meldekortkontrollRequest: MeldekortkontrollRequest
): suspend () -> HttpResponse = {
    val incomingToken = authString?.replace("Bearer ", "") ?: ""
    val tokenProvider = tokenXExchanger(incomingToken, getEnv("MELDEKORTKONTROLL_AUDIENCE") ?: "")

    httpClient.post(getEnv("MELDEKORTKONTROLL_URL")!!) {
        header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        header(HttpHeaders.XRequestId, callId)
        setBody(defaultObjectMapper.writeValueAsString(meldekortkontrollRequest))
    }
}

private fun kanEndres(meldekort: Meldekort, meldekortListe: List<Meldekort>): Boolean {
    return if (meldekort.kortType == KortType.KORRIGERT_ELEKTRONISK || meldekort.beregningstatus == "UBEHA") {
        false
    } else {
        meldekortListe.find { mk ->
            (
                    meldekort.meldekortId != mk.meldekortId
                            && meldekort.meldeperiode == mk.meldeperiode
                            && mk.kortType == KortType.KORRIGERT_ELEKTRONISK
                    )
        } == null
    }
}

private fun mapAktivitetsdager(fom: LocalDate, meldekortdetaljer: Meldekortdetaljer): List<Dag> {
    val aktivitetsdager = List(14) { index ->
        Dag(fom.plusDays(index.toLong()), mutableListOf(), index)
    }
    meldekortdetaljer.sporsmal?.meldekortDager?.forEach { dag ->
        if (dag.arbeidetTimerSum != null && dag.arbeidetTimerSum > 0) {
            (aktivitetsdager[dag.dag - 1].aktiviteter as MutableList).add(
                Aktivitet(
                    UUIDv7.newUuid(),
                    Aktivitet.AktivitetsType.Arbeid,
                    dag.arbeidetTimerSum.toDouble()
                )
            )
        }
        if (dag.syk == true) {
            (aktivitetsdager[dag.dag - 1].aktiviteter as MutableList).add(
                Aktivitet(
                    UUIDv7.newUuid(),
                    Aktivitet.AktivitetsType.Syk,
                    null
                )
            )
        }
        if (dag.kurs == true) {
            (aktivitetsdager[dag.dag - 1].aktiviteter as MutableList).add(
                Aktivitet(
                    UUIDv7.newUuid(),
                    Aktivitet.AktivitetsType.Utdanning,
                    null
                )
            )
        }
        if (dag.annetFravaer == true) {
            (aktivitetsdager[dag.dag - 1].aktiviteter as MutableList).add(
                Aktivitet(
                    UUIDv7.newUuid(),
                    Aktivitet.AktivitetsType.Fravaer,
                    null
                )
            )
        }
    }

    return aktivitetsdager
}

private fun mapAktivitetsdager(arenaMeldekort: ArenaMeldekort): List<Dag> {
    val fraOgMed = arenaMeldekort.periode.fraOgMed

    val aktivitetsdager = List(14) { index ->
        Dag(fraOgMed.plusDays(index.toLong()), mutableListOf(), index)
    }
    arenaMeldekort.dager.forEach { dag ->
        if (dag.dagIndex !in aktivitetsdager.indices) return@forEach
        if (dag.arbeidsdag && dag.timer > BigDecimal.ZERO) {
            (aktivitetsdager[dag.dagIndex].aktiviteter as MutableList).add(
                Aktivitet(
                    UUIDv7.newUuid(),
                    Aktivitet.AktivitetsType.Arbeid,
                    dag.timer.toDouble()
                )
            )
        }
        if (dag.syk) {
            (aktivitetsdager[dag.dagIndex].aktiviteter as MutableList).add(
                Aktivitet(
                    UUIDv7.newUuid(),
                    Aktivitet.AktivitetsType.Syk,
                    null
                )
            )
        }
        if (dag.kurs) {
            (aktivitetsdager[dag.dagIndex].aktiviteter as MutableList).add(
                Aktivitet(
                    UUIDv7.newUuid(),
                    Aktivitet.AktivitetsType.Utdanning,
                    null
                )
            )
        }
        if (dag.annetfravaer) {
            (aktivitetsdager[dag.dagIndex].aktiviteter as MutableList).add(
                Aktivitet(
                    UUIDv7.newUuid(),
                    Aktivitet.AktivitetsType.Fravaer,
                    null
                )
            )
        }
    }

    return aktivitetsdager
}

private fun getcallId(headers: Headers): String {
    return headers[HttpHeaders.XRequestId] ?: "dp-adapter-${UUIDv7.newUuid()}"
}
