package no.nav.eessi.eessifagmodul.services.eux

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.eessifagmodul.models.SEDType
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.*
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs

class BucUtils {

    private lateinit var buc: Buc
    private lateinit var bucjson: String

    constructor(buc: Buc) {
        this.buc = buc
    }

    constructor(bucjson: String) {
        this.bucjson = bucjson
        this.buc = parseBuc(bucjson)
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

    fun getCreator(): Creator {
        return getBuc().creator ?: throw NoSuchFieldException("Fant ikke Creator")
    }

    fun getSubject(): Subject {
        return getBuc().subject ?: throw NoSuchFieldException("Fant ikke Subject")
    }

    fun getDocuments(): List<DocumentsItem> {
        return getBuc().documents ?: throw NoSuchFieldException("Fant ikke DocumentsItem")
    }

    fun findFirstDocumentItemByType(sedType: SEDType): ShortDocumentItem? {
        val documents = getDocuments()
        documents.forEach {
            if (sedType.name == it.type) {
                val shortdoc = ShortDocumentItem(
                        id = it.id,
                        type = it.type,
                        status = it.status
                )
                return shortdoc
            }
        }
        return null
    }

    fun findAndFilterDocumentItemByType(sedType: SEDType): List<ShortDocumentItem> {
        val documents = getDocuments()
        val lists = mutableListOf<ShortDocumentItem>()
        documents.forEach {
            if (sedType.name == it.type) {
                val shortdoc = ShortDocumentItem(
                        id = it.id,
                        type = it.type,
                        status = it.status
                )
                lists.add(shortdoc)
            }
        }
        return lists
    }

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

    fun getCaseOwnerCountryCode(): String {
        val creator = getCreator()
        val participants = getBuc().participants ?: throw NoSuchFieldException("Fant ikke Participants")
        participants.forEach {
            if ("CaseOwner" == it?.role) {
                if (it.organisation?.id == creator.organisation?.id) {
                    creator.organisation?.countryCode?.let { return it }
                }
            }

        }
        return ""
    }

}

