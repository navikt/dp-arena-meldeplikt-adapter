package no.nav.dagpenger.arenameldepliktadapter.models

data class Person(
    val personId: Long,
    val etternavn: String,
    val fornavn: String,
    val maalformkode: String,
    val meldeform: String,
    val meldekortListe: List<Meldekort>? = null,
)
