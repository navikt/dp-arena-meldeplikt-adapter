package no.nav.dagpenger.arenameldepliktadapter.models

import java.time.LocalDate
import java.util.*

data class Rapporteringsperiode(
    val id: Long, // meldekortId
    val periode: Periode,
    val dager: List<Dag>,
    val kanSendesFra: LocalDate,
    val kanSendes: Boolean,
    val kanKorrigeres: Boolean,
    val status: RapporteringsperiodeStatus,
    val bruttoBelop: Double? = null,
    val registrertArbeidssoker: Boolean? = null
) {
    fun finnesDagMedAktivitetsType(aktivitetsType: Aktivitet.AktivitetsType): Boolean {
        return dager.find { dag -> dag.finnesAktivitetMedType(aktivitetsType) } != null
    }
}

data class Periode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate
)

enum class RapporteringsperiodeStatus {
    TilUtfylling,
    Korrigert,
    Innsendt,
    Ferdig
}

class Dag(
    val dato: LocalDate,
    val aktiviteter: List<Aktivitet> = emptyList(),
    val dagIndex: Int
) {
    fun finnesAktivitetMedType(aktivitetsType: Aktivitet.AktivitetsType): Boolean {
        return this.aktiviteter.find { aktivitet -> aktivitet.type == aktivitetsType } != null
    }

    fun hentArbeidstimer(): Double {
        return this.aktiviteter.find { aktivitet -> aktivitet.type == Aktivitet.AktivitetsType.Arbeid }?.timer ?: 0.0
    }
}

data class Aktivitet(
    val uuid: UUID,
    val type: AktivitetsType,
    val timer: Double?
) {
    enum class AktivitetsType {
        Arbeid,
        Syk,
        Utdanning,
        Fravaer
    }
}
