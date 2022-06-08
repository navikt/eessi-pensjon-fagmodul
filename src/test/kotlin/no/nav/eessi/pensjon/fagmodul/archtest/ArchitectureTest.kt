package no.nav.eessi.pensjon.fagmodul.archtest

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaAnnotation
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaFieldAccess
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.EessiFagmodulApplication
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Scope
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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

            assertTrue(allClasses.size > 350, "Sanity check on no. of classes to analyze")
            assertTrue(allClasses.size < 1300, "Sanity check on no. of classes to analyze")

            productionClasses = ClassFileImporter()
                    .withImportOption(ImportOption.DoNotIncludeTests())
                    .withImportOption(ImportOption.DoNotIncludeJars())
                    .importPackages(root)

            assertTrue(productionClasses.size > 200, "Sanity check on no. of classes to analyze")
            assertTrue(productionClasses.size < 850, "Sanity check on no. of classes to analyze")

            testClasses = ClassFileImporter()
                    .withImportOption{ !ImportOption.DoNotIncludeTests().includes(it) }
                    .importPackages(root)

            //assertEquals(625, testClasses.size, "Sanity check on no. of classes to analyze")
            assertTrue(testClasses.size > 500, "Sanity check on no. of classes to analyze")
        }
    }

    @Test
    fun `check architecture in detail`() {

        // components
        val bucSedApi         = "fagmodul api"
        val prefill           = "fagmodul prefill"
        val models            = "fagmodul models"
        val pesys             = "fagmodul pesys"
        val euxService        = "fagmodul euxservice"
        val euxBasisModel     = "fagmodul euxBasisModel"
        val euxBucModel       = "fagmodul euxBucModel"
        val config            = "fagmodul config"
        val innhentingService = "fagmodul prefill"
        val geoApi            = "api geo"
        val personApi         = "api person"
        val pensjonApi        = "api pensjon"
        val kodeverkService   = "services kodeverk"
        val geoService        = "services geo"
        val pensjonService    = "services pensjon"
        val statistikk    = "services statistikk"
        val utils             = "utils"
        val vedlegg           = "vedlegg"
        val packages: Map<String, String> = mapOf(
                "$root.fagmodul.api.." to bucSedApi,
                "$root.fagmodul.prefill.." to prefill,
                "$root.fagmodul.models.." to models,
                "$root.fagmodul.eux" to euxService,
                "$root.fagmodul.eux.basismodel.." to euxBasisModel,
                "$root.fagmodul.eux.bucmodel.." to euxBucModel,
                "$root.fagmodul.config.." to config,
                "$root.fagmodul.pesys.." to pesys,
                "$root.fagmodul.prefill.." to innhentingService,
                "$root.api.geo.." to geoApi,
                "$root.api.person.." to personApi,
                "$root.api.pensjon.." to pensjonApi,
                "$root.config.." to config,
                "$root.services.kodeverk" to kodeverkService,
                "$root.services.geo" to geoService,
                "$root.services.statistikk" to statistikk,
                "$root.services.pensjonsinformasjon" to pensjonService,
                "$root.metrics.." to utils,
                "$root.utils.." to utils,
                "$root.logging.." to utils,
                "$root.vedlegg.." to vedlegg,
            )

        // packages in each component - default is the package with the component name
        fun packagesFor(layer: String) = packages.entries.filter { it.value == layer }.map { it.key }.toTypedArray()

        // mentally replace the word "layer" with "component":
        layeredArchitecture()
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
                .layer(kodeverkService).definedBy(*packagesFor(kodeverkService))
                .layer(geoService).definedBy(*packagesFor(geoService))
                .layer(pensjonService).definedBy(*packagesFor(pensjonService))
                .layer(config).definedBy(*packagesFor(config))
                .layer(utils).definedBy(*packagesFor(utils))
                .layer(vedlegg).definedBy(*packagesFor(vedlegg))
                .layer(innhentingService).definedBy(*packagesFor(innhentingService))
                .layer(statistikk).definedBy(*packagesFor(statistikk))
                .whereLayer(geoApi).mayNotBeAccessedByAnyLayer()

                .whereLayer(pesys).mayOnlyBeAccessedByLayers(models, euxService)
                .whereLayer(bucSedApi).mayNotBeAccessedByAnyLayer()
                .whereLayer(prefill).mayOnlyBeAccessedByLayers(bucSedApi)
                .whereLayer(euxService).mayOnlyBeAccessedByLayers(bucSedApi, pesys, innhentingService, personApi)
                .whereLayer(euxBasisModel).mayOnlyBeAccessedByLayers(euxService, bucSedApi, pesys)
                .whereLayer(euxBucModel).mayOnlyBeAccessedByLayers(euxService, bucSedApi, pesys, personApi)
                .whereLayer(models).mayOnlyBeAccessedByLayers(prefill, euxService, euxBasisModel, euxBucModel, bucSedApi, pensjonApi, personApi, pesys)
                .whereLayer(vedlegg).mayOnlyBeAccessedByLayers(innhentingService, bucSedApi)
                .whereLayer(geoService).mayOnlyBeAccessedByLayers(geoApi, prefill)
                .whereLayer(pensjonService).mayOnlyBeAccessedByLayers(pensjonApi, prefill, bucSedApi, personApi)

                .whereLayer(config).mayNotBeAccessedByAnyLayer()
                .check(productionClasses)
    }

    @Test
    fun `main layers check`() {
        val frontendAPI = "Frontend API"
        val fagmodulCore = "Fagmodul Core"
        val services = "Services"
        val support = "Support"
        val vedlegg ="Vedlegg"
        val personoppslag = "Personoppslag"
        val euxmodel = "euxmodel"
        val models = "models"
        layeredArchitecture()
                .layer(frontendAPI).definedBy(      "$root.api..")
                .layer(fagmodulCore).definedBy(     "$root.fagmodul..")
                .layer(models).definedBy(           "$root.fagmodul.models..")
                .layer(services).definedBy(         "$root.services..")
                .layer(personoppslag).definedBy(    "$root.personoppslag..")
                .layer(vedlegg).definedBy(          "$root.vedlegg..")
                .layer(euxmodel).definedBy(         "$root.eux.model..")
                .layer(support).definedBy(
                        "$root.metrics..",
                        "$root.config..",
                        "$root.logging..",
                        "$root.utils.."
                )
                .whereLayer(frontendAPI).mayNotBeAccessedByAnyLayer()
                .whereLayer(fagmodulCore).mayOnlyBeAccessedByLayers(
                        frontendAPI,
                        services)
                .whereLayer(models).mayOnlyBeAccessedByLayers(
                        support,
                        fagmodulCore,
                        frontendAPI,
                        services,
                        vedlegg,
                        euxmodel)
                .whereLayer(services).mayOnlyBeAccessedByLayers(
                        frontendAPI,
                        fagmodulCore,
                        vedlegg)
                .whereLayer(support).mayOnlyBeAccessedByLayers(
                        frontendAPI,
                        fagmodulCore,
                        services,
                        personoppslag,
                        vedlegg,
                        euxmodel,
                        models)
                .withOptionalLayers(true)
                .check(productionClasses)
    }

    @Test
    fun `no cycles on top level`() {
        slices()
                .matching("$root.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses)
    }

    @Test
    fun `no cycles on any level for production classes`() {
        slices()
                .matching("$root.(*)..")
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
    fun `RestControllers should not call each other`() {
        classes()
            .that().areAnnotatedWith(RestController::class.java)
            .should().onlyHaveDependentClassesThat().areNotAnnotatedWith(RestController::class.java)
            .because("RestControllers should not call each other")
            .check(allClasses)
    }

    @Test
    fun `Methods in RestControllers should only call be fo get, post, patch and delete`() {
        val rule = methods()
            .that().arePublic()
            .and(dontAccessInstanceMethods())
            .and(dontAccessMemberVariables())
            .and().areDeclaredInClassesThat()
            .resideInAPackage("..no.nav.eessi.pensjon.fagmodul.api..")
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(RequestMapping::class.java)

        rule.check(productionClasses)

    }
    private fun dontAccessMemberVariables(): DescribedPredicate<JavaMethod?>? {
        return object : DescribedPredicate<JavaMethod?>("don't access member variables") {
            override fun apply(input: JavaMethod?): Boolean {
                return !input!!.fieldAccesses.stream().filter { access: JavaFieldAccess ->
                    val optionalField = access.target.resolveField()
                    if (!optionalField.isPresent) {
                        return@filter false
                    }
                    !optionalField.get().modifiers
                        .contains(JavaModifier.STATIC)
                }
                    .findAny().isPresent
            }
        }
    }

    private fun dontAccessInstanceMethods(): DescribedPredicate<JavaMethod?>? {
        return object : DescribedPredicate<JavaMethod?>("don't access instance methods") {
            override fun apply(input: JavaMethod?): Boolean {
                return !input!!.getCallsFromSelf().stream().filter { access ->
                    val targets: Set<JavaCodeUnit> = access.target.resolve()
                    if (targets.isEmpty()) {
                        return@filter false
                    }
                    val target = targets.stream().findAny().get()
                    target.owner == input.owner && !target.modifiers.contains(JavaModifier.STATIC)
                }
                    .findAny().isPresent
            }
        }
    }

    @Test
    fun `tests should assert, not log`() {
        noClasses().that().haveNameNotMatching(".*\\.logging\\..*") // we allow using slf4j in the logging-package
                .should().dependOnClassesThat().resideInAPackage("org.slf4j.LoggerFactory")
                .because("Test should assert, not log; after you made your test the logs will not be checked")
                .check(testClasses)
    }


    @Test
    fun `Spring singleton components should not have mutable instance fields`() {

        class SpringStereotypeAnnotation:DescribedPredicate<JavaAnnotation<*>>("Spring component annotation") {
            override fun apply(input: JavaAnnotation<*>?) = input != null &&
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
