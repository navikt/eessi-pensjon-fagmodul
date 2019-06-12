package no.nav.eessi.eessifagmodul.services.eux

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.eessifagmodul.models.IkkeGyldigKallException
import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.SEDType
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.*
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import org.bouncycastle.asn1.x500.style.RFC4519Style.name
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.awt.print.Book




class BucUtils {
    private val logger = LoggerFactory.getLogger(BucUtils::class.java)

    private var buc: Buc
    private lateinit var bucjson: String

    constructor(buc: Buc) {
        this.buc = buc
    }
    constructor(bucjson: String) {
        this.buc = parseBuc(bucjson)
        this.bucjson = bucjson
    }

    fun parseBuc(bucjson: String): Buc {
        val mapper = jacksonObjectMapper()
        val rootNode = mapper.readValue(bucjson, JsonNode::class.java)

        val creator = rootNode["creator"]
        val actions = rootNode["actions"]
        val documents = rootNode["documents"]
        val participants = rootNode["participants"]
        val subject = rootNode["subject"]


        val buc = mapJsonToAny(rootNode.toString(), typeRefs<Buc>())
        buc.creator = mapJsonToAny(creator.toString(), typeRefs())
        buc.actions = mapJsonToAny(actions.toString(), typeRefs<List<ActionsItem>>())
        buc.documents = mapJsonToAny(documents.toString(), typeRefs())
        buc.participants = mapJsonToAny(participants.toString(), typeRefs<List<ParticipantsItem>>())
        buc.subject = mapJsonToAny(subject.toString(), typeRefs())

        return buc
    }


    fun getBuc(): Buc {
        return buc
    }

    fun getCreator(): Creator? {
        return getBuc().creator
    }

    fun getCreatorContryCode(): Map<String, String> {
        val countryCode = getCreator()?.organisation?.countryCode ?: "N/A"
        return mapOf(Pair("countrycode", countryCode))
    }

    fun getSubject(): Subject {
        return getBuc().subject ?: throw NoSuchFieldException("Fant ikke Subject")
    }

    fun getDocuments(): List<DocumentsItem> {
        return getBuc().documents ?: throw NoSuchFieldException("Fant ikke DocumentsItem")
    }

    fun findDocument(documentId: String): ShortDocumentItem {
        getAllDocuments().forEach {
            if (documentId == it.id) {
                return it
            }
        }
        return ShortDocumentItem(id = documentId)
    }

    fun getBucAttachments(): List<Attachment>? {
        return getBuc().attachments
    }

    fun getStartDate(): LocalDate {
        val date = getBuc().startDate
        return getLocalDate(date)
    }

    fun getLastDate(): LocalDate {
        val date = getBuc().lastUpdate
        return getLocalDate(date)
    }

    fun getLocalDate(date: Any?): LocalDate {
        return if (date is Long) {
            Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()
        } else if (date is String) {
            val datestr = date.substring(0, date.indexOf('T'))
            LocalDate.parse(datestr)
        } else {
            LocalDate.now().minusYears(1000)
        }
    }

    fun getProcessDefinitionName(): String? {
        return getBuc().processDefinitionName
    }

    fun getProcessDefinitionVersion(): String? {
        return getBuc().processDefinitionVersion
    }

    fun findFirstDocumentItemByType(sedType: SEDType): ShortDocumentItem? {
        return findFirstDocumentItemByType(sedType.name)
    }

    fun findFirstDocumentItemByType(sedType: String): ShortDocumentItem? {
        val documents = getDocuments()
        documents.forEach {
            if (sedType == it.type) {
                val shortdoc = createShortDocument(it)
                return shortdoc
            }
        }
        return null
    }



    private fun createShortDocument(docuemntItem: DocumentsItem): ShortDocumentItem {
        return ShortDocumentItem(
                id = docuemntItem.id,
                type = docuemntItem.type,
                status = docuemntItem.status,
                creationDate = docuemntItem.creationDate,
                lastUpdate = getLocalDate(docuemntItem.lastUpdate),
                participants = createParticipants(docuemntItem.conversations),
                attachments = createShortAttachemnt(docuemntItem.attachments)
        )
    }

    private fun createParticipants(conventions: List<ConversationsItem>?): List<ParticipantsItem?>? {

        conventions?.forEach {
            return it.participants
        }

        return null
    }


    private fun createShortAttachemnt(attachments: List<Attachment>?): List<ShortAttachment> {

        val list = mutableListOf<ShortAttachment>()
        attachments?.forEach {
            list.add(
                    ShortAttachment(
                            id = it.id,
                            name = it.name,
                            mimeType = it.mimeType,
                            fileName = it.fileName,
                            documentId = it.documentId,
                            lastUpdate = getLocalDate(it.lastUpdate),
                            medical = it.medical
                    )
            )
        }
        return list
    }

