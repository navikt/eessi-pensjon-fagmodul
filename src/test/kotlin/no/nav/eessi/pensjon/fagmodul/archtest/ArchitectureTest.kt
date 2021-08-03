package no.nav.eessi.pensjon.fagmodul.archtest

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaAnnotation
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.EessiFagmodulApplication
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Scope
import org.springframework.web.bind.annotation.RestController

@Disabled
class ArchitectureTest {

    companion object {

        @JvmStatic
        private val root = EessiFagmodulApplication::class.qualifiedName!!.replace("." + EessiFagmodulApplication::class.simpleName, "")

        @JvmStatic
        lateinit var allClasses: JavaClasses

        @JvmStatic
        lateinit var productionClasses: JavaClasses

        @JvmStatic
        lateinit var testClasses: JavaClasses

        @BeforeAll @JvmStatic
        fun `extract classes`() {
            allClasses = ClassFileImporter().importPackages(root)

            assertTrue(allClasses.size > 250, "Sanity check on no. of classes to analyze")
            assertTrue(allClasses.size < 1100, "Sanity check on no. of classes to analyze")

            productionClasses = ClassFileImporter()
                    .withImportOption(ImportOption.DoNotIncludeTests())
                    .importPackages(root)

            assertTrue(productionClasses.size > 200, "Sanity check on no. of classes to analyze")
            assertTrue(productionClasses.size < 850, "Sanity check on no. of classes to analyze")

            testClasses = ClassFileImporter()
                    .withImportOption{ !ImportOption.DoNotIncludeTests().includes(it) }
                    .importPackages(root)

            assertTrue(testClasses.size > 100, "Sanity check on no. of classes to analyze")
        }
    }

