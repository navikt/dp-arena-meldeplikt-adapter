package no.nav.dagpenger.arenameldepliktadapter.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ArenaMeldekort(
    val id: String,
    val beregningstatus: MeldekortBeregningstatus,
    val type: MeldekortType,
    val periode: MeldekortPeriode,
    val dager: List<ArenaDag>,
    val kanSendes: Boolean,
    val kanEndres: Boolean,
    val kanSendesFra: LocalDate,
    val sisteFristForTrekk: LocalDate,
    val opprettetAv: OpprettetAv,
    val originalMeldekortId: String? = null,
    val begrunnelse: String? = null,
    val kilde: MeldekortKilde? = null,
    val innsendtTidspunkt: LocalDateTime? = null,
    val registrertArbeidssoker: Boolean? = null,
    val meldedato: LocalDate? = null,
    val belop: BigDecimal? = null,
)

data class MeldekortPeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
)

data class ArenaDag(
    val dagIndex: Int,
    val arbeidsdag: Boolean,
    val annetfravaer: Boolean,
    val kurs: Boolean,
    val syk: Boolean,
    val timer: BigDecimal,
)

data class MeldekortKilde(
    val rolle: MeldekortKildeRolle,
    val ident: String,
)

enum class MeldekortBeregningstatus(
    val value: String,
) {
    OPPRE("OPPRE"),
    SENDT("SENDT"),
    SLETT("SLETT"),
    REGIS("REGIS"),
    FMOPP("FMOPP"),
    FUOPP("FUOPP"),
    KLAR("KLAR"),
    KAND("KAND"),
    IKKE("IKKE"),
    OVERM("OVERM"),
    NYKTR("NYKTR"),
    FERDI("FERDI"),
    FEIL("FEIL"),
    VENTE("VENTE"),
    OPPF("OPPF"),
    UBEHA("UBEHA"),
    ;

    @JsonValue
    override fun toString(): String {
        return value
    }

    companion object {
        @JsonCreator
        @JvmStatic
        fun deserialize(value: String?): MeldekortBeregningstatus {
            var meldekortBeregningstatus: MeldekortBeregningstatus? = null
            for (entry in MeldekortBeregningstatus.entries) {
                if (entry.value == value) meldekortBeregningstatus = entry
            }

            return meldekortBeregningstatus ?: throw IllegalArgumentException("Ugyldig verdi [ $value ]")
        }
    }
}

enum class MeldekortType(
    val value: String,
) {
    ORDINAER("ORDINAER"),
    ERSTATNING("ERSTATNING"),
    RETUR("RETUR"),
    ELEKTRONISK("ELEKTRONISK"),
    AAP("AAP"),
    ORDINAER_MANUELL("ORDINAER_MANUELL"),
    MASKINELT_OPPDATERT("MASKINELT_OPPDATERT"),
    MANUELL_ARENA("MANUELL_ARENA"),
    KORRIGERT_ELEKTRONISK("KORRIGERT_ELEKTRONISK"),
    ;

    @JsonValue
    override fun toString(): String {
        return value
    }

    companion object {
        @JsonCreator
        @JvmStatic
        fun deserialize(value: String?): MeldekortType {
            var meldekortType: MeldekortType? = null
            for (entry in MeldekortType.entries) {
                if (entry.value == value) meldekortType = entry
            }

            return meldekortType ?: throw IllegalArgumentException("Ugyldig verdi [ $value ]")
        }
    }
}

enum class OpprettetAv(
    val value: String,
) {
    ARENA("Arena"),
    DAGPENGER("Dagpenger"),
    ;

    @JsonValue
    override fun toString(): String {
        return value
    }

    companion object {
        @JsonCreator
        @JvmStatic
        fun deserialize(value: String?): OpprettetAv {
            var opprettetAv: OpprettetAv? = null
            for (entry in OpprettetAv.entries) {
                if (entry.value == value) opprettetAv = entry
            }

            return opprettetAv ?: throw IllegalArgumentException("Ugyldig verdi [ $value ]")
        }
    }
}

enum class MeldekortKildeRolle(
    val value: String,
) {
    BRUKER("Bruker"),
    SAKSBEHANDLER("Saksbehandler"),
    ;

    @JsonValue
    override fun toString(): String {
        return value
    }

    companion object {
        @JsonCreator
        @JvmStatic
        fun deserialize(value: String?): MeldekortKildeRolle {
            var meldekortKildeRolle: MeldekortKildeRolle? = null
            for (entry in MeldekortKildeRolle.entries) {
                if (entry.value == value) meldekortKildeRolle = entry
            }

            return meldekortKildeRolle ?: throw IllegalArgumentException("Ugyldig verdi [ $value ]")
        }
    }
}
