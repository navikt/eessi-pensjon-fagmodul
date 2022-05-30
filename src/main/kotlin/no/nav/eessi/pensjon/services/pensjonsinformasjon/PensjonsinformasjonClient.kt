package no.nav.eessi.pensjon.services.pensjonsinformasjon


//@Component
//@CacheConfig(cacheNames = ["PensjonsinformasjonClient"])
//class PensjonsinformasjonClient(
//    private val pensjoninformasjonRestTemplate: RestTemplate,
////    private val pensjonRequestBuilder: PensjonRequestBuilder,
//    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {
//
//    companion object {
//        private val logger = LoggerFactory.getLogger(PensjonsinformasjonClient::class.java)
//
//        fun finnSak(sakId: String, pendata: Pensjonsinformasjon): V1Sak? {
//            logger.info("Søker brukersSakerListe etter sakId: $sakId")
//            val v1saklist = pendata.brukersSakerListe.brukersSakerListe
//            return v1saklist.firstOrNull { sak -> "${sak.sakId}" == sakId  }
//        }
//    }
//
//    private lateinit var pensjoninformasjonHentKunSakType: MetricsHelper.Metric
//    private lateinit var pensjoninformasjonHentAltPaaIdent: MetricsHelper.Metric
//    private lateinit var pensjoninformasjonAltPaaVedtak: MetricsHelper.Metric
//    private lateinit var pensjoninformasjonHentAltPaaIdentRequester: MetricsHelper.Metric
//    private lateinit var pensjoninformasjonAltPaaVedtakRequester: MetricsHelper.Metric
//
//    @PostConstruct
//    fun initMetrics() {
//        pensjoninformasjonHentKunSakType = metricsHelper.init("PensjoninformasjonHentKunSakType")
//        pensjoninformasjonHentAltPaaIdent = metricsHelper.init("PensjoninformasjonHentAltPaaIdent")
//        pensjoninformasjonAltPaaVedtak = metricsHelper.init("PensjoninformasjonAltPaaVedtak")
//        pensjoninformasjonHentAltPaaIdentRequester = metricsHelper.init("PensjoninformasjonHentAltPaaIdentRequester")
//        pensjoninformasjonAltPaaVedtakRequester = metricsHelper.init("PensjoninformasjonAltPaaVedtakRequester")
//    }
//
//    fun hentKunSakType(sakId: String, aktoerid: String): Pensjontype {
//            val sak = finnSak(sakId, hentAltPaaAktoerId(aktoerid)) ?: return Pensjontype(sakId, "")
//            return Pensjontype(sakId, sak.sakType)
//    }
//
//    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
//    fun hentAltPaaAktoerId(aktoerId: String): Pensjonsinformasjon {
//        require(aktoerId.isNotBlank()) { "AktoerId kan ikke være blank/tom"}
//
//        return pensjoninformasjonHentAltPaaIdent.measure {
//
//            val requestBody = pensjonRequestBuilder.requestBodyForSakslisteFromAString()
//
//            logger.debug("Requestbody:\n$requestBody")
//            logger.info("Henter pensjonsinformasjon for aktor: $aktoerId")
//
//            val xmlResponse = doRequest("/aktor/", aktoerId, requestBody, pensjoninformasjonHentAltPaaIdentRequester)
//            transform(xmlResponse)
//        }
//    }
//
//
//
//    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
//    fun hentAltPaaVedtak(vedtaksId: String): Pensjonsinformasjon {
//
//        return pensjoninformasjonAltPaaVedtak.measure {
//
//            val requestBody = pensjonRequestBuilder.requestBodyForVedtakFromAString()
//            logger.debug("Requestbody:\n$requestBody")
//            logger.info("Henter pensjonsinformasjon for vedtaksid: $vedtaksId")
//
//            val xmlResponse = doRequest("/vedtak", vedtaksId, requestBody, pensjoninformasjonAltPaaVedtakRequester)
//
//            logger.debug("**".repeat(50) + "\n"
//                + "XML Response: $xmlResponse\n"
//                + "**".repeat(50))
//
//            transform(xmlResponse)
//        }
//    }
//
//    fun hentKravDatoFraAktor(aktorId: String, saksId: String, kravId: String) : String? {
//        val pensjonSak = hentAltPaaAktoerId(aktorId)
//        return hentKravFraKravHistorikk(saksId, pensjonSak, kravId)
//    }
//
//    private fun hentKravFraKravHistorikk(saksId: String, pensjonSak: Pensjonsinformasjon, kravId: String ): String? {
//        val sak = finnSak(saksId, pensjonSak) ?: return null
//
//        val kravHistorikk = sak.kravHistorikkListe?.kravHistorikkListe?.filter { krav -> krav.kravId == kravId }
//
//        if (kravHistorikk == null) {
//            logger.warn("Kravhistorikk har ingen krav")
//            throw PensjoninformasjonException("Mangler kravistorikk")
//        } else if (kravHistorikk.size > 1) {
//            logger.warn("Det forventes kun et krav for kravId: $kravId, men Kravhistorikk har ${kravHistorikk.size}  krav")
//            throw PensjoninformasjonException("KravHistorikkListe med har for mange krav")
//        }
//
//        return kravHistorikk[0]?.mottattDato!!.simpleFormat()
//    }
//
//
//    //transform xmlString til Pensjoninformasjon object
//    fun transform(xmlString: String) : Pensjonsinformasjon {
//        return try {
//
//            val context = JAXBContext.newInstance(Pensjonsinformasjon::class.java)
//            val unmarshaller = context.createUnmarshaller()
//
//            logger.debug("Pensjonsinformasjon xml: $xmlString")
//            val res = unmarshaller.unmarshal(StreamSource(StringReader(xmlString)), Pensjonsinformasjon::class.java)
//
//            res.value as Pensjonsinformasjon
//
//        } catch (ex: Exception) {
//            logger.error("Feiler med xml transformering til Pensjoninformasjon")
//            throw PensjoninformasjonProcessingException("Feiler med xml transformering til Pensjoninformasjon: ${ex.message}")
//        }
//    }
//
//    @Throws(PensjoninformasjonException::class, HttpServerErrorException::class, HttpClientErrorException::class)
//    private fun doRequest(path: String, id: String, requestBody: String, metric: MetricsHelper.Metric): String {
//
//        val headers = HttpHeaders()
//        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
//        val requestEntity = HttpEntity(requestBody, headers)
//
//        val uriBuilder = UriComponentsBuilder.fromPath(path).pathSegment(id)
//
//        return metric.measure {
//            return@measure try {
//                val responseEntity = pensjoninformasjonRestTemplate.exchange(
//                        uriBuilder.toUriString(),
//                        HttpMethod.POST,
//                        requestEntity,
//                        String::class.java)
//
//                responseEntity.body!!
//
//            } catch (hsee: HttpServerErrorException) {
//                val errorBody = hsee.responseBodyAsString
//                logger.error("PensjoninformasjonService feiler med HttpServerError body: $errorBody", hsee)
//                throw PensjoninformasjonException("PensjoninformasjonService feiler med innhenting av pensjoninformasjon fra PESYS, prøv igjen om litt")
//            } catch (hcee: HttpClientErrorException) {
//                val errorBody = hcee.responseBodyAsString
//                logger.error("PensjoninformasjonService feiler med HttpClientError body: $errorBody", hcee)
//                throw PensjoninformasjonException("PensjoninformasjonService feiler med innhenting av pensjoninformasjon fra PESYS, prøv igjen om litt")
//            } catch (ex: Exception) {
//                logger.error("PensjoninformasjonService feiler med kontakt til PESYS pensjoninformajson, ${ex.message}", ex)
//                throw PensjoninformasjonException("PensjoninformasjonService feiler med ukjent feil mot PESYS. melding: ${ex.message}")
//            }
//        }
//    }
//}
//
//class PensjoninformasjonException(message: String) : ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message)
//
//class PensjoninformasjonProcessingException(message: String) : ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message)
