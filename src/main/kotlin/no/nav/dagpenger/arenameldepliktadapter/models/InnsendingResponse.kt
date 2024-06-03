package no.nav.dagpenger.arenameldepliktadapter.models

data class InnsendingResponse(
    val id: Long,
    val status: String,
    val feil: List<InnsendingFeil>
)

data class InnsendingFeil(
    val kode: String,
    val params: List<String>? = null
)
