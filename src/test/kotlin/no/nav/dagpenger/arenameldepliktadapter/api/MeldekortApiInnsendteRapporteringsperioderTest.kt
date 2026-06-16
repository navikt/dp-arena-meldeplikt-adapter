package no.nav.dagpenger.arenameldepliktadapter.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.dagpenger.arenameldepliktadapter.models.Aktivitet
import no.nav.dagpenger.arenameldepliktadapter.models.ArenaDag
import no.nav.dagpenger.arenameldepliktadapter.models.ArenaMeldekort
import no.nav.dagpenger.arenameldepliktadapter.models.Dag
import no.nav.dagpenger.arenameldepliktadapter.models.DatadelingRequest
import no.nav.dagpenger.arenameldepliktadapter.models.KortType
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortBeregningstatus
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortKilde
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortKildeRolle
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortPeriode
import no.nav.dagpenger.arenameldepliktadapter.models.MeldekortType
import no.nav.dagpenger.arenameldepliktadapter.models.OpprettetAv
import no.nav.dagpenger.arenameldepliktadapter.models.Rapporteringsperiode
import no.nav.dagpenger.arenameldepliktadapter.models.RapporteringsperiodeStatus
import no.nav.dagpenger.arenameldepliktadapter.utils.defaultObjectMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MeldekortApiInnsendteRapporteringsperioderTest : TestBase() {
    private val ident = "01020312345"

    @Test
    fun `innsendte-rapporteringsperioder uten token skal gi Unauthorized`() = setUpTestApplication {
        val response = client.post("/innsendte-rapporteringsperioder") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `innsendte-rapporteringsperioder uten request body skal gi BadRequest`() = setUpTestApplication {
        val token = issueToken(ident)

        val response = client.post("/innsendte-rapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `innsendte-rapporteringsperioder med ident i request som ikke er lik ident i token skal gi Unauthorized`() =
        setUpTestApplication {
            val token = issueToken(ident)
            val datadelingRequest = DatadelingRequest(
                personIdent = "01020312346",
                fraOgMedDato = LocalDate.now().minusDays(30),
            )

            val response = client.post("/innsendte-rapporteringsperioder") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, ContentType.Application.Json)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(defaultObjectMapper.writeValueAsString(datadelingRequest))
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `innsendte-rapporteringsperioder med Azure-token skal fungere`() =
        testInnsendteRapporteringsperioder {
            val token = issueAzureToken()
            val datadelingRequest = DatadelingRequest(
                personIdent = ident,
                fraOgMedDato = LocalDate.now().minusDays(30),
            )

            client.post("/innsendte-rapporteringsperioder") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, ContentType.Application.Json)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(defaultObjectMapper.writeValueAsString(datadelingRequest))
            }
        }

    @Test
    fun `innsendte-rapporteringsperioder med TokenX skal fungere`() = testInnsendteRapporteringsperioder {
        val token = issueToken(ident)
        val datadelingRequest = DatadelingRequest(
            personIdent = ident,
            fraOgMedDato = LocalDate.now().minusDays(30),
        )

        client.post("/innsendte-rapporteringsperioder") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(defaultObjectMapper.writeValueAsString(datadelingRequest))
        }
    }

    private fun testInnsendteRapporteringsperioder(block: suspend ApplicationTestBuilder.() -> HttpResponse) =
        setUpTestApplication {
            val fraOgMed = LocalDate.now().minusDays(15)
            val tilOgMed = LocalDate.now().minusDays(1)

            val periode = MeldekortPeriode(
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
            )
            val kilde = MeldekortKilde(
                rolle = MeldekortKildeRolle.BRUKER,
                ident = ident,
            )
            val kanSendesFra = LocalDate.now().minusDays(2)
            val sisteFristForTrekk = LocalDate.now().plusDays(8)
            val innsendtTidspunkt = LocalDateTime.now()
            val meldedato = LocalDate.now()

            val arenaMeldekort1 = ArenaMeldekort(
                id = "1",
                beregningstatus = MeldekortBeregningstatus.OVERM,
                type = MeldekortType.ELEKTRONISK,
                periode = periode,
                dager = opprettDager(),
                kanSendes = false,
                kanEndres = false,
                kanSendesFra = kanSendesFra,
                sisteFristForTrekk = sisteFristForTrekk,
                opprettetAv = OpprettetAv.ARENA,
                originalMeldekortId = null,
                begrunnelse = null,
                kilde = kilde,
                innsendtTidspunkt = innsendtTidspunkt,
                registrertArbeidssoker = true,
                meldedato = meldedato,
                belop = BigDecimal.valueOf(12345),
            )
            val arenaMeldekort2 = ArenaMeldekort(
                id = "2",
                beregningstatus = MeldekortBeregningstatus.FERDI,
                type = MeldekortType.KORRIGERT_ELEKTRONISK,
                periode = periode,
                dager = opprettDager(),
                kanSendes = true,
                kanEndres = true,
                kanSendesFra = kanSendesFra,
                sisteFristForTrekk = sisteFristForTrekk,
                opprettetAv = OpprettetAv.ARENA,
                originalMeldekortId = "1",
                begrunnelse = "Bla bla bla",
                kilde = kilde,
                innsendtTidspunkt = innsendtTidspunkt,
                registrertArbeidssoker = true,
                meldedato = meldedato,
                belop = null,
            )
            val meldekortListe = listOf(arenaMeldekort1, arenaMeldekort2)

            externalServices {
                hosts("https://dp-proxy") {
                    routing {
                        post("/proxy/v1/arena/innsendtemeldekort") {
                            call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            call.respond(defaultObjectMapper.writeValueAsString(meldekortListe))
                        }
                    }
                }
            }

            val response = block()

            assertEquals(HttpStatusCode.OK, response.status)
            val innsendteMeldekort = defaultObjectMapper.readValue<List<Rapporteringsperiode>>(response.bodyAsText())
            assertEquals(2, innsendteMeldekort.size)

            assertEquals(1, innsendteMeldekort[0].id)
            assertEquals(KortType.ELEKTRONISK, innsendteMeldekort[0].type)
            assertEquals(periode.fraOgMed, innsendteMeldekort[0].periode.fraOgMed)
            assertEquals(periode.tilOgMed, innsendteMeldekort[0].periode.tilOgMed)
            assertEquals(false, innsendteMeldekort[0].kanSendes)
            assertEquals(false, innsendteMeldekort[0].kanEndres)
            assertEquals(kanSendesFra, innsendteMeldekort[0].kanSendesFra)
            assertEquals(RapporteringsperiodeStatus.Endret, innsendteMeldekort[0].status)
            assertEquals(meldedato, innsendteMeldekort[0].mottattDato)
            assertEquals(12345.0, innsendteMeldekort[0].bruttoBelop)
            assertEquals(true, innsendteMeldekort[0].registrertArbeidssoker)
            assertEquals(null, innsendteMeldekort[0].begrunnelseEndring)
            sjekkDager(fraOgMed, innsendteMeldekort[0].dager)

            assertEquals(2, innsendteMeldekort[1].id)
            assertEquals(KortType.KORRIGERT_ELEKTRONISK, innsendteMeldekort[1].type)
            assertEquals(periode.fraOgMed, innsendteMeldekort[1].periode.fraOgMed)
            assertEquals(periode.tilOgMed, innsendteMeldekort[1].periode.tilOgMed)
            assertEquals(true, innsendteMeldekort[1].kanSendes)
            assertEquals(true, innsendteMeldekort[1].kanEndres)
            assertEquals(kanSendesFra, innsendteMeldekort[1].kanSendesFra)
            assertEquals(RapporteringsperiodeStatus.Ferdig, innsendteMeldekort[1].status)
            assertEquals(meldedato, innsendteMeldekort[1].mottattDato)
            assertEquals(null, innsendteMeldekort[1].bruttoBelop)
            assertEquals(true, innsendteMeldekort[1].registrertArbeidssoker)
            assertEquals("Bla bla bla", innsendteMeldekort[1].begrunnelseEndring)
            sjekkDager(fraOgMed, innsendteMeldekort[1].dager)
        }

    private fun opprettDager() = List(14) { index ->
        ArenaDag(
            index,
            if (index % 2 == 0) true else false,
            if (index % 3 == 0) true else false,
            if (index % 4 == 0) true else false,
            if (index % 5 == 0) true else false,
            if (index % 2 == 0) (index + 1).toBigDecimal() else BigDecimal.ZERO
        )
    }

    private fun sjekkDager(fraOgMed: LocalDate, dager: List<Dag>) {
        List(14) { index ->
            val dag = dager[index]
            assertEquals(index, dag.dagIndex)
            assertEquals(fraOgMed.plusDays(index.toLong()), dag.dato)
            assertEquals(
                if (index % 2 == 0) true else false,
                dag.aktiviteter.any { aktivitet -> aktivitet.type == Aktivitet.AktivitetsType.Arbeid }
            )
            assertEquals(
                if (index % 3 == 0) true else false,
                dag.aktiviteter.any { aktivitet -> aktivitet.type == Aktivitet.AktivitetsType.Fravaer }
            )
            assertEquals(
                if (index % 4 == 0) true else false,
                dag.aktiviteter.any { aktivitet -> aktivitet.type == Aktivitet.AktivitetsType.Utdanning }
            )
            assertEquals(
                if (index % 5 == 0) true else false,
                dag.aktiviteter.any { aktivitet -> aktivitet.type == Aktivitet.AktivitetsType.Syk }
            )
            assertEquals(
                if (index % 2 == 0) (index + 1.0) else null,
                dag.aktiviteter.find { aktivitet -> aktivitet.type == Aktivitet.AktivitetsType.Arbeid }?.timer
            )
        }
    }
}
