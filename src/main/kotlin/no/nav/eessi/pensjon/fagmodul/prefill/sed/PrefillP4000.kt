package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.fagmodul.sedmodel.PersonArbeidogOppholdUtland
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP4000(private val prefillSed: PrefillSed) {

    private val logger: Logger =  LoggerFactory.getLogger(PrefillP4000::class.java)
    private val mapper = jacksonObjectMapper()

    fun prefillSed(prefillData: PrefillDataModel, personData: PersonDataCollection): SED {
        return try {
            val sed = prefillSed.prefill(prefillData, personData)
            sed.trygdetid = perfillPersonTrygdetid(prefillData)
            sed
        } catch (ex: Exception) {
            logger.error("Feiler ved prefill P4000 person", ex)
            SED(prefillData.sedType)
        }
    }

    private fun perfillPersonTrygdetid(prefillData: PrefillDataModel) : PersonArbeidogOppholdUtland? {
            val p4000json = prefillData.getPartSEDasJson("P4000")

            return try {
                val personDataNode = mapper.readTree(p4000json)

                logger.debug("Prøver å preutfylle mappe om en p4000-innseningdata til P4000")
                val personDataJson =  personDataNode["periodeInfo"].toString()
                mapJsonToAny(personDataJson, typeRefs())

            } catch (ex: Exception) {
                logger.debug("Feiler ved mapping av P4000, fortsetter")
                null
            }
    }

}