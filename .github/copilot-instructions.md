# eessi-pensjon-fagmodul

Kotlin/Spring Boot backend service for NAV's EESSI Pensjon system. Handles prefilling and
exchange of SED (Structured Electronic Documents) within BUCs (Business Use Cases) via the
EUX RINA API, for cross-border pension case handling in the EU/EEA.

## Build, test, lint

- Build: `./gradlew assemble`
- Full build incl. tests (what CI runs): `./gradlew build --stacktrace`
- Run all tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests "no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillServiceTest"`
- Run a single test method: `./gradlew test --tests "no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillServiceTest.navn på test"`
- Requires Java 21 (see `.java-version`); Kotlin compiler warnings are treated as errors
  (`allWarningsAsErrors = true` in `buildSrc/ep-module.gradle`), so builds fail on warnings.
- To run locally with `EessiFagmodulApplication`, set VM option `-Dspring.profiles.active=local`
  (uses `application-local.yml`; requires `SRVEESSIPENSJON_USERNAME`/`SRVEESSIPENSJON_PASSWORD_*`
  as env vars).
- `src/test/kotlin/.../architecture/ArchitectureTest.kt` uses ArchUnit to enforce the module
  layering below and several coding rules — run it (`./gradlew test --tests "*ArchitectureTest"`)
  after moving/adding classes or new package dependencies.

## Architecture

Base package: `no.nav.eessi.pensjon`. Key top-level packages under `src/main/kotlin/no/nav/eessi/pensjon/`:

- `api/` — REST controllers for external-facing concerns: `geo`, `gjenny`, `pensjon`, `person`.
  Controllers here must never be called by other layers (enforced by ArchitectureTest).
- `fagmodul/` — the core domain:
  - `fagmodul/api` — BUC/SED-facing REST controllers ("Frontend API" layer).
  - `fagmodul/prefill` — prefilling logic that populates SEDs from NAV data (pension, person,
    etc.) before they are sent via EUX. Only accessible from `fagmodul/api` and `api/pensjon`.
  - `fagmodul/eux` — the EUX/RINA integration layer (`EuxPrefillService`,
    `EuxInnhentingService`, `BucUtils`, etc.), talking to the external `EuxKlientLib`
    (from the `ep-eux` NAV library). Accessible from `fagmodul/api`, `fagmodul/pesys`,
    `fagmodul/prefill`, `api/person`.
  - `fagmodul/eux/bucmodel` — BUC/SED domain model classes.
  - `fagmodul/pesys` — integration with Pesys (pension system); only reachable from
    `fagmodul/eux`.
  - `fagmodul/config` — Spring configuration; must not be depended on by any other layer.
- `services/` — supporting services: `pensjonsinformasjon` (pension info) and `statistikk`
  (SED/BUC event statistics).
- `shared/` — cross-cutting API models (`shared/api`, e.g. `PrefillDataModel`,
  `ApiRequest`) and `shared/retry` helpers, shared across layers.
- `vedlegg/` — attachment/document handling (SAF integration); only accessible from
  `fagmodul/api` and `fagmodul/prefill`.
- `gcp/` — Google Cloud Storage integration.
- `utils/` — generic helpers (JSON mapping via `mapJsonToAny`/`toJson`, etc.), no dependencies
  on other layers.

Domain models for SEDs, BUCs, and PDL (person) data are **not** defined in this repo — they
come from NAV's shared libraries `ep-eux`, `ep-personoppslag`, `ep-kodeverk`, `ep-metrics`,
`ep-logging` (declared as versioned dependencies in `build.gradle`). When looking for a type
like `SED`, `Buc`, `BucType`, `SedType`, or `Ident`, check these external library packages
(e.g. `no.nav.eessi.pensjon.eux.model.*`, `no.nav.eessi.pensjon.personoppslag.pdl.model.*`)
rather than assuming they live in this codebase.

### Layering rules (enforced by ArchUnit in `ArchitectureTest.kt`)

- `api.geo`, `api.pensjon`, and `fagmodul.api` (frontend controllers) may not be depended on
  by any other layer — they are terminal/entry-point layers.
- `fagmodul.config` may not be depended on by any other layer.
- `fagmodul.prefill` may only be accessed by `fagmodul.api` and `api.pensjon`.
- `fagmodul.pesys` may only be accessed by `fagmodul.eux`.
- `vedlegg` may only be accessed by `fagmodul.api` and `fagmodul.prefill`.
- `fagmodul.eux` may only be accessed by `fagmodul.api`, `fagmodul.pesys`, `fagmodul.prefill`,
  `api.person`.
- `services.pensjonsinformasjon` may only be accessed by `api.pensjon`, `fagmodul.prefill`,
  `fagmodul.api`, `api.person`.
- No circular package dependencies are allowed anywhere in `no.nav.eessi.pensjon`.

## Conventions

- Classes named `*Controller` must be annotated `@RestController`; controllers must not call
  other `@RestController` classes; public methods on controllers must be annotated with
  `@RequestMapping`/`@GetMapping` (etc.) and must not access instance state directly.
- Spring-managed components (`@Service`, `@Component`, etc.) must not have mutable instance
  fields — inject dependencies via constructor `val`/`private val`, not mutable `var`
  (exceptions exist for classes matching `*STSService|Template|Config|GcpStorageService` which
  use setter injection, and `MetricsHelper.Metric` fields).
- Test classes (`*Test`/`*Tests`) must assert, not log — don't add `LoggerFactory`/slf4j calls
  in tests (outside the `logging` package) since log output isn't checked by tests.
- Test classes must not use inheritance from other test/support classes.
- Services commonly wire in `MetricsHelper` (optional, `@Autowired(required = false)`,
  defaulting to `MetricsHelper.ForTest()`) and define named `MetricsHelper.Metric` instances in
  an `init {}` block to time/measure operations — follow this pattern for new EUX/prefill
  operations that should be monitored.
- JSON (de)serialization uses the project's own `mapJsonToAny`/`toJson` helpers in
  `no.nav.eessi.pensjon.utils`, not raw Jackson `ObjectMapper` calls, for consistency.
