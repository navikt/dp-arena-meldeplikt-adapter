package no.nav.dagpenger.arenameldepliktadapter.models

import java.time.LocalDate

data class Meldekortdetaljer(
    val id: String,
    val personId: Long,
    val fodselsnr: String,
    val meldekortId: Long,
    val meldeperiode: String,
    val meldegruppe: String,
    val arkivnokkel: String,
    val kortType: String,
    val meldeDato: LocalDate? = null,
    val lestDato: LocalDate? = null,
    val sporsmal: Sporsmal? = null,
    val begrunnelse: String? = ""
)

data class Sporsmal(
    val arbeidssoker: Boolean? = null,
    val arbeidet: Boolean? = null,
    val syk: Boolean? = null,
    val annetFravaer: Boolean? = null,
    val kurs: Boolean? = null,
    val forskudd: Boolean? = null,
    val signatur: Boolean? = null,
    val meldekortDager: List<MeldekortDag>? = null
)

data class MeldekortDag(
    val dag: Int = 0,
    val arbeidetTimerSum: Float? = null,
    val syk: Boolean? = null,
    val annetFravaer: Boolean? = null,
    val kurs: Boolean? = null,
    val meldegruppe: String? = null
)
