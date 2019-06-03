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

Sjekke om man har utdaterte avhengigheter (forsøker å unngå milestones og beta-versjoner):

```
./gradlew dependencyUpdates
```

Enkelte interne artifakter har ikke komplette metadata i repo.adeo.no - noe som gir en advarsel:

```
 Failed to determine the latest version for the following dependencies (use --info for details):
 - no.nav.pensjon:pensjonsinformasjon-xsd
 - no.nav.tjenester:nav-person-v3-tjenestespesifikasjon
```

Dersom du er supertrygg på testene kan du forsøke en oppdatering av alle avhengighetene:

```
./gradlew useLatestVersions && ./gradlew useLatestVersionsCheck
```

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
