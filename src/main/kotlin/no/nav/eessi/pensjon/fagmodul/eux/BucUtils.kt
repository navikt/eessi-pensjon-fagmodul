package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.eux.basismodel.RinaAksjon
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.*
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneId


class BucUtils(private val buc: Buc) {

    private val logger = LoggerFactory.getLogger(BucUtils::class.java)
    private val validbucsed = ValidBucAndSed()

    private fun getCreatorAsInstitusjonItem(): InstitusjonItem {
        return InstitusjonItem(
                country = buc.creator?.organisation?.countryCode ?: "",
                institution = buc.creator?.organisation?.id ?: "",
                name = buc.creator?.organisation?.name
        )
    }

    fun getCaseOwnerOrCreator() = getCaseOwner() ?: getCreatorAsInstitusjonItem()

    fun getCaseOwner() : InstitusjonItem? {
        return try {
                getParticipants()
                    .asSequence()
                    .filter { it.role == "CaseOwner" }
                    .map {
                        InstitusjonItem(
                                country = it.organisation?.countryCode ?: "",
                                institution = it.organisation?.id ?: "",  //kan hende må være id?!
                                name = it.organisation?.name ?: "" //name optinal
                        )
                }.first()
        } catch (ex: Exception) {
            null
        }
    }

    fun getCreatorContryCode(): Map<String, String> {
        val countryCode = buc.creator?.organisation?.countryCode ?: "N/A"
        return mapOf(Pair("countrycode", countryCode))
    }

    private fun getDocuments(): List<DocumentsItem> {
        return buc.documents ?: throw NoSuchFieldException("Fant ikke DocumentsItem")
    }

    fun findDocument(documentId: String): DocumentsItem? =
            getAllDocuments().firstOrNull { it.id == documentId }

    fun getStartDateLong(): Long {
        val date = buc.startDate
        return getDateTimeToLong(date)
    }

