package no.nav.dagpenger.arenameldepliktadapter.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDate

data class Meldekort(
    val meldekortId: Long,
    val kortType: KortType,
    val meldeperiode: String,
    val fraDato: LocalDate,
    val tilDato: LocalDate,
    val hoyesteMeldegruppe: String,
    val beregningstatus: String,
    val forskudd: Boolean,
    val mottattDato: LocalDate? = null,
    val bruttoBelop: Float = 0F
)

enum class KortType(private val code: String) {
    ORDINAER("01"),
    ERSTATNING("03"),
    RETUR("04"),
    ELEKTRONISK("05"),
    AAP("06"),
    ORDINAER_MANUELL("07"),
    MASKINELT_OPPDATERT("08"),
    MANUELL_ARENA("09"),
    KORRIGERT_ELEKTRONISK("10");

    @JsonValue
    override fun toString(): String {
        return code
    }

    companion object {
        @JsonCreator
        @JvmStatic
        fun deserialize(code: String?): KortType {
            var kortType: KortType? = null
            for (entry in entries) {
                if (entry.code == code) kortType = entry
            }

            return kortType ?: throw IllegalArgumentException("Ugyldig kode [ $code ]")
        }
    }
}
