package no.nav.dagpenger.arenameldepliktadapter.models

import java.time.LocalDate
import java.util.*

data class Rapporteringsperiode(
    val id: Long, // meldekortId
    val periode: Periode,
    val dager: List<Dag>,
    val kanSendesFra: LocalDate,
    val kanSendes: Boolean,
    val kanKorrigeres: Boolean
)

data class Periode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate
)

class Dag(
    val dato: LocalDate,
    val aktiviteter: List<Aktivitet> = emptyList()
)

data class Aktivitet(
    val uuid: UUID,
    val type: AktivitetsType,
    val timer: String?
) {
    enum class AktivitetsType {
        Arbeid,
        Syk,
        Utdanning,
        Fravaer
    }
}