    @Test
    fun `check architecture in detail`() {

        // components
        val health = "fagmodul.health"
        val bucSedApi = "fagmodul.api"
        val prefill = "fagmodul.prefill"
        val models = "fagmodul.models"
        val euxmodel = "fagmodul.sedmodel"
        val arkivApi = "api.arkiv"
        val geoApi = "api.geo"
        val personApi = "api.person"
        val pensjonApi = "api.pensjon"
        val pesys = "fagmodul.pesys"
        val config = "fagmodul.config"
        val metrics = "fagmodul.metrics"
        val euxService = "fagmodul.euxservice"
        val euxBasisModel = "fagmodul.euxBasisModel"
        val euxBucModel = "fagmodul.euxBucModel"
        val kodeverkService = "services.kodeverk"
        val geoService = "services.geo"
        val pensjonService = "services.pensjon"
        val integrationtest = "integrationtest"
        val utils = "utils"
        val vedlegg = "vedlegg"
        val security = "security"
        val personService = "personoppslag.personv3"
        val personDataLosning = "personoppslag.pdl"
        val innhentingService = "fagmodul.prefill"

        val packages: Map<String, String> = mapOf(
                "$root.fagmodul.health.." to health,
                "$root.api.arkiv.." to arkivApi,
                "$root.api.geo.." to geoApi,
                "$root.api.person.." to personApi,
                "$root.api.pensjon.." to pensjonApi,
                "$root.fagmodul.api.." to bucSedApi,
                "$root.fagmodul.prefill.." to prefill,
                "$root.fagmodul.models.." to models,
                "$root.eux.model.." to euxmodel,
                "$root.fagmodul.eux" to euxService,
                "$root.fagmodul.eux.basismodel.." to euxBasisModel,
                "$root.fagmodul.eux.bucmodel.." to euxBucModel,
                "$root.fagmodul.config.." to config,
                "$root.fagmodul.pesys.." to pesys,
                "$root.config.." to config,
                "$root.fagmodul.metrics.." to metrics,
                "$root.services.kodeverk" to kodeverkService,
                "$root.services.geo" to geoService,
                "$root.services.pensjonsinformasjon" to pensjonService,
                "$root.security.." to security,
                "$root.integrationtest.." to integrationtest,
                "$root.metrics.." to utils,
                "$root.utils.." to utils,
                "$root.logging.." to utils,
                "$root.vedlegg.." to vedlegg,
                "$root.personoppslag.pdl" to personDataLosning,
                "$root.personoppslag.personv3" to personService,
                "$root.fagmodul.prefill.." to innhentingService
            )

        // packages in each component - default is the package with the component name
        fun packagesFor(layer: String) = packages.entries.filter { it.value == layer }.map { it.key }.toTypedArray()

        // mentally replace the word "layer" with "component":
        layeredArchitecture()
                .layer(health).definedBy(*packagesFor(health))

                .layer(arkivApi).definedBy(*packagesFor(arkivApi))
                .layer(geoApi).definedBy(*packagesFor(geoApi))
                .layer(personApi).definedBy(*packagesFor(personApi))
                .layer(pensjonApi).definedBy(*packagesFor(pensjonApi))

                .layer(bucSedApi).definedBy(*packagesFor(bucSedApi))
                .layer(pesys).definedBy(*packagesFor(pesys))
                .layer(prefill).definedBy(*packagesFor(prefill))
                .layer(euxService).definedBy(*packagesFor(euxService))
                .layer(euxBasisModel).definedBy(*packagesFor(euxBasisModel))
                .layer(euxBucModel).definedBy(*packagesFor(euxBucModel))
                .layer(models).definedBy(*packagesFor(models))
                .layer(euxmodel).definedBy(*packagesFor(euxmodel))
                .layer(personDataLosning).definedBy(*packagesFor(personDataLosning))
                .layer(personService).definedBy(*packagesFor(personService))
                .layer(kodeverkService).definedBy(*packagesFor(kodeverkService))
                .layer(geoService).definedBy(*packagesFor(geoService))
                .layer(pensjonService).definedBy(*packagesFor(pensjonService))
                .layer(security).definedBy(*packagesFor(security))
                .layer(config).definedBy(*packagesFor(config))
                .layer(metrics).definedBy(*packagesFor(metrics))
                .layer(utils).definedBy(*packagesFor(utils))
                .layer(integrationtest).definedBy(*packagesFor(integrationtest))
                .layer(vedlegg).definedBy(*packagesFor(vedlegg))
                .layer(innhentingService).definedBy(*packagesFor(innhentingService))

                .whereLayer(health).mayNotBeAccessedByAnyLayer()
                .whereLayer(arkivApi).mayOnlyBeAccessedByLayers(metrics)
                .whereLayer(geoApi).mayNotBeAccessedByAnyLayer()
                .whereLayer(personApi).mayOnlyBeAccessedByLayers(metrics)
                .whereLayer(pensjonApi).mayOnlyBeAccessedByLayers(metrics)

                .whereLayer(pesys).mayOnlyBeAccessedByLayers(health, euxmodel, models, euxService)
                .whereLayer(bucSedApi).mayNotBeAccessedByAnyLayer()
                .whereLayer(prefill).mayOnlyBeAccessedByLayers(bucSedApi, integrationtest)
                .whereLayer(euxService).mayOnlyBeAccessedByLayers(health, bucSedApi, pesys, integrationtest, innhentingService)
                .whereLayer(euxBasisModel).mayOnlyBeAccessedByLayers(euxService, bucSedApi, pesys, integrationtest, euxmodel)
                .whereLayer(euxBucModel).mayOnlyBeAccessedByLayers(euxService, bucSedApi, pesys, integrationtest, euxmodel)
                .whereLayer(models).mayOnlyBeAccessedByLayers(prefill, euxService, euxBasisModel, euxBucModel, euxmodel, bucSedApi, pensjonApi, personApi, pesys, integrationtest)
                .whereLayer(euxmodel).mayOnlyBeAccessedByLayers(prefill, euxService, bucSedApi, models, integrationtest, pesys, pensjonService, euxBasisModel, euxBucModel)
                .whereLayer(personDataLosning).mayOnlyBeAccessedByLayers(config, health, personApi, bucSedApi, pensjonApi, prefill, models, integrationtest, innhentingService)
                .whereLayer(vedlegg).mayOnlyBeAccessedByLayers(integrationtest, innhentingService, bucSedApi)
                .whereLayer(geoService).mayOnlyBeAccessedByLayers(geoApi, prefill)
                .whereLayer(pensjonService).mayOnlyBeAccessedByLayers(health, pensjonApi, prefill, bucSedApi, personApi, integrationtest)

                .whereLayer(config).mayNotBeAccessedByAnyLayer()
                .whereLayer(metrics).mayOnlyBeAccessedByLayers(config, health, euxService, vedlegg)
                .whereLayer(security).mayOnlyBeAccessedByLayers(config,
                health,
                euxService,
                vedlegg,
                pensjonService,
                personDataLosning,
                personService,
                kodeverkService,
                pesys,
                integrationtest,
                prefill)
                .check(allClasses)
    }

    @Test
    fun `main layers check`() {
        val frontendAPI = "Frontend API"
        val fagmodulCore = "Fagmodul Core"
        val integrationtest = "Integration tests"
        val services = "Services"
        val support = "Support"
        val vedlegg ="Vedlegg"
        val personoppslag = "Personoppslag"
        val euxmodel = "euxmodel"
        layeredArchitecture()
                .layer(frontendAPI).definedBy("$root.api..")
                .layer(fagmodulCore).definedBy("$root.fagmodul..")
                .layer(integrationtest).definedBy("$root.integrationtest..")
                .layer(services).definedBy("$root.services..")
                .layer(personoppslag).definedBy("$root.personoppslag..")
                .layer(vedlegg).definedBy("$root.vedlegg..")
                .layer(euxmodel).definedBy("$root.eux.model..")
                .layer(support).definedBy(
                        "$root.metrics..",
                        "$root.security..",
                        "$root.config..",
                        "$root.logging..",
                        "$root.utils.."
                )
                .whereLayer(frontendAPI).mayNotBeAccessedByAnyLayer()
                .whereLayer(fagmodulCore).mayOnlyBeAccessedByLayers(
                        frontendAPI,
                        integrationtest,
                        services)
                .whereLayer(services).mayOnlyBeAccessedByLayers(
                        frontendAPI,
                        fagmodulCore,
                        integrationtest)
                .whereLayer(support).mayOnlyBeAccessedByLayers(
                        frontendAPI,
                        fagmodulCore,
                        services,
                        personoppslag,
                        vedlegg,
                        integrationtest,
                        euxmodel)
                .check(allClasses)
    }

