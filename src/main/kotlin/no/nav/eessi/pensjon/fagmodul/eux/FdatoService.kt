package no.nav.eessi.pensjon.fagmodul.eux

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory

class FdatoService(private val euxService: EuxService) {

    private val logger = LoggerFactory.getLogger(FdatoService::class.java)

    private val mapper = jacksonObjectMapper()

    fun getFDatoFromSed(euxCaseId: String, bucType: String): String? {
        val gyldigeSeds = filterUtGyldigSedId(BucUtils(euxService.getBuc(euxCaseId)).getAllDocuments().toJson())
        return finnFDatoFraSed(euxCaseId,gyldigeSeds)

    }

    private fun finnFDatoFraSed(euxCaseId: String, gyldigeSeds: List<Pair<String, String>>): String? {
        var fdato: String? = null

        gyldigeSeds?.forEach {  pair ->
            try {
                val sedDocumentId =  pair.first
                val sedType = pair.second
                val sedJson = euxService.getSedOnBucByDocumentIdAsJson(euxCaseId, sedDocumentId)
                val sedRootNode = mapper.readTree(sedJson)

                when (sedType) {
                    SEDType.P2100.name -> {
                        fdato = filterGjenlevendeFDatoNode(sedRootNode)
                    }
                    SEDType.P15000.name -> {
                        val krav = sedRootNode.get("nav").get("krav").textValue()
                        if (krav == "02") {
                            fdato = filterGjenlevendeFDatoNode(sedRootNode)
                        } else {
                            fdato = filterPersonFDatoNode(sedRootNode)
                        }
                    }
                    else -> {
                        fdato = filterAnnenPersonFDatoNode(sedRootNode)
                        if (fdato == null) {
                            //P2000 - P2200 -- andre..
                            fdato = filterPersonFDatoNode(sedRootNode)
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.error("Feil ved henting av fødseldato, ${ex.message}", ex)
                throw IkkeFunnetException("Ingen fødselsdato funnet")
            }
            print(fdato)
            if (fdato != null) return fdato
        }
        throw IkkeFunnetException("Ingen fødselsdato funnet")
    }

    private fun filterAnnenPersonFDatoNode(sedRootNode: JsonNode): String? {
        val subNode = sedRootNode.at("/nav/annenperson") ?: return null
        if (subNode.at("/person/rolle").textValue() == "01") {
            return subNode.get("person")
                    .map { node -> node.get("foedselsdato").textValue() }
                    .lastOrNull()
        }
        return null
    }

    private fun filterGjenlevendeFDatoNode(sedRootNode: JsonNode): String? {
        return sedRootNode
                .at("/pensjon/gjenlevende/person")
                .get("foedselsdato").textValue()
    }

    private fun filterPersonFDatoNode(sedRootNode: JsonNode): String? {
        return sedRootNode.at("/nav/bruker/person")
                .get("foedselsdato").textValue()
    }

    private fun filterUtGyldigSedId(sedJson: String?): List<Pair<String, String>> {
        val validSedtype = listOf("P2000","P2100","P2200","P1000",
                "P5000","P6000","P7000", "P8000",
                "P10000","P1100","P11000","P12000","P14000","P15000")
        val sedRootNode = mapper.readTree(sedJson)
        return sedRootNode
                .filterNot { node -> node.get("status").textValue() =="empty" }
                .filter { node ->  validSedtype.contains(node.get("type").textValue()) }
                .map { node -> Pair(node.get("id").textValue(), node.get("type").textValue()) }
                .sortedBy { (_, sorting) -> sorting }
                .toList()
    }

}