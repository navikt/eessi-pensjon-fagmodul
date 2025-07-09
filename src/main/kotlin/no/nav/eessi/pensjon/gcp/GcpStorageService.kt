package no.nav.eessi.pensjon.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.ByteBuffer

@Component
class GcpStorageService(
    @param:Value("\${GCP_BUCKET_GJENNY}") var gjennyBucket: String,
    @param:Value("\${GCP_BUCKET_P8000}") var p8000Bucket: String,
    @param:Value("\${GCP_BUCKET_P6000}") var p6000Bucket: String,
    @param:Value("\${GCP_BUCKET_SAKSBEHANDLING_API}") var saksBehandlApiBucket: String,

    private val gcpStorage: Storage) {

    private val logger = LoggerFactory.getLogger(GcpStorageService::class.java)

    init {
        listOf(gjennyBucket, p8000Bucket, saksBehandlApiBucket, p6000Bucket).forEach { ensureBucketExists(it)}
    }

    private fun ensureBucketExists(bucketNavn: String): Boolean {
        when (gcpStorage.get(bucketNavn) != null) {
            false -> throw IllegalStateException("Fant ikke bucket med navn $bucketNavn. Må provisjoneres")
            true -> logger.info("Bucket $bucketNavn funnet.")
        }
        return false
    }

    fun lagreRinasakFraFrontEnd(pesysId: String, rinaSakId: String, dokumentId: String) {
        try {
            lagretilBackend(pesysId, rinaSakId, dokumentId, p6000Bucket)
        } catch (ex: Exception) {
            logger.error("Lagring til $p6000Bucket feilet: $ex")
        }
    }

    fun lagretilBackend(pesysId: String, rinaSakId: String, dokumentId: String, backEndBucket: String) {

        val blobInfo = BlobInfo.newBuilder(BlobId.of(backEndBucket, pesysId)).setContentType("application/json").build()
        val dataTilLagring = FrontEndData(pesysId, rinaSakId, dokumentId).toJson()

        runCatching {
            gcpStorage.writer(blobInfo).use {
                it.write(ByteBuffer.wrap(dataTilLagring.toByteArray()))
            }
        }.onFailure { e ->
            logger.error("Feilet med å lagre detaljer med id: ${blobInfo.blobId.name} i bucket: $backEndBucket", e)
        }.onSuccess {
            logger.info("Lagret sed detaljer til S3 med pesysId: $pesysId til $backEndBucket")
        }
    }

    fun lagreGjennySak(euxCaseId: String, gjennysak: GjennySak) {
        return if (gjennysak.sakId?.length in 4..5 && gjennysak.sakId?.all { it.isDigit() } == true) {
            lagre(euxCaseId, gjennysak.toJson(), gjennyBucket)
        } else {
            logger.error("SakId må være korrekt strukturert med 5 tegn; mottok: ${gjennysak.toJson()}")
        }
    }

        fun lagreP8000Options(documentid: String, options: String) {
            if(p8000SakFinnes(documentid)){
                gcpStorage.delete(BlobId.of(p8000Bucket, documentid))
            }
            lagre(documentid, options, p8000Bucket)
        }
    fun hentTrygdetid(aktoerId: String, rinaSakId: String): List<String>? {
        val searchString = if (aktoerId.isNotEmpty() && rinaSakId.isNotEmpty()) {
            "${aktoerId}___PESYS___$rinaSakId"
        } else if (aktoerId.isNotEmpty()) {
            aktoerId + "___PESYS___"
        } else if (rinaSakId.isNotEmpty()) {
            "___PESYS___$rinaSakId"
        } else {
            logger.warn("Henter trygdetid uten gyldig aktoerId eller rinaSakId")
            return null
        }
        logger.info("Henter trygdetid for aktoerId: $aktoerId eller rinaSakId: $rinaSakId, med søkestreng: $searchString")

        kotlin.runCatching {
            val trygdetid = gcpStorage.get(BlobId.of(saksBehandlApiBucket, searchString))
            if (trygdetid.exists()) {
                logger.info("Henter melding med aktoerId $searchString, for bucket $saksBehandlApiBucket")
                return listOf(trygdetid.getContent().decodeToString())
            }
        }.onFailure { e ->
            logger.error("Feil ved henting av trygdetid for aktoerId: $aktoerId, rinaSakId: $rinaSakId", e)
        }
        return emptyList()
    }

    private fun lagre(euxCaseId: String, informasjon: String, bucketNavn: String) {
        if(bucketNavn == gjennyBucket) {
            if (gjennySakFinnes(euxCaseId)) return
        }
        else {
            if (p8000SakFinnes(euxCaseId)) return
        }

        val blobInfo =  BlobInfo.newBuilder(BlobId.of(bucketNavn, euxCaseId)).setContentType("application/json").build()
        kotlin.runCatching {
            gcpStorage.writer(blobInfo).use {
                it.write(ByteBuffer.wrap(informasjon.toByteArray()))
            }
        }.onFailure { e ->
            logger.error("Feilet med å lagre dokument med id: ${blobInfo.blobId.name} for bucket: $bucketNavn", e)
        }.onSuccess {
            logger.info("Lagret info på S3 med rinaID: $euxCaseId for $bucketNavn: $informasjon")
        }
    }

    fun gjennySakFinnes(euxCaseId: String): Boolean {
        return eksisterer(euxCaseId, gjennyBucket)
    }
    fun p8000SakFinnes(euxCaseId: String): Boolean {
        return eksisterer(euxCaseId, p8000Bucket)
    }

    private fun eksisterer(storageKey:String, bucketNavn: String): Boolean {
        logger.debug("sjekker om $storageKey finnes i bucket: $bucketNavn")

        kotlin.runCatching {
            gcpStorage.get(BlobId.of(bucketNavn, storageKey)).exists()
        }.onFailure {
            return false
        }.onSuccess {
            return true
        }
        return false
    }

    fun hentP8000(storageKey:String): String? {
        kotlin.runCatching {
            val options =  gcpStorage.get(BlobId.of(p8000Bucket, storageKey))
            if (options.exists()) {
                logger.info("Henter melding med rinanr $storageKey, for bucket $p8000Bucket")
                return options.getContent().decodeToString()
            }
        }.onFailure {
            logger.info("Henter melding med rinanr $storageKey, for bucket $p8000Bucket")

        }
        return null
    }
}

data class FrontEndData(
    val pesysId: String,
    val rinaSakId: String,
    val dokumentId: String
)