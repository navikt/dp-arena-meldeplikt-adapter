package no.nav.dagpenger.arenameldepliktadapter.models

import java.time.LocalDate

class MeldekortkontrollRequest(
    var meldekortId: Long = 0,
    var fnr: String,
    var personId: Long = 0,
    var kilde: String,
    var kortType: String,
    val meldedato: LocalDate? = null,
    val periodeFra: LocalDate? = null,
    val periodeTil: LocalDate? = null,
    var meldegruppe: String,
    var annetFravaer: Boolean? = null,
    var arbeidet: Boolean? = null,
    var arbeidssoker: Boolean? = null,
    var kurs: Boolean? = null,
    var syk: Boolean? = null,
    var begrunnelse: String?,
    var meldekortdager: List<MeldekortkontrollFravaer>
)

data class MeldekortkontrollFravaer(
    val dato: LocalDate? = null,
    val syk: Boolean? = null,
    val kurs: Boolean? = null,
    val annetFravaer: Boolean? = null,
    val arbeidTimer: Double? = null
)
