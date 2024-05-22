package no.nav.dagpenger.arenameldepliktadapter.models

import java.time.LocalDate

data class Rapporteringsperiode(
    val id: Long, // meldekortId
    val periode: Periode,
    val dager: List<Dag>,
    val kanSendesFra: LocalDate,
    val kanSendes: Boolean,
    val kanKorrigeres: Boolean
)

data class Periode(val fraOgMed: LocalDate, val tilOgMed: LocalDate) {
    init {
        require(!fraOgMed.isAfter(tilOgMed)) {
            "Fra og med-dato kan ikke være etter til og med-dato"
        }
    }

    fun inneholder(dato: LocalDate): Boolean {
        return !dato.isBefore(fraOgMed) && !dato.isAfter(tilOgMed)
    }
}

class Dag(
    val dato: LocalDate,
    val aktiviteter: List<Aktivitet> = emptyList()
)

data class Aktivitet(
    val dato: LocalDate,
    val type: AktivitetsType
)

enum class AktivitetsType {
    ARBIED,
    SYK,
    UTDANNING,
    FERIEELLERFRAVAER
}
