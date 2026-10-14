![](https://github.com/navikt/eessi-pensjon-fagmodul/workflows/Bygg%20og%20deploy%20Q2/badge.svg)
![](https://github.com/navikt/eessi-pensjon-fagmodul/workflows/Deploy%20Q1/badge.svg)
![](https://github.com/navikt/eessi-pensjon-fagmodul/workflows/Manuell%20deploy/badge.svg)


EESSI Pensjon Fagmodul
======================

#### -=<>=- Electronic Exchange of Social Security Information  -=<>=- ####

# Utvikling

## Komme i gang

git clone [url:denne repo]


```
./gradlew assemble
```

##Systembruker
legge systembruker 'srveessipensjon' og passord inn som systemverdier:
```
SRVEESSIPENSJON_USERNAME
```

Benyttes under kjøring debug T8 miljø
```
SRVEESSIPENSJON_PASSWORD_T  
```
benyttes under kjøring debug Q2 miljø
```
SRVEESSIPENSJON_PASSWORD_Q  
```


## Oppdatere avhengigheter

Det er viktig at man holder avhengigheter oppdatert for å unngå sikkerhetshull.

Se mer dokumentasjon rundt dette her: [Oppgradere avhengigheter](https://github.com/navikt/eessi-pensjon/blob/master/docs/dev/oppgradere_avhengigheter.md).

## Data til P12000 fra Pesys

`GET /pensjon/ytelserpermaaned/{sakId}` henter ytelsesperioder til P12000 fra Pesys for en sak.
Endpointet krever autentisering.
Oppslaget bruker den eksisterende Pesys-klienten med tjenestetoken.

Pesys kalles fortsatt med `GET /sed/p6000` og `sakId` i en header.
Endpointet samler `ytelsePerMaaned` fra alle vedtak, uavhengig av vedtaksdato.
Rekkefølgen og eventuelle duplikater fra Pesys beholdes.

Query-parametrene `fom` og `tom` er valgfrie datoer i formatet `yyyy-MM-dd`, for eksempel
`/pensjon/ytelserpermaaned/123?fom=2025-01-01&tom=2025-12-31`.
De inkluderer ytelsesperioder som overlapper intervallet: periodens `fom` er før
eller lik forespurt `tom`, og periodens `tom` er etter eller lik forespurt `fom`.
Begge grensene er inklusive, og `tom = null` betyr at perioden
fortsatt løper. Én dato gir en åpen grense i den andre retningen. Uten datoer
returneres alle periodene. Periodenes opprinnelige `fom` og `tom` endres ikke.

Responsen er `ResponseEntity<FrontEndResponse<List<P6000MeldingOmVedtakDto.YtelsePerMaaned>>>`.
Listen ligger i `result`, med status `200 OK`. Ingen overlappende perioder, tom liste eller manglende responsbody gir
`404 NOT_FOUND`. Blank sakId, ugyldig dato eller `fom` etter `tom` gir `400 BAD_REQUEST`.
Feil fra Pesys sendes videre til eksisterende
feilhåndtering. Responsen inneholder Pesys-data, ikke en ferdig utfylt P12000-SED.

## SonarQube m/JaCoCo

Prosjektet er satt opp med støtte for å kunne kjøre SonarQube, med JaCoCo for å fange test coverage, men du trenger å ha en SonarQube-instans (lokal?) å kjøre dataene inn i - [les mer her](https://github.com/navikt/eessi-pensjon/blob/master/docs/dev/sonarqube.md).

---


Så er du klar til å starte EessiFagmodulApplication med VM option:

```
-Dspring.profiles.active=local
```

da benyttes application-local.yml ønskes det å debugge i Q2 må 
application-local_Q2.yml enres til application-local.yml. 

---

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #eessi-pensj-utviklere.

# For å hente ut info om siste ukes commits

(echo “./.git”; ls -d */.git) | sed ‘s#/.git##’ | xargs -I{} sh -c “git pull --rebase --autostash > /dev/null ; pushd {} > /dev/null ; git log --reverse --format=' (%cr) %h %s’ --since=‘8 days’ | sed ‘s/^/{}:/’ ; popd > /dev/null”
