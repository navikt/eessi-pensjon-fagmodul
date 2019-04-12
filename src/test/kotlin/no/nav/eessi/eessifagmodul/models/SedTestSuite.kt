package no.nav.eessi.eessifagmodul.models

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
        SedTest::class,
        SedP2000Test::class,
        SedP2100Test::class,
        SedP2200Test::class,
        SedP3000noTest::class,
        SedP4000Test::class,
        SedP5000Test::class,
        SedP6000Test::class,
        SedP7000Test::class,
        SedP8000Test::class)
class SedTestSuite
