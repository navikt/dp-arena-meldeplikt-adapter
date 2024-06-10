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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.arenameldepliktadapter.models.Aktivitet
import no.nav.dagpenger.arenameldepliktadapter.models.Dag
import no.nav.dagpenger.arenameldepliktadapter.models.InnsendingFeil
import no.nav.dagpenger.arenameldepliktadapter.models.InnsendingResponse
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
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config
import java.time.LocalDate
import java.util.*

fun Routing.meldekortApi(httpClient: HttpClient) {
    authenticate {
        route("/rapporteringsperioder") {
            get {
                try {
                    val authString = call.request.header(HttpHeaders.Authorization)!!

                    val response = sendHttpRequestWithRetry(httpClient, authString, "/v2/meldekort")

                    if (response.status == HttpStatusCode.NoContent) {
                        call.response.status(HttpStatusCode.NoContent)
                        return@get
                    }

                    val person = defaultObjectMapper.readValue<Person>(response.bodyAsText())

                    val rapporteringsperioder = person.meldekortListe?.filter { meldekort ->
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
                            kanKorrigeres(meldekort, person.meldekortListe),
                            RapporteringsperiodeStatus.TilUtfylling
                        )
                    } ?: emptyList()

                    call.respondText(
                        defaultObjectMapper.writeValueAsString(rapporteringsperioder),
                        ContentType.Application.Json
                    )
                } catch (e: Exception) {
                    call.application.environment.log.error("Feil ved henting av rapporteringsperioder: $e")
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/sendterapporteringsperioder") {
            get {
                try {
                    val authString = call.request.header(HttpHeaders.Authorization)!!

                    val response = sendHttpRequestWithRetry(
                        httpClient,
                        authString,
                        "/v2/historiskemeldekort?antallMeldeperioder=5"
                    )
                    val person = defaultObjectMapper.readValue<Person>(response.bodyAsText())

                    val rapporteringsperioder = person.meldekortListe?.filter { meldekort ->
                        meldekort.hoyesteMeldegruppe in arrayOf(
                            "ARBS",
                            "DAGP"
                        )
                    }?.map { meldekort ->
                        val kanSendesFra = meldekort.tilDato.minusDays(1)

                        val responseDetaljer = sendHttpRequestWithRetry(
                            httpClient,
                            authString,
                            "/v2/meldekortdetaljer?meldekortId=${meldekort.meldekortId}"
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
                            kanKorrigeres(meldekort, person.meldekortListe),
                            if (meldekort.beregningstatus in arrayOf(
                                    "FERDI",
                                    "IKKE",
                                    "OVERM"
                                )
                            ) RapporteringsperiodeStatus.Ferdig
                            else RapporteringsperiodeStatus.Innsendt,
                            meldekort.bruttoBelop.toDouble(),
                            meldekortdetaljer.sporsmal?.arbeidssoker
                        )
                    }

                    call.respondText(
                        defaultObjectMapper.writeValueAsString(rapporteringsperioder),
                        ContentType.Application.Json
                    )
                } catch (e: Exception) {
                    call.application.environment.log.error("Feil ved henting av innsendte rapporteringsperioder: $e")
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/korrigertMeldekort/{meldekortId}") {
            get {
                try {
                    val authString = call.request.header(HttpHeaders.Authorization)!!

                    val meldekortId = call.parameters["meldekortId"]
                    if (meldekortId.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }

                    val response = sendHttpRequestWithRetry(
                        httpClient,
                        authString,
                        "/v2/korrigertMeldekort?meldekortId=$meldekortId"
                    )

                    call.respondText(response.bodyAsText())
                } catch (e: Exception) {
                    call.application.environment.log.error("Feil ved henting av korrigert meldekort: $e")
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/sendinn") {
            post {
                try {
                    val authString = call.request.header(HttpHeaders.Authorization)!!

                    call.application.environment.log.info("Innsending")

                    val rapporteringsperiode = defaultObjectMapper.readValue<Rapporteringsperiode>(call.receiveText())
                    call.application.environment.log.info("Send inn ID ${rapporteringsperiode.id}")

                    // Henter meldekortdetaljer og meldekortservice sjekker at ident stemmer med FNR i dette meldekortet
                    val responseDetaljer = sendHttpRequestWithRetry(
                        httpClient,
                        authString,
                        "/v2/meldekortdetaljer?meldekortId=${rapporteringsperiode.id}"
                    )
                    val meldekortdetaljer = defaultObjectMapper.readValue<Meldekortdetaljer>(
                        responseDetaljer.bodyAsText()
                    )
                    call.application.environment.log.info("Meldekortdetaljer: $meldekortdetaljer")

                    // Mapper meldekortdager
                    val meldekortdager: List<MeldekortkontrollFravaer> = rapporteringsperiode.dager.map { dag ->
                        MeldekortkontrollFravaer(
                            dag.dato,
                            dag.finnesAktivitetMedType(Aktivitet.AktivitetsType.Syk),
                            dag.finnesAktivitetMedType(Aktivitet.AktivitetsType.Utdanning),
                            dag.finnesAktivitetMedType(Aktivitet.AktivitetsType.FerieEllerFravaer),
                            dag.hentArbeidstimer()
                        )
                    }
                    call.application.environment.log.info("Meldekortdager: $meldekortdager")

                    // Oppretter MeldekortkontrollRequest
                    val meldekortkontrollRequest = MeldekortkontrollRequest(
                        meldekortdetaljer.meldekortId,
                        meldekortdetaljer.fodselsnr,
                        meldekortdetaljer.personId,
                        "DP",
                        meldekortdetaljer.kortType,
                        if (meldekortdetaljer.kortType == "KORRIGERT_ELEKTRONISK" && meldekortdetaljer.meldeDato != null) meldekortdetaljer.meldeDato else LocalDate.now(),
                        rapporteringsperiode.periode.fraOgMed,
                        rapporteringsperiode.periode.tilOgMed,
                        meldekortdetaljer.meldegruppe,
                        rapporteringsperiode.finnesDagMedAktivitetsType(Aktivitet.AktivitetsType.FerieEllerFravaer),
                        rapporteringsperiode.finnesDagMedAktivitetsType(Aktivitet.AktivitetsType.Arbeid),
                        rapporteringsperiode.registrertArbeidssoker!!,
                        rapporteringsperiode.finnesDagMedAktivitetsType(Aktivitet.AktivitetsType.Utdanning),
                        rapporteringsperiode.finnesDagMedAktivitetsType(Aktivitet.AktivitetsType.Syk),
                        if (meldekortdetaljer.kortType == "KORRIGERT_ELEKTRONISK") "Korrigert av bruker" else null,
                        meldekortdager
                    )
                    call.application.environment.log.info("MeldekortkontrollRequest: $meldekortkontrollRequest")

                    // Henter TokenX
                    val incomingToken = authString.replace("Bearer ", "")
                    val tokenProvider = tokenExchanger(incomingToken, getEnv("MELDEKORTKONTROLL_AUDIENCE") ?: "")

                    // Request til meldekortkontroll-api
                    val response = httpClient.post(getEnv("MELDEKORTKONTROLL_URL")!!) {
                        header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
                        header(HttpHeaders.Accept, ContentType.Application.Json)
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        setBody(defaultObjectMapper.writeValueAsString(meldekortkontrollRequest))
                    }

                    val meldekortkontrollResponse = defaultObjectMapper.readValue<MeldekortkontrollResponse>(
                        response.bodyAsText()
                    )

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
                    call.application.environment.log.error("Feil ved innsending: $e")
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}

private suspend fun sendHttpRequestWithRetry(httpClient: HttpClient, authString: String, path: String): HttpResponse {
    var retries = 0
    var response: HttpResponse

    do {
        response = sendHttpRequest(httpClient, authString, path)
        retries++
    } while (response.status != HttpStatusCode.OK && retries < 3)

    return response
}

private suspend fun sendHttpRequest(httpClient: HttpClient, authString: String, path: String): HttpResponse {
    val incomingToken = authString.replace("Bearer ", "")
    val tokenProvider = tokenExchanger(incomingToken, getEnv("MELDEKORTSERVICE_AUDIENCE") ?: "")

    val decodedToken = decodeToken(authString)
    val ident = extractSubject(decodedToken)

    return httpClient.get(getEnv("MELDEKORTSERVICE_URL") + path) {
        header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header("ident", ident)
    }
}

private fun kanKorrigeres(meldekort: Meldekort, meldekortListe: List<Meldekort>): Boolean {
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
                    Aktivitet.AktivitetsType.FerieEllerFravaer,
                    null
                )
            )
        }
    }

    return aktivitetsdager
}

private fun tokenExchanger(token: String, audience: String): () -> String = {
    runBlocking { tokenXClient.tokenExchange(token, audience).accessToken }
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
