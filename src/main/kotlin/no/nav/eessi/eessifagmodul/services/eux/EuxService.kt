package no.nav.eessi.eessifagmodul.services.eux

import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Buc
import no.nav.eessi.eessifagmodul.utils.getCounter
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRef
import no.nav.eessi.eessifagmodul.utils.typeRefs
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Description
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.*


@Service
@Description("Service class for EuxBasis - EuxCpiServiceController.java")
class EuxService(private val euxOidcRestTemplate: RestTemplate) {
    private val logger = LoggerFactory.getLogger(EuxService::class.java)

    // Nye API kall er er fra 23.01.19
    // https://eux-app.nais.preprod.local/swagger-ui.html#/eux-cpi-service-controller/getDocumentUsingGET


    //Oppretter ny RINA sak(buc) og en ny Sed
    @Throws(EuxServerException::class, RinaCasenrIkkeMottattException::class)
    fun opprettBucSed(navSED: SED, bucType: String, mottakerid: String, fagSaknr: String): BucSedResponse {
        val path = "/buc/sed"
        val builder = UriComponentsBuilder.fromPath(path)
                .queryParam("BucType", bucType)
                .queryParam("MottakerId", mottakerid)
                .queryParam("FagSakNummer", fagSaknr)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val httpEntity = HttpEntity(navSED.toJson(), headers)

        return try {
            logger.info("Prøver å kontakte EUX /${builder.toUriString()}")
            val response = euxOidcRestTemplate.exchange(builder.toUriString(),
                    HttpMethod.POST,
                    httpEntity,
                    String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                getCounter("OPPRETTBUCOGSEDOK").increment()
                mapJsonToAny(response.body!!, typeRefs())
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


    //ny SED på ekisterende buc
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


    //henter ut bucdata fra valgt buc/euxCaseId
    @Throws(BucIkkeMottattException::class, EuxServerException::class)
    fun getBuc(euxCaseId: String): Buc {
        logger.info("har euxCaseId verdi: $euxCaseId")

        val path = "/buc/{RinaSakId}"
        val uriParams = mapOf("RinaSakId" to euxCaseId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)

        return try {
            logger.info("Prøver å kontakte EUX /${builder.toUriString()}")

            val response = euxOidcRestTemplate.exchange(builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                val jsonbuc = response.body!!
                getCounter("HENTBUCOK").increment()
                mapJsonToAny(jsonbuc, typeRefs())
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
        logger.info("Prøver å hente ut en BucUtils for buc $euxCaseId")
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


    //Eldre API kall er under denne disse vil ikke virke


    //Henter en liste over tilgjengelige aksjoner for den aktuelle RINA saken PK-51365"
//    fun getPossibleActions(euSaksnr: String): List<RINAaksjoner> {
//        val builder = UriComponentsBuilder.fromPath("/MuligeAksjoner")
//                .queryParam("RINASaksnummer", euSaksnr)
//
//        val httpEntity = HttpEntity("")
//
//        val response = euxOidcRestTemplate.exchange(builder.toUriString(), HttpMethod.GET, httpEntity, typeRef<String>())
//        val responseBody = response.body!!
//        try {
//            if (response.statusCode.isError) {
//                getCounter("AKSJONFEIL").increment()
//                throw createErrorMessage(responseBody)
//            } else {
//                getCounter("AKSJONOK").increment()
//                return mapJsonToAny(responseBody, typeRefs())
//            }
//        } catch (ex: IOException) {
//            getCounter("AKSJONFEIL").increment()
//            throw RuntimeException(ex.message)
//        }
//    }
//
//    /*
//        hjelpe funksjon for sendSED må hente ut dokumentID for valgt sed f.eks P2000
//     */
//    fun hentDocuemntID(euxCaseId: String, sed: String): String {
//        val aksjon = "Send"
//        val aksjoner = getPossibleActions(euxCaseId)
//        aksjoner.forEach {
//            if (sed == it.dokumentType && aksjon == it.navn) {
//                return it.dokumentId ?: throw IkkeGyldigKallException("Ingen gyldig dokumentID funnet")
//            }
//        }
//        throw IkkeGyldigKallException("Ingen gyldig dokumentID funnet")
//    }

    //TODO: euxBasis hva finnes av metoder for:
    //TODO: euxBasis metode for å legge til flere mottakere (Institusjoner)
    //TODO: euxBasis metode for å fjenre en eller flere mottakere (Institusjoner)

}
