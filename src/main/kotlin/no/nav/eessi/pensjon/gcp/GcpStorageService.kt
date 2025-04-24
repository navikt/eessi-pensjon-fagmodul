package no.nav.eessi.pensjon.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.eessi.pensjon.kodeverk.KodeverkClient.Companion.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.ByteBuffer

@Component
class GcpStorageService(
    @param:Value("\${GCP_BUCKET_GJENNY}") var gjennyBucket: String,
    @param:Value("\${GCP_BUCKET_P8000}") var p8000Bucket: String,
    private val gcpStorage: Storage) {

    private val logger = LoggerFactory.getLogger(GcpStorageService::class.java)

    init {
        listOf(gjennyBucket, p8000Bucket).forEach { ensureBucketExists(it)}
    }

    private fun ensureBucketExists(bucketNavn: String): Boolean {
        when (gcpStorage.get(bucketNavn) != null) {
            false -> throw IllegalStateException("Fant ikke bucket med navn $bucketNavn. Må provisjoneres")
            true -> logger.info("Bucket $bucketNavn funnet.")
        }
        return false
    }

    fun lagreGjennySak(euxCaseId: String, gjennysak: GjennySak) {
        lagre(euxCaseId, gjennysak.toJson(), gjennyBucket)
    }

    fun lagreP8000Options(documentid: String, options: String) {
        if(p8000SakFinnes(documentid)){
            gcpStorage.delete(BlobId.of(p8000Bucket, documentid))
        }
        lagre(documentid, options, p8000Bucket)
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