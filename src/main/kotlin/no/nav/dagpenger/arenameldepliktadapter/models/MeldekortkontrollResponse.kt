package no.nav.dagpenger.arenameldepliktadapter.models

data class MeldekortkontrollResponse(
    var meldekortId: Long = 0,
    var kontrollStatus: String = "",
    var feilListe: List<MeldekortkontrollFeil> = emptyList(),
    var oppfolgingListe: List<MeldekortkontrollFeil> = emptyList()
)

data class MeldekortkontrollFeil(
    var kode: String? = null,
    var tekst: String? = null,
    var dag: Int? = null,
    var params: Array<String>? = null
)
