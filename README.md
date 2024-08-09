# dp-arena-meldeplikt-adapter

## Formål
Dette er en adapter som skal fungere som et mellomlag mellom [dp-rapportering](https://github.com/navikt/dp-rapportering) og nåværende meldekort løsningen. Adapteren konverterer meldekort til rapporteringsperioder og rapporteringsperioder til meldekort for å hente og sende data fra og til Arena gjennom [meldekortservice](https://github.com/navikt/meldekortservice) og [meldekortkontroll-api](https://github.com/navikt/meldekortkontroll-api).

Adapter-mekanismen er designet for å lette overgangen fra en adapter til en annen. Selve Arena-adapteren er tiltenkt som en midlertidig løsning som skal erstattes med en annen adapter når vi slutter å bruke Arena som master for DP data.

## Funksjonalitet
- Henter rapporteringsperioder (dvs. hente tilgjengelige meldekort fra meldekortservice og konvertere til rapporteringsperioder)
- Henter person (henter Person-data fra meldekortserivce og returnerer den uten konvertering)
- Henter sendte rapporteringsperioder (dvs. henter historiske meldekort fra meldekortservice og konverterer til rapporteringsperioder)
- Henter rapporteringsperiode (meldekort) ID for endring (korrigering) fra meldekortservice
- Sender rapporteringsperiode til Arena (dvs. konverterer en innsendt rapporteringsperiode til MeldekortkontrollRequest, sender den til meldekortkontroll-api og returnerer InnsendingResponse)

## Mer dokumentasjon
Rapportering i dagpenger-dokumentasjon: https://dagpenger-dokumentasjon.ansatt.nav.no/innbyggerflate/losninger/rapportering

## Lokal kjøring
Start meldekortservice lokalt

Start adapteren ved å kjøre `./gradlew runServerTest`.
