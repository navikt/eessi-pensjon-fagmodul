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