    @Test
    fun `prefill structure test`() {
        val prefillRoot = "$root.prefill"
        val sedModel = "$root.sedModel"
        val prefillClasses = ClassFileImporter()
                .importPackages(prefillRoot, sedModel)

        layeredArchitecture()
                .layer("service").definedBy(prefillRoot)
                .layer("sed").definedBy("$prefillRoot.sed..")
                .layer("person").definedBy("$prefillRoot.person..")
                .layer("pen").definedBy("$prefillRoot.pen..")
                .layer("pdl").definedBy("$prefillRoot.pdl..")
                .layer("eessi").definedBy("$prefillRoot.eessi..")
                .layer("model").definedBy("$prefillRoot.model..")
                .whereLayer("service").mayNotBeAccessedByAnyLayer()
                .whereLayer("sed").mayOnlyBeAccessedByLayers("service")
                .whereLayer("person").mayOnlyBeAccessedByLayers("sed")
                .whereLayer("pen").mayOnlyBeAccessedByLayers("sed")
                .whereLayer("pdl").mayOnlyBeAccessedByLayers("sed")
                .whereLayer("eessi").mayOnlyBeAccessedByLayers("sed", "pdl")
                .check(prefillClasses)
    }
    @Test
    fun `no cycles on top level`() {
        slices()
                .matching("$root.(*)..")
                .should().beFreeOfCycles()
                .check(allClasses)
    }

    @Test
    @Disabled
    fun `no cycles on any level for production classes`() {
        slices()
                .matching("$root..(*)")
                .should().beFreeOfCycles()
                .check(productionClasses)
    }

    @Test
    fun `controllers should have RestController-annotation`() {
        classes().that()
                .haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(RestController::class.java)
                .check(allClasses)
    }

    @Test
    fun `controllers should not call each other`() {
        classes().that()
                .areAnnotatedWith(RestController::class.java)
                .should().onlyBeAccessed().byClassesThat().areNotAnnotatedWith(RestController::class.java)
                .because("Controllers should not call each other")
                .check(allClasses)
    }

    @Test
    fun `tests should assert, not log`() {
        noClasses().that().haveNameNotMatching(".*\\.logging\\..*") // we allow using slf4j in the logging-package
                .should().dependOnClassesThat().resideInAPackage("org.slf4j..")
                .because("Test should assert, not log; after you made your test the logs will not be checked")
                .check(testClasses)
    }

    @Test
    fun `Spring singleton components should not have mutable instance fields`() {

        class SpringStereotypeAnnotation:DescribedPredicate<JavaAnnotation>("Spring component annotation") {
            override fun apply(input: JavaAnnotation?) = input != null &&
                    (input.rawType.packageName.startsWith("org.springframework.stereotype") ||
                            input.rawType.isEquivalentTo(RestController::class.java))
        }

        val springStereotype = SpringStereotypeAnnotation()

        noMethods().that()
                .haveNameMatching("set[A-Z]+.*")
                .and().doNotHaveRawParameterTypes(MetricsHelper.Metric::class.java)
                .and().areDeclaredInClassesThat().areNotAnnotatedWith(Scope::class.java) // If scope is not singleton it might be ok
                .and().areDeclaredInClassesThat().haveNameNotMatching(".*(STSService|Template|Config)") // these use setter injection
                .should().beDeclaredInClassesThat().areAnnotatedWith(springStereotype)
                .because("Spring-components (usually singletons) must not have mutable instance fields " +
                        "as they can easily be misused and create 'race conditions'")
                .check(productionClasses)

        noFields().that()
                .areNotFinal()
                .and().doNotHaveRawType(MetricsHelper.Metric::class.java)
                .and().areDeclaredInClassesThat().areNotAnnotatedWith(Scope::class.java)// If scope is not singleton it might be ok
                .and().areDeclaredInClassesThat().haveNameNotMatching(".*(STSService|Template|Config)") // these use setter injection
                .should().beDeclaredInClassesThat().areAnnotatedWith(springStereotype)
                .because("Spring-components (usually singletons) must not have mutable instance fields " +
                        "as they can easily be misused and create 'race conditions'")
                .check(productionClasses)
    }

    @Test
    fun `No test classes should use inheritance`() {
        class TestSupportClasses:DescribedPredicate<JavaClass>("test support classes") {
            override fun apply(input: JavaClass?) = input != null &&
                    (!input.simpleName.endsWith("Test") &&
                            (!input.simpleName.endsWith("Tests")
                                    && input.name != "java.lang.Object"))
        }

        noClasses().that().haveSimpleNameEndingWith("Test").or().haveSimpleNameEndingWith("Tests")
                .should().beAssignableTo(TestSupportClasses())
                .because("it is hard to understand the logic of tests that inherit from other classes.")
                .check(testClasses)
    }
}
