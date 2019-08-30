package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Description
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.concurrent.CompletableFuture

@Service
@Description("Async service class for eux-rina-api/cpi/buc getBuc")
class AsyncEuxService(private val euxAsyncOidcRestTemplate: RestTemplate) {

    private val logger = LoggerFactory.getLogger(AsyncEuxService::class.java)

    @Async("threadPoolTaskExecutor")
    fun  getBucAsync(euxCaseId: String, oidcToken: String): CompletableFuture<BucAndSedView> {
        val path = "/buc/{RinaSakId}"
        val uriParams = mapOf("RinaSakId" to euxCaseId)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)

        logger.info("Prøver å kontakte EUX /$euxCaseId} - kjører i trå: ${Thread.currentThread().name}")

        val headers = HttpHeaders()
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer $oidcToken")
        val httpEntity = HttpEntity("", headers)

        val response = euxAsyncOidcRestTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                httpEntity,
                String::class.java
        )

        val buc = mapJsonToAny(response.body!!, typeRefs<Buc>() )
        return try {
            val bucsedview  = BucAndSedView.from ( buc, buc.id!!, "" )
            logger.debug("BucSedView : ${bucsedview.caseId}  Seds: ${bucsedview.seds?.let { it.size }}")
            CompletableFuture.completedFuture(bucsedview )
        } catch (ex: Exception) {
            logger.error("Feiler ved mapping av BucAndSedView Buc: $euxCaseId", ex)
            CompletableFuture.completedFuture( BucAndSedView(caseId = euxCaseId, type = "Feiler ved mapping av BucAndSedView Buc: $euxCaseId")  )
        }
   }

}