    fun getAllDocuments(): List<ShortDocumentItem> {
        val documents = getDocuments()
        val lists = mutableListOf<ShortDocumentItem>()
        documents.forEach {
            lists.add(createShortDocument(it))
        }
        return lists
    }

    fun findAndFilterDocumentItemByType(sedType: SEDType): List<ShortDocumentItem> {
        return findAndFilterDocumentItemByType(sedType.name)
    }

    fun findAndFilterDocumentItemByType(sedType: String): List<ShortDocumentItem> {
        val documents = getDocuments()
        val lists = mutableListOf<ShortDocumentItem>()
        documents.forEach {
            if (sedType == it.type) {
                lists.add(createShortDocument(it))
            }
        }
        return lists
    }

    //hjelpefunkson for å hente ut list over alle documentid til valgt SEDType (kan ha flere docid i type)
    fun findDocmentIdBySedType(sedType: SEDType): List<String?> {
        val doclist = findAndFilterDocumentItemByType(sedType)
        return doclist.map { it.id }.toList()
    }

    fun getSbdh(): List<Sbdh> {
        val lists = mutableListOf<Sbdh>()
        val documents = getDocuments()
        //Sbdh -> UserMessagesItem -> ConversationsItem -> DocumentsItem -> Buc
        for (doc in documents) {
            for (conv in doc.conversations!!) {
                val usermsgs = conv.userMessages
                usermsgs?.forEach {
                    val sbdh = it.sbdh!!
                    lists.add(sbdh)
                }
            }
        }
        return lists
    }

    fun getInternatinalId(): String? {
        return getBuc().internationalId
    }

    fun getParticipants(): List<ParticipantsItem>? {
        return getBuc().participants
    }

    fun getParticipantsExclusiveCaseowner(): List<ParticipantsItem> {
        val parts = getParticipants()
        val caseOwner = "CaseOwner"
        val bucdeltakere = mutableListOf<ParticipantsItem>()
        parts?.forEach {
            if (it.role != caseOwner) {
                bucdeltakere.add(it)
            }
        }
        return bucdeltakere
    }

    fun getParticipantsExclusiveCaseownerAsInstitusjonItem(): List<InstitusjonItem> {
        val list = getParticipantsExclusiveCaseowner()
        logger.debug("ParticipantsExclusive size: ${list.size}")

        val result = mutableListOf<InstitusjonItem>()
        list.forEach{
            val institusjonItem = InstitusjonItem(
                    country = it.organisation?.countryCode,
                    institution = it.organisation?.name,  //kan hende må være id?!
                    name = "" //
            )
            logger.debug("Legger til BucParticipants som InstitusjonItem i liste")
            result.add(institusjonItem)
        }
        return result
    }

    fun matchParticipantsToInstitusjonItem(bucParticipants: List<InstitusjonItem>, list: List<InstitusjonItem>): List<InstitusjonItem> {
        logger.debug("Sjekker på bucDeltakere mot Nye")
        if (bucParticipants.isEmpty() && list.isEmpty()) {
            logger.debug("BucDeltakere og Nye er Begge tomme (bør ikke skje)")
            throw IkkeGyldigKallException("Ingen deltakere/Institusjon er tom")
        }
        if (bucParticipants.isEmpty() && list.isNotEmpty()) {
            logger.debug("BucDeltaker (filtrert) er tom (helt ny Buc) returnerer Ny")
            return list
        }
        if (bucParticipants.isNotEmpty() && list.isEmpty()) {
            logger.debug("BucDeltaker er ikke tom, Ny er tom returner Ny")
            return list
        }

        logger.debug("BucDeltaker er ikke tom, Ny er ikke tom, finne unike som ikke finnes i Buc")
        val deltakere = mutableListOf<InstitusjonItem>()
        deltakere.addAll(list)
        val found = mutableListOf<InstitusjonItem>()
        bucParticipants.forEach { bucpart ->
           for (apidel in list) {
                if (apidel.country == bucpart.country && apidel.institution == bucpart.institution) {
                    found.add(apidel)
                }
            }
        }
        logger.debug("Fjerner funnet Deltakere i Buc fra nye Deltakere $found")
        deltakere.removeAll(found)
        logger.debug("Returnerer filtrert liste over Deltakere $deltakere")
        return deltakere
    }

    fun getBucAction(): List<ActionsItem>? {
        return getBuc().actions
    }

    fun getRinaAksjon(): List<RinaAksjon> {
        val aksjoner = mutableListOf<RinaAksjon>()
        val actionitems = getBuc().actions
        val buctype = getProcessDefinitionName()
        actionitems?.forEach {
            if (it.documentType != null) {
                aksjoner.add(
                        RinaAksjon(
                                dokumentType = it.documentType,
                                navn = it.name,
                                dokumentId = it.documentId,
                                kategori = "Documents",
                                id = buctype
                        )
                )
            }
        }
        return aksjoner.sortedBy { it.dokumentType }.toList()
    }

}

