package no.nav.eessi.eessifagmodul.services.eux

import com.google.common.base.Preconditions
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRef
import no.nav.eessi.eessifagmodul.utils.typeRefs
import no.nav.eessi.eessifagmodul.metrics.getCounter
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Buc
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.BucAndSedView
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.ParticipantsItem
import no.nav.eessi.eessifagmodul.services.saf.SafService
import no.nav.eessi.eessifagmodul.services.saf.VariantFormat
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Description
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.io.IOException
import java.util.*
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.LinkedMultiValueMap


@Service
@Description("Service class for EuxBasis - EuxCpiServiceController.java")
class EuxService(private val euxOidcRestTemplate: RestTemplate,
                 val safService: SafService) {

    private val logger = LoggerFactory.getLogger(EuxService::class.java)

    // Nye API kall er er fra 23.01.19
    // https://eux-app.nais.preprod.local/swagger-ui.html#/eux-cpi-service-controller/


    //Oppretter ny RINA sak(type) og en ny Sed
    @Throws(EuxServerException::class, RinaCasenrIkkeMottattException::class)
    fun opprettBucSed(navSED: SED, bucType: String, mottakerid: String, fagSaknr: String): BucSedResponse {
        Preconditions.checkArgument(mottakerid.contains(":"), "format for mottaker er NN:ID")

        val path = "/buc/sed"
        val builder = UriComponentsBuilder.fromPath(path)
                .queryParam("BucType", bucType)
                .queryParam("MottakerId", mottakerid)
                .queryParam("FagSakNummer", fagSaknr)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val httpEntity = HttpEntity(navSED.toJson(), headers)

        try {
            logger.info("Prøver å kontakte EUX /${builder.toUriString()}")
            val response = euxOidcRestTemplate.exchange(builder.toUriString(),
                    HttpMethod.POST,
                    httpEntity,
                    String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                getCounter("OPPRETTBUCOGSEDOK").increment()
                return mapJsonToAny(response.body!!, typeRefs())
            } else {
                throw RinaCasenrIkkeMottattException("Ikke mottatt RINA casenr, feiler ved opprettelse av BUC og SED")
            }
        } catch (rx: RinaCasenrIkkeMottattException) {
            logger.error(rx.message)
            getCounter("OPPRETTBUCOGSEDFEIL").increment()
            throw RinaCasenrIkkeMottattException(rx.message)
        } catch (sx: HttpServerErrorException) {
            logger.error(sx.message)
            getCounter("OPPRETTBUCOGSEDFEIL").increment()
            throw EuxServerException("Feiler med kontakt med EUX/Rina.")
        } catch (ex: Exception) {
            logger.error(ex.message)
            getCounter("OPPRETTBUCOGSEDFEIL").increment()
            throw EuxServerException("Feiler ved kontakt mot EUX")
        }
    }


    //ny SED på ekisterende type
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSedOnBuc(navSED: SED, euxCaseId: String): BucSedResponse {
        val path = "/buc/{RinaSakId}/sed"

        val uriParams = mapOf("RinaSakId" to euxCaseId)
        val builder = UriComponentsBuilder.fromUriString(path)
                .queryParam("KorrelasjonsId", UUID.randomUUID().toString())
                .buildAndExpand(uriParams)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val httpEntity = HttpEntity(navSED.toJson(), headers)

        val response: ResponseEntity<String>
        try {
            logger.info("Prøver å kontakte EUX /${builder.toUriString()}")
            response = euxOidcRestTemplate.exchange(builder.toUriString(),
                    HttpMethod.POST,
                    httpEntity,
                    String::class.java)
        } catch (ax: HttpServerErrorException) {
            logger.error(ax.message, ax)
            getCounter("OPPRETTBUCOGSEDFEIL").increment()
            throw ax
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            getCounter("OPPRETTBUCOGSEDFEIL").increment()
            throw EuxGenericServerException("Feiler ved kontakt mot EUX")
        }
        if (!response.statusCode.is2xxSuccessful) {
            logger.error("${response.statusCode} Feiler med å legge til SED på en ekisterende BUC")
            getCounter("OPPRETTBUCOGSEDFEIL").increment()
            throw SedDokumentIkkeOpprettetException("Feiler med å legge til SED på en ekisterende BUC")
        }
        getCounter("OPPRETTBUCOGSEDOK").increment()
        return BucSedResponse(euxCaseId, response.body!!)
    }

    //henter ut sed fra rina med bucid og documentid
    fun getSedOnBuc(euxCaseId: String, sedType: String?): List<SED> {
        val docid = getBucUtils(euxCaseId).getDocuments()

        val sedlist = mutableListOf<SED>()
        docid.forEach {
            if (sedType != null && sedType == it.type) {
                it.id?.let { id ->
                    sedlist.add(getSedOnBucByDocumentId(euxCaseId, id))
                }
            } else {
                it.id?.let { id ->
                    sedlist.add(getSedOnBucByDocumentId(euxCaseId, id))
                }
            }
        }
        return sedlist
    }

    //henter ut sed fra rina med bucid og documentid
    @Throws(EuxServerException::class, SedDokumentIkkeLestException::class)
    fun getSedOnBucByDocumentId(euxCaseId: String, documentId: String): SED {
        val path = "/buc/{RinaSakId}/sed/{DokumentId}"
        val uriParams = mapOf("RinaSakId" to euxCaseId, "DokumentId" to documentId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)

        logger.info("Prøver å kontakte EUX /${builder.toUriString()}")
        try {
            val response = euxOidcRestTemplate.exchange(builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    String::class.java)
            val jsonsed = response.body ?: throw SedDokumentIkkeLestException("Feiler ved lesing av navSED, feiler ved uthenting av SED")
            val navsed = mapJsonToAny(jsonsed, typeRefs<SED>())
            getCounter("HENTSEDOK").increment()
            return navsed
        } catch (rx: SedDokumentIkkeLestException) {
            logger.error(rx.message)
            getCounter("HENTSEDFEIL").increment()
            throw SedDokumentIkkeLestException(rx.message)
        } catch (ex: Exception) {
            logger.error(ex.message)
            getCounter("HENTSEDFEIL").increment()
            throw EuxServerException("Feiler ved kontakt mot EUX")
        }

    }

    //val benytt denne for å hente ut PESYS sakid (P2000,P2100,P2200,P6000)
    fun hentPESYSsakIdFraRinaSED(euxCaseId: String, documentId: String): String {
        val na = "N/A"

        try {
            val navsed = getSedOnBucByDocumentId(euxCaseId, documentId)

            val eessisak = navsed.nav?.eessisak?.get(0)

            val instnavn = eessisak?.institusjonsnavn ?: na
            if (instnavn.contains("NO")) {
                navsed.nav?.eessisak?.first()?.saksnummer?.let { return it }
            }

        } catch (ex: Exception) {
            logger.warn("Klarte ikke å hente inn SED dokumenter for å lese inn saksnr!")
        }
        return na
    }

    //henter ut bucdata fra valgt type/euxCaseId
    @Throws(BucIkkeMottattException::class, EuxServerException::class)
    fun getBuc(euxCaseId: String): Buc {
        logger.info("har euxCaseId verdi: $euxCaseId")

        val path = "/buc/{RinaSakId}"
        val uriParams = mapOf("RinaSakId" to euxCaseId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)

        try {
            logger.info("Prøver å kontakte EUX /${builder.toUriString()}")

            val response = euxOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    String::class.java)
            if (response.statusCode.is2xxSuccessful) {
                val jsonbuc = response.body!!
                getCounter("HENTBUCOK").increment()
                return mapJsonToAny(jsonbuc, typeRefs())
            } else {
                throw BucIkkeMottattException("Ikke mottatt Buc, feiler ved uthenting av Buc")
            }
        } catch (rx: BucIkkeMottattException) {
            logger.error(rx.message)
            getCounter("HENTBUCFEIL").increment()
            throw BucIkkeMottattException(rx.message)
        } catch (ex: Exception) {
            logger.error(ex.message)
            getCounter("HENTBUCFEIL").increment()
            throw EuxServerException("Feiler ved kontakt mot EUX")
        }

    }

    //henter ut BucUtils med valgt Buc for diverse uthentinger..
    fun getBucUtils(euxCaseId: String): BucUtils {
        logger.info("Prøver å hente ut en BucUtils for type $euxCaseId")
        return BucUtils(getBuc(euxCaseId))
    }

    /**
     * sletter et SED doument på RINA.
     * @param euxCaseId  er iden til den aktuelle Buc/Rina sak
     * @param documentId er iden til det unike dokuement/Sed som skal slettes.
     * true hvis alt ok, og sed slettt. Exception error hvis feil.
     */
    @Throws(SedIkkeSlettetException::class, EuxServerException::class)
    fun deleteDocumentById(euxCaseId: String, documentId: String): Boolean {
        val path = "/buc/{RinaSakId}/sed/{DokumentId}"
        val uriParams = mapOf("RinaSakId" to euxCaseId, "DokumentId" to documentId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)

        try {
            logger.info("Prøver å kontakte EUX /${builder.toUriString()}")

            val response = euxOidcRestTemplate.exchange(builder.toUriString(),
                    HttpMethod.DELETE,
                    null,
                    String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                logger.info("Slettet SED document OK")
                getCounter("SLETTSEDOK").increment()
                return true
            } else {
                throw SedIkkeSlettetException("Feil, SED document ble ikke slettet")
            }
        } catch (sx: SedIkkeSlettetException) {
            logger.error(sx.message)
            getCounter("SLETTSEDFEIL").increment()
            throw SedIkkeSlettetException(sx.message)
        } catch (ex: Exception) {
            logger.error(ex.message)
            getCounter("SLETTSEDFEIL").increment()
            throw EuxServerException(ex.message)
        }

    }

    /**
     * prøver å sende et SED doument på RINA ut til eu/mottaker.
     * @param euxCaseId  er iden til den aktuelle Buc/Rina sak
     * @param documentId er iden til det unike dokuement/Sed som skal sendes.
     * true hvis alt ok, og sed sendt. Exception error hvis feil.
     */
    @Throws(SedDokumentIkkeSendtException::class, EuxServerException::class)
    fun sendDocumentById(euxCaseId: String, documentId: String): Boolean {

        val path = "/buc/{RinaSakId}/sed/{DokumentId}/send"
        val uriParams = mapOf("RinaSakId" to euxCaseId, "DokumentId" to documentId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)

        return try {
            val response = euxOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.POST,
                    null,
                    String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                getCounter("SENDSEDOK").increment()
                true
            } else {
                throw SedDokumentIkkeSendtException("Feil, SED document ble ikke sendt")
            }
        } catch (sx: SedDokumentIkkeSendtException) {
            getCounter("SENDSEDFEIL").increment()
            throw sx
        } catch (ex: Exception) {
            getCounter("SENDSEDFEIL").increment()
            throw EuxServerException(ex.message)
        }

    }

    /**
     * List all institutions connected to RINA.
     */
    fun getInstitutions(bucType: String, landkode: String? = ""): List<String> {
        val builder = UriComponentsBuilder.fromPath("/institusjoner")
                .queryParam("BucType", bucType)
                .queryParam("LandKode", landkode ?: "")

        val httpEntity = HttpEntity("")
        val response = euxOidcRestTemplate.exchange(builder.toUriString(),
                HttpMethod.GET,
                httpEntity,
                typeRef<List<String>>())
        return response.body ?: listOf()
    }

    /**
     * Lister alle rinasaker på valgt fnr eller euxcaseid, eller bucType...
     * fnr er påkrved resten er fritt
     */
    fun getRinasaker(fnr: String): List<Rinasak> {
        return getRinasaker(fnr, null, null, null)
    }

    fun getRinasaker(fnr: String, euxCaseId: String?, bucType: String?, status: String?): List<Rinasak> {
        //https://eux-rina-api.nais.preprod.local/cpi/rinasaker?F%C3%B8dselsnummer=28064843062&Fornavn=a&Etternavn=b&F%C3%B8dsels%C3%A5r=2&RINASaksnummer=as&BuCType=p&Status=o

        val builder = UriComponentsBuilder.fromPath("/rinasaker")
                .queryParam("Fødselsnummer", fnr)
                .queryParam("RINASaksnummer", euxCaseId ?: "")
                .queryParam("BuCType", bucType ?: "")
                .queryParam("Status", status ?: "")
                .build()


        try {
            val response = euxOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    String::class.java)

            response.body?.let {
                return mapJsonToAny(it, typeRefs())
            } ?: return listOf()
        } catch (ia: IllegalArgumentException) {
            logger.error("mapJsonToAny exception ${ia.message}", ia)
            throw GenericUnprocessableEntity(ia.message!!)
        } catch (hx: HttpClientErrorException) {
            logger.warn("Rinasaker ClientException ${hx.message}", hx)
            throw hx
        } catch (sx: HttpServerErrorException) {
            logger.error("Rinasaker ClientException ${sx.message}", sx)
            throw sx
        } catch (io: ResourceAccessException) {
            logger.error("IO error fagmodul  ${io.message}", io)
            throw IOException(io.message, io)
        } catch (ex: Exception) {
            logger.error("Annen uspesefikk feil oppstod mellom fagmodul og eux ${ex.message}", ex)
            throw ex
        }

    }

    fun getBucAndSedView(fnr: String, aktoerid: String, sakId: String?, euxCaseId: String?, euxService: EuxService): List<BucAndSedView> {
        val startTime = System.currentTimeMillis()

        logger.debug("2 fant fnr.")

        logger.debug("3 henter rinasaker på valgt aktoerid: $aktoerid")

        val rinasaker = euxService.getRinasaker(fnr)

        logger.debug("4 hentet ut rinasaker på valgt borger, antall: ${rinasaker.size}")

        logger.debug("5 starter med å hente ut data for hver BUC i rinasaker")

        val list = mutableListOf<BucAndSedView>()

        rinasaker.forEach {
            val caseId = it.id ?: throw IkkeGyldigKallException("Feil er ikke gyldig caseId fra Rina(Rinasak)")
            list.add(createBucDetails(caseId, aktoerid, euxService))
        }
        logger.debug("9 ferdig returnerer list av BucAndSedView. Antall BUC: ${list.size}")

        val sortlist = list.asSequence().sortedByDescending { it.startDate }.toList()

        logger.debug("10. Sortert listen på startDate nyeste dato først")

        val endTime = System.currentTimeMillis()

        logger.debug("11 tiden tok ${endTime - startTime} ms.")

        return sortlist

    }

    //make single bucDetails form rina/list or return when create buc
    fun createBucDetails(euxCaseId: String, aktoerid: String, euxService: EuxService): BucAndSedView {

        val bucUtil = euxService.getBucUtils(euxCaseId)

        val institusjonlist = mutableListOf<InstitusjonItem>()
        var parts: List<ParticipantsItem>? = null
        try {
            parts = bucUtil.getParticipants()
            logger.debug(" Henter ut liste over deltagere på type")

            parts?.forEach {
                institusjonlist.add(
                        InstitusjonItem(
                                country = it.organisation?.countryCode ?: "",
                                institution = it.organisation?.id ?: "",
                                name = it.organisation?.name
                        )
                )
            }
        } catch (ex: Exception) {
            logger.debug("Ingen meldlemmer i BUC")
        }
        logger.debug(" oppretter bucogsedview")
        val bucAndSedView = BucAndSedView(
                type = bucUtil.getProcessDefinitionName()!!,
                creator = InstitusjonItem(
                        country = bucUtil.getCreator()?.organisation?.countryCode ?: "",
                        institution = bucUtil.getCreator()?.organisation?.id ?: "",
                        name = bucUtil.getCreator()?.name
                ),
                caseId = euxCaseId,
                sakType = "",
                startDate = bucUtil.getStartDateLong(),
                lastUpdate = bucUtil.getLastDateLong(),
                aktoerId = aktoerid,
                status = bucUtil.getBuc().status,
                institusjon = institusjonlist.toList(),
                seds = bucUtil.getAllDocuments()
        )
        logger.debug("8 legger bucogsedview til liste")

        return bucAndSedView
    }

    fun createBuc(bucType: String): String {

        val correlationId = UUID.randomUUID().toString()
        val builder = UriComponentsBuilder.fromPath("/buc")
                .queryParam("BuCType", bucType)
                .queryParam("KorrelasjonsId", correlationId)
                .build()


        logger.debug("Kontakter EUX for å prøve på opprette ny BUC med korrelasjonId: $correlationId")
        try {
            val response = euxOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.POST,
                    null,
                    String::class.java)

            response.body?.let {
                return it
            } ?: throw IkkeFunnetException("Fant ikke noen caseid")
        } catch (ia: IllegalArgumentException) {
            logger.error("noe feil? exception ${ia.message}", ia)
            throw GenericUnprocessableEntity(ia.message!!)
        } catch (hx: HttpClientErrorException) {
            logger.warn("Buc ClientException ${hx.message}", hx)
            throw hx
        } catch (sx: HttpServerErrorException) {
            logger.error("Buc ClientException ${sx.message}", sx)
            throw sx
        } catch (io: ResourceAccessException) {
            logger.error("IO error fagmodul  ${io.message}", io)
            throw IOException(io.message, io)
        } catch (ex: Exception) {
            logger.error("Annen uspesefikk feil oppstod mellom fagmodul og eux ${ex.message}", ex)
            throw ex
        }
    }

    fun putBucDeltager(euxCaseId: String, deltaker: String): Boolean {
        Preconditions.checkArgument(deltaker.contains(":"), "ikke korrekt formater deltager/Institusjooonner... ")

        val correlationId = UUID.randomUUID().toString()

        val builder = UriComponentsBuilder.fromPath("/buc/$euxCaseId/bucdeltakere")
                .queryParam("MottakerId", deltaker)
                .queryParam("KorrelasjonsId", correlationId)
                .build()


        logger.debug("Kontakter EUX for å legge til deltager: $deltaker med korrelasjonId: $correlationId på type: $euxCaseId")
         try {
            val response = euxOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.PUT,
                    null,
                    String::class.java)

           return response.statusCode == HttpStatus.OK
        } catch (ia: IllegalArgumentException) {
            logger.error("noe feil? exception ${ia.message}", ia)
            throw GenericUnprocessableEntity(ia.message!!)
        } catch (hx: HttpClientErrorException) {
            logger.warn("Deltager ClientException ${hx.message}", hx)
            throw hx
        } catch (sx: HttpServerErrorException) {
            logger.error("Deltager ServerException ${sx.message}", sx)
            throw sx
        } catch (io: ResourceAccessException) {
            logger.error("IO Error fagmodul  ${io.message}", io)
            throw IOException(io.message, io)
        } catch (ex: Exception) {
            logger.error("Annen uspesefikk feil oppstod mellom fagmodul og eux ${ex.message}", ex)
            throw ex
        }
    }

    fun leggTilVedleggPaaDokument(aktoerId: String,
                                  rinaSakId: String,
                                  rinaDokumentId: String,
                                  joarkJournalpostId: String,
                                  joarkDokumentInfoId : String,
                                  variantFormat: VariantFormat) {
        try {
            logger.info("Henter dokumentinnhold fra joark")
            val hentDokumentResponse = safService.hentDokumentInnhold(joarkJournalpostId, joarkDokumentInfoId, variantFormat)

            val body = LinkedMultiValueMap<String, Any>()
            body.add("File", ByteArrayResource(hentDokumentResponse.base64.toByteArray()))
            body.add("Filnavn", hentDokumentResponse.fileName)
            body.add("Filtype", hentDokumentResponse.contentType.split("/")[1])

            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA
            val httpEntity = HttpEntity(body, headers)

            val path = "/cpi/buc/$rinaSakId/sed/$rinaDokumentId/vedlegg"
            logger.info("Legger til vedlegg i rinaSakId: $rinaSakId rinaDokumentId: $rinaDokumentId")

            val response = euxOidcRestTemplate.exchange(
                    path,
                    HttpMethod.POST,
                    httpEntity,
                    String::class.java)

            if (!response.statusCode.is2xxSuccessful) {
                throw RuntimeException("En feil opppstod under tilknytning av vedlegg: ${response.statusCode}, ${response.body}")
            }
        } catch (ex: java.lang.Exception) {
            logger.error("En feil opppstod under tilknytning av vedlegg, $ex")
            throw ex
        }
    }

    //Legger en eller flere deltakere/institusjonItem inn i Rina. (Itererer for hver en)
    fun addDeltagerInstitutions(euxCaseId: String, mottaker: List<InstitusjonItem>) : Boolean {
        logger.debug("Prøver å legge til liste over nye InstitusjonItem til Rina ")
        try {
            mottaker.forEach {
                val mottakerItem = it.checkAndConvertInstituion()
                logger.debug("putter $mottaker på Rina buc $euxCaseId")
                putBucDeltager(euxCaseId, mottakerItem)
                //Kan fjernes: Sjekk opp med EUX når de legger in støtte for å legge til flere Deltakere.
                if (mottaker.size > 1) {
                    logger.debug("Prøver å sove litt etter å lagt til Deltaker til Rina: $mottakerItem")
                    Thread.sleep(3000)
                }
            }
            return true
        } catch (ex: Exception) {
            logger.error("Error legge til deltager/instutsjoner ved sed/Buc $euxCaseId", ex)
            throw ex
        }
    }

    //Henter ut Kravtype fra P15000
    fun hentYtelseKravtype(euxCaseId: String, documentId: String): Krav {
        val sed = getSedOnBucByDocumentId(euxCaseId, documentId)
        //validere om SED er vireklig en P15000
        if (SEDType.P15000.name ==  sed.sed) {
            return sed.nav?.krav ?: Krav()
        }
        throw SedDokumentIkkeGyldigException("SED ikke en P15000")
    }

    @Throws(EuxServerException::class)
    fun pingEux(): Boolean {

        val builder = UriComponentsBuilder.fromPath("/kodeverk")
            .queryParam("Kodeverk", "sedtyper")
            .build()

        return try {
            logger.debug("Ping eux-rina-api")
            euxOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    String::class.java)
            true
        } catch (ex: Exception) {
            logger.debug("Feiler ved ping, eux-rina-api")
            throw EuxServerException(ex.message)
        }
    }

    /**
     * Own impl. no list from eux that contains list of SED to a speific BUC
     * @param bucType
     */
    companion object {
        @JvmStatic
        fun initSedOnBuc(): Map<String, List<String>> {
            return mapOf(
                    "P_BUC_01" to listOf("P2000"),
                    "P_BUC_02" to listOf("P2100"),
                    "P_BUC_03" to listOf("P2200"),
                    "P_BUC_05" to listOf("P5000", "P6000", "P7000", "P8000", "P9000"),
                    "P_BUC_06" to listOf("P5000", "P6000", "P7000", "P10000"),
                    "P_BUC_10" to listOf("P15000")
            )
        }

        @JvmStatic
        fun getAvailableSedOnBuc(bucType: String?): List<String> {
            val map = initSedOnBuc()

            if (bucType.isNullOrEmpty()) {
                val set = mutableSetOf<String>()
                map["P_BUC_01"]?.let { set.addAll(it) }
                map["P_BUC_02"]?.let { set.addAll(it) }
                map["P_BUC_03"]?.let { set.addAll(it) }
                map["P_BUC_05"]?.let { set.addAll(it) }
                map["P_BUC_06"]?.let { set.addAll(it) }
                map["P_BUC_10"]?.let { set.addAll(it) }
                return set.toList()
            }
            return map[bucType].orEmpty()
        }
    }
}
