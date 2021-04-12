package no.nav.eessi.pensjon.eux.model

import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.SuiteDisplayName

@SuiteDisplayName("Sed Test Suite")
@SelectClasses(
        SedTest::class,
        SedP2000Test::class,
        SedP2100Test::class,
        SedP2200Test::class,
        SedP3000noTest::class,
        SedP4000fileTest::class,
        SedP5000Test::class,
        SedP6000Test::class,
        SedP7000Test::class,
        SedP8000Test::class,
        SedP9000Test::class,
        SedP10000Test::class,
        SedP15000Test::class,
        SedH120Test::class,
        SedH121Test::class,
        SedH070Test::class,
        SedX005Test::class
        )
class SedTestSuite