    fun getLastDateLong(): Long {
        val date = buc.lastUpdate
        return getDateTimeToLong(date)
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

    fun getProcessDefinitionName() = buc.processDefinitionName

    fun getProcessDefinitionVersion() = buc.processDefinitionVersion ?: ""

    fun findFirstDocumentItemByType(sedType: SEDType) = getDocuments().find { sedType == it.type }?.let { createShortDocument(it) }

    private fun createShortDocument(documentItem: DocumentsItem) =
            DocumentsItem(
                id = documentItem.id,
                parentDocumentId = documentItem.parentDocumentId,
                type = documentItem.type,
                displayName = documentItem.displayName,
                status = documentItem.status,
                creationDate = getDateTimeToLong(documentItem.creationDate),
                lastUpdate = getDateTimeToLong(documentItem.lastUpdate),
                participants = createParticipants(documentItem.conversations),
                attachments = createShortAttachemnt(documentItem.attachments),
                version = getLatestDocumentVersion(documentItem.versions),
                firstVersion = getFirstVersion(documentItem.versions),
                lastVersion = getLastVersion(documentItem.versions),
                allowsAttachments = overrideAllowAttachemnts(documentItem)
        )

    private fun overrideAllowAttachemnts(documentItem: DocumentsItem): Boolean? {
        return if (documentItem.type == SEDType.P5000) {
            false
        } else {
            documentItem.allowsAttachments
        }
    }

    private fun getLatestDocumentVersion(list: List<VersionsItem>?): String {
        return list?.sortedBy { it.id }
                ?.map { it.id }
                ?.lastOrNull() ?: "1"
    }

    private fun getLastVersion(list: List<VersionsItem>?): VersionsItemNoUser? {
        return list?.sortedBy { it.id }
                ?.map { VersionsItemNoUser(
                        id = it.id,
                        date = it.date
                )}
                ?.lastOrNull()
    }

    private fun getFirstVersion(list: List<VersionsItem>?): VersionsItemNoUser? {
        return list?.sortedBy { it.id }
                ?.map { VersionsItemNoUser(
                  id = it.id,
                  date = it.date
                )}
                ?.firstOrNull()
    }

    private fun createParticipants(conversations: List<ConversationsItem>?): List<ParticipantsItem?>? =
            if (conversations != null && conversations.any { it.userMessages != null }) {
                val conversation = conversations.findLast { it.userMessages != null }!!
                val userMessagesSent = conversation.userMessages!!
                val senders = userMessagesSent
                        .map { it.sender }
                        .distinctBy { it!!.id }
                        .map {
                            ParticipantsItem(
                                    role = "Sender",
                                    organisation = it as Sender,
                                    selected = false
                            )
                        }
                if (senders.isEmpty()) {
                    logger.info("No " + "Sender" + "s found for conversation: ${conversation.id}")
                }

                val userMessagesReceivedWithoutError = conversation.userMessages.filter { it.error == null }
                val receivers = userMessagesReceivedWithoutError
                        .map { it.receiver }
                        .distinctBy { it!!.id }
                        .map {
                            ParticipantsItem(
                                    role = "Receiver",
                                    organisation = it as Receiver,
                                    selected = false
                            )
                        }
                if (receivers.isEmpty()) {
                    logger.info("No " + "Receiver" + "s found for conversation: ${conversation.id}")
                }
                senders + receivers
            } else {
                conversations?.lastOrNull()?.participants
            }


    private fun createShortAttachemnt(attachments: List<Attachment>?) =
            attachments?.map {
                Attachment(
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

    fun getDocumentByType(sedType: SEDType): DocumentsItem? = getAllDocuments().firstOrNull { sedType == it.type && it.status != "empty" }

    fun getParticipants() = buc.participants ?: emptyList()

    fun checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(list: List<InstitusjonItem>): Boolean {
        val result = try {
            logger.debug("Sjekk på om newInstitusjonItem er dekativert ved mottatt x100")
            val newlistId = list.map { it.institution }
            buc.documents
                ?.asSequence()
                ?.filter { doc -> doc.type == SEDType.X100 || doc.type == SEDType.X007 && doc.status == "received" }
                ?.mapNotNull { doc -> doc.conversations }?.flatten()
                ?.mapNotNull { con -> con.userMessages?.map { um -> um.sender?.id } }?.flatten()
                ?.firstOrNull { senderId -> newlistId.contains(senderId) }
        } catch (ex: Exception) {
            logger.error("En feil under sjekk av Xsed", ex)
            return true
        }
        if (result != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Institusjon med id: $result, er ikke lenger i bruk. Da den er endret via en X-SED")
        }
        return true
    }

    private fun getGyldigeOpprettSedAksjonList() : List<SEDType> {
        val actions = buc.actions!!
        val keyWord = "Create"
        return actions.asSequence()
                .filter { item -> item.name == keyWord }
                .filterNot { item -> item.documentType == null }
                .map { item -> item.documentType!! }
                .toList()
                .sorted()
    }

    fun getFiltrerteGyldigSedAksjonListAsString(): List<SEDType> {
        val gyldigeSedList = getSedsThatCanBeCreated()
        val aksjonsliste = getGyldigeOpprettSedAksjonList()

        return if (SEDType.DummyChooseParts in gyldigeSedList && gyldigeSedList.size == 1) {
            validbucsed.getAvailableSedOnBuc(buc.processDefinitionName)
                .also { logger.debug("benytter backupList : ${it.toJsonSkipEmpty()}") }
        } else if (aksjonsliste.isNotEmpty()) {
            logger.debug("benytter seg av aksjonliste: ${aksjonsliste.toJsonSkipEmpty()}")
            filterSektorPandRelevantHorizontalSeds(aksjonsliste)
        } else {
            logger.debug("benytter seg av gyldigeSedList : ${gyldigeSedList.toJsonSkipEmpty()}")
            filterSektorPandRelevantHorizontalSeds(gyldigeSedList)
        }
    }

    fun getSedsThatCanBeCreated(): List<SEDType> {
        val keyWord = "empty"
        val docs = getAllDocuments()
        return docs.asSequence()
                .filter { item -> item.status == keyWord }
                .filterNot { item -> item.type == null }
                .map { item -> item.type!! }
                .toList()
                .sortedBy { it.name }
    }

    fun checkIfSedCanBeCreated(sedType: SEDType?, sakNr: String): Boolean {
        if (getFiltrerteGyldigSedAksjonListAsString().none { it == sedType }) {
            logger.warn("SED $sedType kan ikke opprettes, sjekk om den allerede finnes, sakNr: $sakNr ")
            throw SedDokumentKanIkkeOpprettesException("SED $sedType kan ikke opprettes i RINA (mulig det allerede finnes et utkast)")
        }
        return true
    }

    fun checkIfSedCanBeCreatedEmptyStatus(sedType: SEDType, parentId: String) : Boolean{
        if(getAllDocuments().any { it.parentDocumentId == parentId && it.type == sedType && it.status == "empty" }){
            return true
        }
        throw SedDokumentKanIkkeOpprettesException("SvarSED $sedType kan ikke opaprettes i RINA (mulig det allerede finnes et utkast)")
    }

    fun filterSektorPandRelevantHorizontalSeds(list: List<SEDType>): List<SEDType> {
        val gyldigSektorOgHSed: (SEDType) -> Boolean = { type ->
            type.name.startsWith("P")
                .or(type.name.startsWith("H12"))
                .or(type.name.startsWith("H07"))
                .or(type.name.startsWith("H02"))
        }

        return list
            .filter(gyldigSektorOgHSed)
            .sortedBy { it.name }
    }

    fun getRinaAksjon(): List<RinaAksjon> {
        val aksjoner = mutableListOf<RinaAksjon>()
        val actionitems = buc.actions
        actionitems?.forEach {
            if (it.documentType != null) {
                aksjoner.add(
                        RinaAksjon(
                                dokumentType = it.documentType,
                                navn = it.name,
                                dokumentId = it.documentId,
                                kategori = "Documents",
                                id = buc.processDefinitionName
                        )
                )
            }
        }
        return aksjoner.sortedBy { it.dokumentType?.name }
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

class ManglerDeltakereException(message: String) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)

class SedDokumentKanIkkeOpprettesException(message: String) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)
