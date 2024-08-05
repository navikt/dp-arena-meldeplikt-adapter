package no.nav.dagpenger.arenameldepliktadapter.models

import java.time.LocalDate

data class Meldegruppe(
    val fodselsnr: String,
    val meldegruppeKode: String,
    val datoFra: LocalDate,
    val datoTil: LocalDate? = null,
    val hendelsesdato: LocalDate,
    val statusAktiv: String,
    val begrunnelse: String,
    val styrendeVedtakId: Long? = null
)
