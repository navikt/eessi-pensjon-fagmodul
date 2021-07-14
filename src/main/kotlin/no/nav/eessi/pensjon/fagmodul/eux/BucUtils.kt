package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.RinaAksjon
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Attachment
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ConversationsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Receiver
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Sender
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.VersionsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.VersionsItemNoUser
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
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

    fun getBuc() = buc

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

    private fun getDocuments(): List<DocumentsItem> {
        return buc.documents ?: createEmptyDocumentsForRina2020()
    }

    fun isNewRina2020Buc() = buc.documents == null

    private fun createEmptyDocumentsForRina2020() : List<DocumentsItem> {
        logger.debug("Kjører hjelpemetode for RINA2020")
        return when(getProcessDefinitionName()) {
            BucType.P_BUC_01.name -> createEmptyDocument(SedType.P2000)
            BucType.P_BUC_02.name -> createEmptyDocument(SedType.P2100)
            BucType.P_BUC_03.name -> createEmptyDocument(SedType.P2200)
            BucType.P_BUC_04.name -> createEmptyDocument(SedType.P1000)
            BucType.P_BUC_05.name -> createEmptyDocument(SedType.P8000)
            BucType.P_BUC_06.name -> createEmptyDocument(SedType.DummyChooseParts)
            BucType.P_BUC_07.name -> createEmptyDocument(SedType.P11000)
            BucType.P_BUC_08.name -> createEmptyDocument(SedType.P12000)
            BucType.P_BUC_09.name -> createEmptyDocument(SedType.P14000)
            BucType.P_BUC_10.name -> createEmptyDocument(SedType.P15000)
            else ->  emptyList()
        }
    }

    fun createEmptyDocument(sedType: SedType) : List<DocumentsItem> {
        return listOf(
            DocumentsItem(
            displayName = sedType.name,
            type = sedType,
            status = "empty"
        ))
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

    fun getDateTime(dateTime: Any?): DateTime  {
        val zoneId = DateTimeZone.forID(ZoneId.systemDefault().id)

            return when (dateTime) {
                is Long -> DateTime(DateTime(dateTime).toInstant(),zoneId)
                is String -> DateTime(DateTime.parse(dateTime).toInstant(),zoneId)
                else -> DateTime.now().minusYears(1000)
            }
    }

    fun getProcessDefinitionName() = buc.processDefinitionName

    fun getProcessDefinitionVersion() = buc.processDefinitionVersion ?: ""

    fun findX005DocumentByTypeAndStatus() = getDocuments()
        .filter { SedType.X005 == it.type && (it.status != "sent" || it.status != "received" || it.status != "cancelled" || it.status != "active") }

    fun findFirstDocumentItemByType(SedType: SedType) = getDocuments().find { SedType == it.type }?.let { createShortDocument(it) }

    private fun createShortDocument(documentItem: DocumentsItem) =
            DocumentsItem(
                id = documentItem.id,
                parentDocumentId = checkParentDocumentId(documentItem.type, documentItem.parentDocumentId),
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

    private fun checkParentDocumentId(type: SedType?, parentDocumentId: String?): String? = if (type == SedType.X009) null else parentDocumentId


    private fun overrideAllowAttachemnts(documentItem: DocumentsItem): Boolean? {
        return if (documentItem.type == SedType.P5000) {
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

    fun getAllP6000AsDocumentItem() : List<P6000Dokument> {
        val documents = getAllDocuments().filter { doc -> doc.type == SedType.P6000 }.filter { it.status == "sent" || it.status == "received" }
        return documents.map {
                P6000Dokument(
                    type = it.type!!,
                    bucid = getBuc().id!!,
                    documentID = it.id!!,
                    fraLand = getDocumentSenderCountryCode(it.conversations),
                    sisteVersjon = getLastVersion(it.versions)?.id
                )
        }
    }

    fun getDocumentSenderCountryCode(conversations: List<ConversationsItem>?): String? {
        val participants = conversations?.findLast { it.participants != null }?.participants
        return participants
            ?.filter { it?.role == "Sender" }
            ?.map { it?.organisation?.countryCode }
            ?.single()
    }

    fun getAllDocuments() = getDocuments().map { createShortDocument(it) }

    fun getDocumentByType(SedType: SedType): DocumentsItem? = getAllDocuments().firstOrNull { SedType == it.type && it.status != "empty" }

    fun getParticipants() = buc.participants ?: emptyList()

    fun checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(list: List<InstitusjonItem>): Boolean {
        val result = try {
            logger.debug("Sjekk på om newInstitusjonItem er dekativert ved mottatt x100")
            val newlistId = list.map { it.institution }
            buc.documents
                ?.asSequence()
                ?.filter { doc -> (doc.type == SedType.X100 || doc.type == SedType.X007) && doc.status == "received" }
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

    private fun getGyldigeOpprettSedAksjonList() : List<SedType> {
        val actions = buc.actions ?: emptyList()
        val keyWord = "Create"
        return actions.asSequence()
                .filter { item -> item.name == keyWord }
                .filterNot { item -> item.documentType == null }
                .map { item -> item.documentType!! }
                .toList()
                .sorted()
    }

    fun getFiltrerteGyldigSedAksjonListAsString(): List<SedType> {
        val gyldigeSedList = getSedsThatCanBeCreated()
        val aksjonsliste = getGyldigeOpprettSedAksjonList()

        return if (SedType.DummyChooseParts in gyldigeSedList && gyldigeSedList.size == 1) {
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

    fun getSedsThatCanBeCreated(): List<SedType> {
        val keyWord = "empty"
        val docs = getAllDocuments()
        return docs.asSequence()
                .filter { item -> item.status == keyWord }
                .filterNot { item -> item.type == null }
                .map { item -> item.type!! }
                .toList()
                .sortedBy { it.name }
    }

    fun checkIfSedCanBeCreated(SedType: SedType?, sakNr: String): Boolean {
        if (getFiltrerteGyldigSedAksjonListAsString().none { it == SedType }) {
            logger.warn("SED $SedType kan ikke opprettes, sjekk om den allerede finnes, sakNr: $sakNr ")
            throw SedDokumentKanIkkeOpprettesException("SED $SedType kan ikke opprettes i RINA (mulig det allerede finnes et utkast)")
        }
        return true
    }

    fun sjekkOmSvarSedKanOpprettes(SedType: SedType, parentId: String) : Boolean{
        if(getAllDocuments().any { it.parentDocumentId == parentId && it.type == SedType && it.status == "empty" }){
            return true
        }
        throw SedDokumentKanIkkeOpprettesException("SvarSED $SedType kan ikke opaprettes i RINA (mulig det allerede finnes et utkast)")
    }

    fun filterSektorPandRelevantHorizontalSeds(list: List<SedType>): List<SedType> {
        val gyldigSektorOgHSed: (SedType) -> Boolean = { type ->
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
