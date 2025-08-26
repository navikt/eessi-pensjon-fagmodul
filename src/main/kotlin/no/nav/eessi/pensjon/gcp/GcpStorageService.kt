package no.nav.eessi.pensjon.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import kotlin.text.get

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

    fun lagretilBackend(p6000Detaljer: String, pesysId:String) {

        val blobInfo = BlobInfo.newBuilder(BlobId.of(p6000Bucket, pesysId)).setContentType("application/json").build()

        runCatching {
            gcpStorage.writer(blobInfo).use {
                it.write(ByteBuffer.wrap(p6000Detaljer.toByteArray()))
            }
        }.onFailure { e ->
            logger.error("Feilet med å lagre detaljer med id: ${blobInfo.blobId.name} i bucket: $p6000Bucket", e)
        }.onSuccess {
            logger.info("Lagret sed detaljer til S3 med pesysId: $pesysId til $p6000Bucket")
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
    fun hentTrygdetid(aktoerId: String): List<Pair<String, String?>>? {
        val searchString = if (aktoerId.isNotEmpty() ) {
            "${aktoerId}___PESYS___"
        } else if (aktoerId.isNotEmpty()) {
            aktoerId + "___PESYS___"
        } else {
            logger.warn("Henter trygdetid uten gyldig aktoerId eller rinaSakId")
            return null
        }
        val obfuscatedNr = aktoerId.take(4) + "*".repeat(aktoerId.length - 4)
        logger.info("Henter trygdetid for aktoerId: $aktoerId eller rinaSakId: $obfuscatedNr")

        kotlin.runCatching {
            val blobIds = finnBlobMedDelvisId( searchString)
            return blobIds.mapNotNull {
                val trygdetid = gcpStorage.get(BlobId.of(saksBehandlApiBucket, it))
                if (trygdetid.exists()) {
                    logger.info("Trygdetid finnes for: $obfuscatedNr, bucket $saksBehandlApiBucket")
                    Pair(trygdetid.getContent().decodeToString(), trygdetid.name)
                } else {
                    null
                }
            }
//            val trygdetid = blobId.let { gcpStorage.get(BlobId.of(saksBehandlApiBucket, blobId))} ?: return null
//            if (trygdetid.exists()) {
//                logger.info("Trygdetid finnes for: $obfuscatedNr}, bucket $saksBehandlApiBucket")
//                return Pair(trygdetid.getContent().decodeToString(), trygdetid.name)
//            }
        }.onFailure { e ->
            logger.error("Feil ved henting av trygdetid for fnr: $obfuscatedNr", e)
        }
        return null
    }

    fun finnBlobMedDelvisId(fnrMedPesys: String): List<String> {
        val blobs = gcpStorage.list(saksBehandlApiBucket, Storage.BlobListOption.prefix(fnrMedPesys))
        return blobs.iterateAll().mapNotNull { it.name }
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

    fun hentGcpDetlajerForP6000(storageKey:String): String? {
        kotlin.runCatching {
            val options =  gcpStorage.get(BlobId.of(p6000Bucket, storageKey))
            if (options.exists()) {
                logger.info("Henter melding med rinanr $storageKey, for bucket $p8000Bucket")
                return options.getContent().decodeToString()
            }
        }.onFailure {
            logger.info("Henter melding med rinanr $storageKey, for bucket $p8000Bucket")

        }
        return null
    }

    fun hentGcpDetlajerPaaId(storageKey:String): String? {
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

