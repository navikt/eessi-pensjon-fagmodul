package no.nav.eessi.pensjon.fagmodul.eux

import com.google.common.base.Preconditions
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Vedlegg
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.metrics.getCounter
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.sedmodel.Krav
import no.nav.eessi.pensjon.fagmodul.sedmodel.PinItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRef
import no.nav.eessi.pensjon.utils.typeRefs
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Description
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.io.IOException
import java.util.*
import java.nio.file.Paths
import org.springframework.http.ResponseEntity



@Service
@Description("Service class for EuxBasis - eux-cpi-service-controller")
class EuxService(private val euxOidcRestTemplate: RestTemplate) {

    // Vi trenger denne no arg konstruktøren for å kunne bruke @Spy med mockito
    constructor() : this(RestTemplate())

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
        val httpEntity = HttpEntity(navSED.toJsonSkipEmpty(), headers)

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
        //legger til navsed som json skipper nonemty felter dvs. null
        val httpEntity = HttpEntity(navSED.toJsonSkipEmpty(), headers)

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
        logger.info("Prøver å hente ut en BucUtils for type $euxCaseId")
        val docid = getBuc(euxCaseId).documents ?: throw NoSuchFieldException("Fant ikke DocumentsItem")

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
                .queryParam("BuCType", bucType)
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
    fun getRinasaker(fnr: String, fasitEnv: String): List<Rinasak> {
        logger.debug("Henter opp rinasaker på fnr")
        val rinasakfnr =  getRinasaker(fnr, null, null, null)

        //Veldig CT denne skal fjernes etter
        if (rinasakfnr.isEmpty() && "Q2" == fasitEnv.toUpperCase()  ) {
            logger.debug("Ingen rinasaker på fnr funnet, så henter opp rinasaker på buctype")
            return getRinasakerPaaBuctype()
        }
        return rinasakfnr
    }

    //For bruk i CT/Q2. Det vil komme en annen løsning for BUC på innkomende personer som må vises for saksbehandler
    fun getRinasakerPaaBuctype(): List<Rinasak> {
        val rinasaker = mutableListOf<Rinasak>()
        EuxService.initSedOnBuc().keys.forEach{ buctype ->
            logger.debug("Hnter opp rinasaker på buctype: $buctype")
            val result = getRinasaker("","",buctype, "open")
            logger.debug("Antall rinasaker : ${result.size}")
            result.toCollection(rinasaker)
        }
        logger.debug("Totalt antall rinasaker på buctyper: ${rinasaker.size}")
        return rinasaker.asSequence().sortedByDescending{ it.id  }.toList()
    }

