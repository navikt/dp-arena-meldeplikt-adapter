package no.nav.dagpenger.arenameldepliktadapter.models

import java.time.LocalDate

class MeldekortkontrollRequest(
    var meldekortId: Long = 0,
    var fnr: String,
    var personId: Long = 0,
    var kilde: String,
    var kortType: String,
    val meldedato: LocalDate,
    val periodeFra: LocalDate,
    val periodeTil: LocalDate,
    var meldegruppe: String,
    var annetFravaer: Boolean,
    var arbeidet: Boolean,
    var arbeidssoker: Boolean,
    var kurs: Boolean,
    var syk: Boolean,
    var begrunnelse: String?,
    var meldekortdager: List<MeldekortkontrollFravaer>
)

data class MeldekortkontrollFravaer(
    val dato: LocalDate,
    val syk: Boolean,
    val kurs: Boolean,
    val annetFravaer: Boolean,
    val arbeidTimer: Double
)
