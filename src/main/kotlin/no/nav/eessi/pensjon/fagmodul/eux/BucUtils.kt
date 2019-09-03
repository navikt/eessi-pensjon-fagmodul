package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.eux.basismodel.RinaAksjon
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.*
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


class BucUtils(private val buc: Buc ) {

    private val logger = LoggerFactory.getLogger(BucUtils::class.java)

    private fun getBuc(): Buc {
        return buc
    }

    fun getStatus(): String? {
        return getBuc().status
    }

    fun getCreator(): Creator? {
        return getBuc().creator
    }

    fun getCreatorAsInstitusjonItem(): InstitusjonItem? {
        return InstitusjonItem(
                country = getCreator()?.organisation?.countryCode ?: "",
                institution = getCreator()?.organisation?.id ?: "",
                name = getCreator()?.organisation?.name
        )
    }

    fun getCaseOwner() = getParticipants()
                .asSequence()
                .filter { it.role == "CaseOwner" }
                .map {
                    InstitusjonItem(
                            country = it.organisation?.countryCode ?: "",
                            institution = it.organisation?.id ?: "",  //kan hende må være id?!
                            name = it.organisation?.name ?: "" //name optinal
                    )
                }.first() ?: InstitusjonItem("N/A", "NO:NO")

    fun getSubject() = buc.subject ?: throw NoSuchFieldException("Fant ikke Subject")

    fun getCreatorContryCode(): Map<String, String> {
        val countryCode = getCreator()?.organisation?.countryCode ?: "N/A"
        return mapOf(Pair("countrycode", countryCode))
    }

    private fun getDocuments(): List<DocumentsItem> {
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

    fun getLastDate(): LocalDate {
        val date = getBuc().lastUpdate
        return getLocalDate(date)
    }

    fun getStartDateLong(): Long {
        val date = getBuc().startDate
        return getDateTimeToLong(date)
    }

    fun getLastDateLong(): Long {
        val date = getBuc().lastUpdate
        return getDateTimeToLong(date)
    }

    private fun getLocalDate(date: Any?): LocalDate =
            when (date) {
                is Long -> Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()
                is String -> {
                    val datestr = date.substring(0, date.indexOf('T'))
                    LocalDate.parse(datestr)
                }
                else -> LocalDate.now().minusYears(1000)
            }

    private fun getDateTimeToLong(dateTime: Any?): Long {
        return getDateTime(dateTime).millis
    }

    private fun getDateTime(dateTime: Any?): DateTime  {
        val zoneId = DateTimeZone.forID(ZoneId.systemDefault().id)

            return when (dateTime) {
                is Long -> DateTime(DateTime(dateTime).toInstant(),zoneId)
                is String -> DateTime(DateTime.parse(dateTime).toInstant(),zoneId)
                else -> DateTime.now().minusYears(1000)
            }
    }

    fun getProcessDefinitionName() = getBuc().processDefinitionName

    fun getProcessDefinitionVersion() = getBuc().processDefinitionVersion

    fun findFirstDocumentItemByType(sedType: SEDType) = findFirstDocumentItemByType(sedType.name)

    fun findFirstDocumentItemByType(sedType: String) = getDocuments().find { sedType == it.type }?.let { createShortDocument(it) }

    private fun createShortDocument(documentItem: DocumentsItem) =
            ShortDocumentItem(
                id = documentItem.id,
                parentDocumentId = documentItem.parentDocumentId,
                type = documentItem.type,
                displayName = documentItem.displayName,
                status = documentItem.status,
                creationDate = getDateTimeToLong(documentItem.creationDate),
                lastUpdate = getDateTimeToLong(documentItem.lastUpdate),
                participants = createParticipants(documentItem.conversations),
                attachments = createShortAttachemnt(documentItem.attachments)
        )

    /* TODO Is this working as expected? */
    private fun createParticipants(conventions: List<ConversationsItem>?): List<ParticipantsItem?>? {
        conventions?.forEach {
            return it.participants
        }
        return null
    }

    private fun createShortAttachemnt(attachments: List<Attachment>?) =
            attachments?.map {
                ShortAttachment(
                    id = it.id,
                    name = it.name,
                    mimeType = it.mimeType,
                    fileName = it.fileName,
                    documentId = it.documentId,
                    lastUpdate = getDateTimeToLong(it.lastUpdate),
                    medical = it.medical
                )
            }.orEmpty()

    fun getAllDocuments() = getDocuments().map { createShortDocument(it) }

    fun findAndFilterDocumentItemByType(sedType: SEDType) = findAndFilterDocumentItemByType(sedType.name)

    private fun findAndFilterDocumentItemByType(sedType: String) =
            getDocuments().filter { it.type == sedType }.map { createShortDocument(it) }

    fun getSbdh(): List<Sbdh> {
        val lists = mutableListOf<Sbdh>()
        val documents = getDocuments()
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

    fun getInternatinalId() = getBuc().internationalId

    fun getParticipants() = getBuc().participants ?: emptyList()

    fun getBucAction() = getBuc().actions

    fun getAksjonListAsString() : List<String> {
        val keywordCreate = "Create"
        val actions = getBuc().actions ?: listOf()
        val createAkjsonsliste = mutableListOf<String>()
        for(item in actions) {
            if (item.documentType != null && item.name == keywordCreate) {
                createAkjsonsliste.add(item.documentType)
            }
        }
        val aksjonlist = createAkjsonsliste
                .sortedBy { it }
                .toList()

        logger.debug("Seds AksjonList size: ${aksjonlist.size}")
        return aksjonlist
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

    fun findNewParticipants(potentialNewParticipants: List<InstitusjonItem>): List<InstitusjonItem> {
        val currentParticipants =
                getParticipants()
                        .map {
                            InstitusjonItem(
                                    country = it.organisation?.countryCode ?: "",
                                    institution = it.organisation?.id ?: "",  //kan hende må være id?!
                                    name = it.organisation?.name ?: "" //name optinal
                            )
                        }
        if (currentParticipants.isEmpty() && potentialNewParticipants.isEmpty()) {
            throw ManglerDeltakereException("Ingen deltakere/Institusjon er tom")
        }
        return potentialNewParticipants.filter {
            candidate -> currentParticipants.none { current -> candidate.country == current.country && candidate.institution == current.institution }
        }
    }

}

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class ManglerDeltakereException(message: String) : IllegalStateException(message)
