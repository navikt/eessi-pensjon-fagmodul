package no.nav.eessi.eessifagmodul.controllers

//@CrossOrigin
//@RestController
//class DiagnosticsController {
//
//    @Value("\${app.name}")
//    lateinit var appName: String
//
//    @Value("\${app.version}")
//    private lateinit var appVersion: String
//
//    private val logger: Logger by lazy { LoggerFactory.getLogger(DiagnosticsController::class.java) }
//
//    @GetMapping("/ping")
//    fun ping(): ResponseEntity<Unit> {
//        return ResponseEntity.ok().build()
//    }

//    @GetMapping("/internal/selftest")
//    fun selftest(): SelftestResult {
//        logger.debug("Selftest passed")
//        return SelftestResult(name = appName, version = appVersion, aggregateResult = 0, checks = null)
//    }

//    @GetMapping("/internal/isalive")
//    fun isalive(): ResponseEntity<String> {
//        return ResponseEntity.ok("Is alive")
//    }
//
//    @GetMapping("/internal/isready")
//    fun isready(): ResponseEntity<String> {
//        return ResponseEntity.ok("Is ready")
//    }
//
//}

//data class SelftestResult(
//        val name: String,
//        val version: String,
//        val timestamp: Instant = Instant.now(),
//        val aggregateResult: Int,
//        val checks: List<Check>?
//)
//
//data class Check(
//        val endpoint: String,
//        val description: String,
//        val errorMessage: String,
//        val result: Int
//)
