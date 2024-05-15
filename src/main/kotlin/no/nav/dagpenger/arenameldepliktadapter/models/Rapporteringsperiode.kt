package no.nav.dagpenger.arenameldepliktadapter.models

import java.time.LocalDate
import java.util.*
import kotlin.time.Duration

data class Rapporteringsperiode(
    val ident: String,
    val id: Long,  // meldekortId
    val periode: Periode,
    val aktivitetstidslinje: Aktivitetstidslinje,
    val kanKorrigeres: Boolean,
)

data class Periode(
    val fra: LocalDate,
    val til: LocalDate,
    val kanSendesFra: LocalDate,
)

data class Aktivitetstidslinje internal constructor(
    private val dager: MutableSet<Dag> = mutableSetOf(),
)

class Dag(
    internal val dato: LocalDate,
    private val aktiviteter: MutableList<Aktivitet>,
)

sealed class Aktivitet(
    val dato: LocalDate,
    val tid: Duration,
    val type: AktivitetType,
    val uuid: UUID = UUID.randomUUID(),
) {
    enum class AktivitetType {
        Arbeid,
        Syk,
        Utdanning,
        Annet
    }
}