    fun getRinasaker(fnr: String, euxCaseId: String?, bucType: String?, status: String?): List<Rinasak> {

        val builder = UriComponentsBuilder.fromPath("/rinasaker")
                .queryParam("fødselsnummer", fnr)
                .queryParam("rinasaksnummer", euxCaseId ?: "")
                .queryParam("buctype", bucType ?: "")
                .queryParam("status", status ?: "")
                .build()
        try {
            val response = euxOidcRestTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    String::class.java)

            return if (response.statusCode.is2xxSuccessful) {
                logger.debug("parse RinaSaker json ti RinaSak obj")
                mapJsonToAny(response.body!!, typeRefs())
            } else {
                listOf()
            }
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

    fun getSingleBucAndSedView(euxCaseId: String) = BucAndSedView.from(getBuc(euxCaseId), euxCaseId, "")

    fun getBucAndSedView(rinasaker: List<Rinasak>, aktoerid: String): List<BucAndSedView> {
        val startTime = System.currentTimeMillis()
        val list = mutableListOf<BucAndSedView>()

        rinasaker.forEach {
            val caseId = it.id ?: throw UgyldigCaseIdException("Feil er ikke gyldig caseId fra Rina(Rinasak)")
            list.add(BucAndSedView.from(getBuc(caseId), caseId, aktoerid))
        }

        logger.debug("9 ferdig returnerer list av BucAndSedView. Antall BUC: ${list.size}")
        val sortlist = list.asSequence().sortedByDescending { it.startDate }.toList()
        logger.debug("10. Sortert listen på startDate nyeste dato først")
        logger.debug("11 tiden tok ${System.currentTimeMillis() - startTime} ms.")
        return sortlist
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
            logger.error("noe feil ved opprett buc? exception ${ia.message}", ia)
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


    fun convertListInstitusjonItemToString(deltakere: List<InstitusjonItem>): String {
        val encodedList = mutableListOf<String>()
        deltakere.forEach { item ->
            Preconditions.checkArgument(item.institution.contains(":"), "ikke korrekt formater deltager/Institusjooonner... ")
            encodedList.add("&mottakere=${item.institution}")
        }
        return encodedList.joinToString(separator = "")
    }
    fun putBucMottakere(euxCaseId: String, deltaker: List<InstitusjonItem>): Boolean {
        //cpi/buc/245580/mottakere?KorrelasjonsId=23424&mottakere=NO%3ANAVT003&mottakere=NO%3ANAVT008"
        val correlationId = UUID.randomUUID().toString()
        val builder = UriComponentsBuilder.fromPath("/buc/$euxCaseId/mottakere")
                .queryParam("KorrelasjonsId", correlationId)
                .build()

        val url = builder.toUriString() + convertListInstitusjonItemToString(deltaker)

        logger.debug("Kontakter EUX for å legge til deltager: $deltaker med korrelasjonId: $correlationId på type: $euxCaseId")
        try {
            val response = euxOidcRestTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    null,
                    String::class.java)

            return response.statusCode == HttpStatus.OK
        } catch (ia: IllegalArgumentException) {
            logger.error("noe feil ved mottaker? exception ${ia.message}", ia)
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
                                  vedlegg: Vedlegg,
                                  filtype: String) {
        try {

            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA

            val disposition = ContentDisposition
                    .builder("form-data")
                    .name("file")
                    .filename("")
                    .build().toString()

            val attachmentMeta = LinkedMultiValueMap<String, String>()
            attachmentMeta.add(HttpHeaders.CONTENT_DISPOSITION, disposition)
            val dokumentInnholdBinary = Base64.getDecoder().decode(vedlegg.filInnhold)
            val attachmentPart = HttpEntity(dokumentInnholdBinary, attachmentMeta)

            val body = LinkedMultiValueMap<String, Any>()
            body.add("multipart", attachmentPart)

            val requestEntity = HttpEntity(body, headers)

            val queryUrl = UriComponentsBuilder
                    .fromPath("/buc/")
                    .path(rinaSakId)
                    .path("/sed/")
                    .path(rinaDokumentId)
                    .path("/vedlegg")
                    .queryParam("Filnavn", vedlegg.filnavn)
                    .queryParam("Filtype", filtype)
                    .queryParam("synkron", true)
                    .build().toUriString()

            val response = euxOidcRestTemplate.exchange(
                    queryUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String::class.java)
            if (!response.statusCode.is2xxSuccessful) {
                throw RuntimeException("En feil opppstod under tilknytning av vedlegg: ${response.statusCode}, ${response.body}")
            }
        } catch (ex: java.lang.Exception) {
            logger.error("En feil opppstod under tilknytning av vedlegg, $ex", ex.printStackTrace())
            throw ex
        } finally {
            val file = File(Paths.get("").toAbsolutePath().toString() + "/" + vedlegg.filnavn)
            file.delete()
        }
    }

    //Legger en eller flere deltakere/institusjonItem inn i Rina. (Itererer for hver en)
    fun addDeltagerInstitutions(euxCaseId: String, mottaker: List<InstitusjonItem>) : Boolean {
        logger.debug("Prøver å legge til liste over nye InstitusjonItem til Rina ")

        return putBucMottakere(euxCaseId, mottaker)
    }

    //Henter ut Kravtype og Fnr fra P2100 og P15000
    fun hentFnrOgYtelseKravtype(euxCaseId: String, documentId: String): PinOgKrav {
        val sed = getSedOnBucByDocumentId(euxCaseId, documentId)

        //validere om SED er virkelig en P2100 eller P15000
        if (SEDType.P2100.name == sed.sed) {
            return PinOgKrav(
                    fnr = getFnrMedLandkodeNO(sed.pensjon?.gjenlevende?.person?.pin),
                    krav = sed.nav?.krav ?: Krav()
            )
        }
        //P15000 sjekke om det er 02 Gjenlevende eller ikke
        if (SEDType.P15000.name == sed.sed) {
            val krav = sed.nav?.krav ?: Krav()
            return if ("02" == krav.type) {
                PinOgKrav(
                        fnr = getFnrMedLandkodeNO(sed.pensjon?.gjenlevende?.person?.pin),
                        krav = krav
                )
            } else {
                PinOgKrav(
                        fnr = getFnrMedLandkodeNO(sed.nav?.bruker?.person?.pin),
                        krav = sed.nav?.krav ?: Krav()
                )
            }
        }
        throw SedDokumentIkkeGyldigException("SED gyldig SED av type P2100 eller P15000")
    }

    fun getFnrMedLandkodeNO(pinlist: List<PinItem>?): String? {

        pinlist?.forEach {
            if ("NO" == it.land) {
                return it.identifikator
            }
        }
        return null

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

    fun getFDatoFromSed(euxCaseId: String, bucType: String): String? {
        val sedOnBuc = initSedOnBuc()
        if (!sedOnBuc.containsKey(bucType)) throw GenericUnprocessableEntity("Ugyldig buctype, vi støtter ikke denne bucen $bucType")

        val sedType = sedOnBuc[bucType]

        var fdato: String? = null

        sedType?.forEach {
            val sedDocument = BucUtils(getBuc(euxCaseId)).findFirstDocumentItemByType(it)
            val sed = getSedOnBucByDocumentId(euxCaseId, sedDocument?.id ?: throw NoSuchFieldException("Fant ikke DocumentsItem"))

            val sedValue = sed.sed?.let { it1 -> SEDType.valueOf(it1) }
            logger.info("mapping prefillClass to SED: $sedValue")

            //TODO: Må rette mappingen på P8000
            // "01" er rollen for søker på gjenlevende ytelse
            fdato = if ("01" == sed.nav?.annenperson?.person?.rolle) {
                sed.nav?.annenperson?.person?.foedselsdato
            } else if (sed.pensjon?.gjenlevende?.person?.foedselsdato != null) {
                sed.pensjon?.gjenlevende?.person?.foedselsdato
            }  else {
                sed.nav?.bruker?.person?.foedselsdato
            }
            if (fdato != null) return fdato
        }
        throw IkkeFunnetException("Ingen fødselsdato funnet")
    }

    /**
     * Own impl. no list from eux that contains list of SED to a speific BUC
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
                    "P_BUC_09" to listOf("P14000"),
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
                map["P_BUC_09"]?.let { set.addAll(it) }
                map["P_BUC_10"]?.let { set.addAll(it) }
                return set.toList()
            }
            return map[bucType].orEmpty()
        }
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class IkkeFunnetException(message: String) : IllegalArgumentException(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedDokumentIkkeOpprettetException(message: String) : Exception(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedDokumentIkkeLestException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class SedIkkeSlettetException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class BucIkkeMottattException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class EuxGenericServerException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class RinaCasenrIkkeMottattException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class SedDokumentIkkeSendtException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
class EuxServerException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
open class GenericUnprocessableEntity(message: String) : IllegalArgumentException(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class SedDokumentIkkeGyldigException(message: String?) : Exception(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class UgyldigCaseIdException(message: String) : IllegalArgumentException(message)
