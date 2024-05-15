package no.nav.dagpenger.arenameldepliktadapter.models

import java.time.LocalDate

data class Meldekort(
    val meldekortId: Long,
    val kortType: String,
    val meldeperiode: String,
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val hoyesteMeldegruppe: String,
    val beregningstatus: String,
    val forskudd: Boolean,
    val mottattDato: LocalDate? = null,
    val bruttoBelop: Float = 0F
)
