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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.arenameldepliktadapter.models.Aktivitet
import no.nav.dagpenger.arenameldepliktadapter.models.Dag
import no.nav.dagpenger.arenameldepliktadapter.models.InnsendingFeil
import no.nav.dagpenger.arenameldepliktadapter.models.InnsendingResponse
import no.nav.dagpenger.arenameldepliktadapter.models.Meldegruppe
import no.nav.dagpenger.arenameldepliktadapter.models.Meldekort
import no.nav.dagpenger.arenameldepliktadapter.models.Meldekortdetaljer
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollFravaer
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollRequest
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortkontrollResponse
import no.nav.dagpenger.arenameldepliktadapter.models.Periode
import no.nav.dagpenger.arenameldepliktadapter.models.Person
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.models.RapporteringsperiodeStatus
import no.nav.dagpenger.arenameldepliktadapter.utils.decodeToken
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import no.nav.dagpenger.arenameldepliktadapter.utils.extractSubject
import no.nav.dagpenger.arenameldepliktadapter.utils.getEnv
import no.nav.dagpenger.arenameldepliktadapter.utils.isCurrentlyRunningLocally
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

fun Routing.meldekortApi(httpClient: HttpClient) {
    authenticate {
        route("/harmeldeplikt") {
            get {
                try {
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val response = sendHttpRequestWithRetry(
                        sendHttpRequestTilMeldekortservice(httpClient, authString, callId, "/v2/meldegrupper")
                    )

                    val meldegrupper = defaultObjectMapper.readValue<List<Meldegruppe>>(response.bodyAsText())

                    var harMeldeplikt = "false"

                    if (meldegrupper.any { meldegruppe -> meldegruppe.meldegruppeKode == "DAGP" }) {
                        harMeldeplikt = "true"
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
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val response = sendHttpRequestWithRetry(
                        sendHttpRequestTilMeldekortservice(httpClient, authString, callId, "/v2/meldekort")
                    )

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
                        sendHttpRequestTilMeldekortservice(httpClient, authString, callId, "/v2/meldekort")
                    )

                    if (response.status == HttpStatusCode.NoContent) {
                        call.response.status(HttpStatusCode.NoContent)
                        return@get
                    }

                    val person = defaultObjectMapper.readValue<Person>(response.bodyAsText())

                    call.respondText(
                        defaultObjectMapper.writeValueAsString(person),
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
                    val authString = call.request.header(HttpHeaders.Authorization)
                    val callId = getcallId(call.request.headers)
                    call.response.header(HttpHeaders.XRequestId, callId)

                    val response = sendHttpRequestWithRetry(
                        sendHttpRequestTilMeldekortservice(
                            httpClient,
                            authString,
                            callId,
                            "/v2/historiskemeldekort?antallMeldeperioder=5"
                        )
                    )
                    val person = defaultObjectMapper.readValue<Person>(response.bodyAsText())

                    // Vi tar ikke bare DAGP meldekort her, men også ARBS fordi det er naturlig å forutsette at hvis bruker har DP nå, tilhører tidligere ARBS meldekort DP
                    val rapporteringsperioder = person.meldekortListe?.filter { meldekort ->
                        meldekort.hoyesteMeldegruppe in arrayOf(
                            "ARBS",
                            "DAGP"
                        )
                    }?.map { meldekort ->
                        val kanSendesFra = meldekort.tilDato.minusDays(1)

                        val responseDetaljer = sendHttpRequestWithRetry(
                            sendHttpRequestTilMeldekortservice(
                                httpClient,
                                authString,
                                callId,
                                "/v2/meldekortdetaljer?meldekortId=${meldekort.meldekortId}"
                            )
                        )
                        val meldekortdetaljer = defaultObjectMapper.readValue<Meldekortdetaljer>(
                            responseDetaljer.bodyAsText()
                        )

                        val aktivitetsdager = mapAktivitetsdager(meldekort.fraDato, meldekortdetaljer)

                        Rapporteringsperiode(
                            meldekort.meldekortId,
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
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }

                    val response = sendHttpRequestWithRetry(
                        sendHttpRequestTilMeldekortservice(
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
                        sendHttpRequestTilMeldekortservice(
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
                        sendHttpRequestTilMeldekortkontroll(httpClient, authString, callId, meldekortkontrollRequest)
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
    }
}

private suspend fun sendHttpRequestWithRetry(
    fn: suspend () -> HttpResponse
): HttpResponse {
    var retries = 0
    var response: HttpResponse?

    do {
        if (retries > 0) delay(1000)

        response = try {
            fn.invoke()
        } catch (e: Exception) {
            logger.warn(e) { "Feil ved sending request. Forsøk ${retries+1}" }
            null
        }

        retries++
    } while ((response == null || response.status != HttpStatusCode.OK) && retries < 3)

    if (response == null) throw Exception("Kunne ikke få response etter $retries forsøk")
    if (response.status.value > 300) throw Exception("Uforventet HTTP status ${response.status.value} etter $retries forsøk")

    return response
}

private fun sendHttpRequestTilMeldekortservice(
    httpClient: HttpClient,
    authString: String?,
    callId: String,
    path: String
): () -> HttpResponse = {
    val incomingToken = authString?.replace("Bearer ", "") ?: ""
    val tokenProvider = tokenExchanger(incomingToken, getEnv("MELDEKORTSERVICE_AUDIENCE") ?: "")

    val decodedToken = decodeToken(authString)
    val ident = extractSubject(decodedToken)

    runBlocking {
        httpClient.get(getEnv("MELDEKORTSERVICE_URL") + path) {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.XRequestId, callId)
            header("ident", ident)
        }
    }
}

private fun sendHttpRequestTilMeldekortkontroll(
    httpClient: HttpClient,
    authString: String?,
    callId: String,
    meldekortkontrollRequest: MeldekortkontrollRequest
): () -> HttpResponse = {
    val incomingToken = authString?.replace("Bearer ", "") ?: ""
    val tokenProvider = tokenExchanger(incomingToken, getEnv("MELDEKORTKONTROLL_AUDIENCE") ?: "")

    runBlocking {
        httpClient.post(getEnv("MELDEKORTKONTROLL_URL")!!) {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.XRequestId, callId)
            setBody(defaultObjectMapper.writeValueAsString(meldekortkontrollRequest))
        }
    }
}

private fun kanEndres(meldekort: Meldekort, meldekortListe: List<Meldekort>): Boolean {
    return if (meldekort.kortType == "10" || meldekort.beregningstatus == "UBEHA") {
        false
    } else {
        meldekortListe.find { mk -> (meldekort.meldekortId != mk.meldekortId && meldekort.meldeperiode == mk.meldeperiode && mk.kortType == "10") } == null
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
                    UUID.randomUUID(),
                    Aktivitet.AktivitetsType.Arbeid,
                    dag.arbeidetTimerSum.toDouble()
                )
            )
        }
        if (dag.syk == true) {
            (aktivitetsdager[dag.dag - 1].aktiviteter as MutableList).add(
                Aktivitet(
                    UUID.randomUUID(),
                    Aktivitet.AktivitetsType.Syk,
                    null
                )
            )
        }
        if (dag.kurs == true) {
            (aktivitetsdager[dag.dag - 1].aktiviteter as MutableList).add(
                Aktivitet(
                    UUID.randomUUID(),
                    Aktivitet.AktivitetsType.Utdanning,
                    null
                )
            )
        }
        if (dag.annetFravaer == true) {
            (aktivitetsdager[dag.dag - 1].aktiviteter as MutableList).add(
                Aktivitet(
                    UUID.randomUUID(),
                    Aktivitet.AktivitetsType.Fravaer,
                    null
                )
            )
        }
    }

    return aktivitetsdager
}

private fun getcallId(headers: Headers): String {
    return headers[HttpHeaders.XRequestId] ?: "dp-adapter-${UUID.randomUUID()}"
}

private fun tokenExchanger(token: String, audience: String): () -> String = {
    if (isCurrentlyRunningLocally()) {
        ""
    } else {
        runBlocking { tokenXClient.tokenExchange(token, audience).accessToken ?: "" }
    }
}

private val tokenXClient: CachedOauth2Client by lazy {
    val config = HashMap<String, String>()
    config["TOKEN_X_CLIENT_ID"] = getEnv("TOKEN_X_CLIENT_ID") ?: ""
    config["TOKEN_X_PRIVATE_JWK"] = getEnv("TOKEN_X_PRIVATE_JWK") ?: ""
    config["TOKEN_X_WELL_KNOWN_URL"] = getEnv("TOKEN_X_WELL_KNOWN_URL") ?: ""

    val tokenXConfig = OAuth2Config.TokenX(config)
    CachedOauth2Client(
        tokenEndpointUrl = tokenXConfig.tokenEndpointUrl,
        authType = tokenXConfig.privateKey(),
    )
}
