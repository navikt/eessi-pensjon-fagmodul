package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.document.Retning
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ActionOperation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ActionsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Attachment
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ConversationsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Receiver
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Sender
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.VersionsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.VersionsItemNoUser
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class BucUtils(private val buc: Buc) {

    private val logger = LoggerFactory.getLogger(BucUtils::class.java)

    private fun getCreatorAsInstitusjonItem(): InstitusjonItem {
        return InstitusjonItem(
                country = buc.creator?.organisation?.countryCode ?: "",
                institution = buc.creator?.organisation?.id ?: "",
                name = buc.creator?.organisation?.name,
                acronym = buc.creator?.organisation?.acronym
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
                                name = it.organisation?.name ?: "", //name optinal
                                acronym = it.organisation?.acronym
                        )
                }.first()
        } catch (ex: Exception) {
            null
        }
    }

    private fun getDocuments(): List<DocumentsItem> {
        return if (buc.documents != null && buc.documents!!.isNotEmpty()) {
            buc.documents!!
        } else {
            createEmptyDocumentsForRina2020()
        }
    }

    fun isNewRina2020Buc() : Boolean {
        return buc.documents == null || (buc.documents != null && buc.documents!!.isEmpty())
    }

    private fun createEmptyDocumentsForRina2020() : List<DocumentsItem> {
        logger.debug("Kjører hjelpemetode for RINA2020")
        return when(getProcessDefinitionName()) {
            P_BUC_01.name -> createEmptyDocument(SedType.P2000)
            P_BUC_02.name -> createEmptyDocument(SedType.P2100)
            P_BUC_03.name -> createEmptyDocument(SedType.P2200)
            P_BUC_04.name -> createEmptyDocument(SedType.P1000)
            P_BUC_05.name -> createEmptyDocument(SedType.P8000)
            P_BUC_06.name -> createEmptyDocument(SedType.DummyChooseParts)
            P_BUC_07.name -> createEmptyDocument(SedType.P11000)
            P_BUC_08.name -> createEmptyDocument(SedType.P12000)
            P_BUC_09.name -> createEmptyDocument(SedType.P14000)
            P_BUC_10.name -> createEmptyDocument(SedType.P15000)
            else ->  emptyList()
        }
    }

    fun createEmptyDocument(sedType: SedType) : List<DocumentsItem> {
        return listOf(
            DocumentsItem(
            displayName = sedType.name,
            type = sedType,
            status = "empty",
            direction = "OUT" // DUMMY verdi
        ))
    }

    fun findDocument(documentId: String): DocumentsItem? =
            getAllDocuments().firstOrNull { it.id == documentId }

    fun getStartDateLong(): Long? {
        val date = buc.startDate
        return getDateTimeToLong(date)
    }

    fun getLastDateLong(): Long? {
        val date = buc.lastUpdate
        return getDateTimeToLong(date)
    }

    private fun getDateTimeToLong(dateTime: Any?): Long? {
        return getLocalDateTime(dateTime)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }

    fun getLocalDateTime(dateTime: Any?): LocalDateTime? {
        return when (dateTime) {
            is Long ->  Instant.ofEpochMilli(dateTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
            is String -> toLocalDatetimeFromString(dateTime)
            else -> null
        }
    }

    /***
     * Evaluates date formats and attempts to find the best match
     *
     * @param datetime, localdate or date with offsett
     * @return a LocalDateTime
     */
    private fun toLocalDatetimeFromString(dateTime: String): LocalDateTime? {
        checkdateFormat(dateTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME, OffsetDateTime::class)?.let { return it }
        checkdateFormat(dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd"), LocalDate::class)?.let{return it}
        checkdateFormat(dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"), OffsetDateTime::class)?.let { return it }

        return LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"))
    }

    private fun <T> checkdateFormat(dateStr: String, dateTimeFormatter: DateTimeFormatter, type : T): LocalDateTime? {
        try {
            if(type == LocalDate::class){
                return LocalDate.parse(dateStr,  dateTimeFormatter).atStartOfDay()
            }
            if(type == OffsetDateTime::class){
                return OffsetDateTime.parse(dateStr, dateTimeFormatter).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            }
        } catch (e: DateTimeParseException) {
            return null
        }
        return null
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
                allowsAttachments = overrideAllowAttachemnts(documentItem),
                direction = documentItem.direction,
                receiveDate = filterOutReceiveDateOnOut(documentItem.direction, getDateTimeToLong(documentItem.receiveDate))
            )

    fun filterOutReceiveDateOnOut(direction: String, receiveDate: Long?): Long? = if (direction == "OUT") null else receiveDate

    private fun checkParentDocumentId(type: SedType?, parentDocumentId: String?): String? {
        //fjerne parentid for ikke å opprette svarsed
        return when(type) {
            SedType.X009 -> null
            SedType.X012 -> null
            else -> parentDocumentId
        }
    }

    private fun overrideAllowAttachemnts(documentItem: DocumentsItem): Boolean? {
        return when(documentItem.type) {
            SedType.P5000 -> false // støtter ikke vedlegg
            SedType.X010 -> false  // støtter ikke vedlegg
            else -> documentItem.allowsAttachments
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

    fun getAllP6000AsDocumentItem(pdfurl: String) : List<P6000Dokument> {
        val documents = getAllDocuments().filter { doc -> doc.type == SedType.P6000 }.filter { it.status == "sent" || it.status == "received" }
        return documents.map { doc ->
                P6000Dokument(
                    type = doc.type!!,
                    bucid = getBuc().id!!,
                    documentID = doc.id!!,
                    fraLand = getDocumentSenderCountryCode(doc.participants),
                    sisteVersjon = doc.version ?: "1",
                    pdfUrl = "$pdfurl/buc/${getBuc().id}/sed/${doc.id}/pdf",
                    sistMottatt = getLocalDateTime(doc.lastUpdate)?.toLocalDate()!!,
                    retning = Retning.valueOf(doc.direction)
                )
        }
    }

    fun getDocumentSenderCountryCode(participants: List<ParticipantsItem?>?): String {
        return participants
            ?.filter { it?.role == "Sender" }
            ?.map { it?.organisation?.countryCode }
            ?.single()!!
    }


    fun getDocumentSenderOrganisation(participants: List<ParticipantsItem?>?): Organisation {
        val res =  participants
            ?.filter { it?.role == "Sender" }
            ?.mapNotNull { it?.organisation }
            ?.single()
        return res ?: Organisation(name = "Empty", id = "", countryCode = "")
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
            logger.error("En feil under sjekk av X100/X007", ex)
            return true
        }
        if (result != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Institusjon med id: $result, er ikke lenger i bruk. Da den er endret via en X100/X007")
        }
        return true
    }

    fun getGyldigeOpprettSedAksjonList() : List<SedType> {
        val action = getRinaAksjon()
        return action.filter { it.operation == ActionOperation.Create }
            .map { item -> item.documentType!! }
            .toList()
            .sorted()
    }

    fun getFiltrerteGyldigSedAksjonListAsString(): List<SedType> {
        val gyldigeSedList = getSedsThatCanBeCreated()
        val aksjonsliste = getGyldigeOpprettSedAksjonList()

        return if (SedType.DummyChooseParts in gyldigeSedList && gyldigeSedList.size == 1) {
            ValidBucAndSed.getAvailableSedOnBuc(buc.processDefinitionName)
                .also { logger.debug("benytter backupList : ${it.toJsonSkipEmpty()}") }
        } else if (aksjonsliste.isNotEmpty()) {
            logger.debug("benytter seg av aksjonliste: ${aksjonsliste.toJsonSkipEmpty()}")
            filterSektorPandRelevantHorizontalAndXSeds(aksjonsliste)
        } else {
            logger.debug("benytter seg av gyldigeSedList : ${gyldigeSedList.toJsonSkipEmpty()}")
            filterSektorPandRelevantHorizontalAndXSeds(gyldigeSedList)
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

    fun filterSektorPandRelevantHorizontalAndXSeds(list: List<SedType>): List<SedType> {
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

    fun isChildDocumentByParentIdBeCreated(parentId: String, sedType: SedType): Boolean {
        val possibleId = getAllDocuments().firstOrNull { docs-> docs.type == sedType && docs.parentDocumentId == parentId }?.id
        when (getRinaAksjon().firstOrNull { it.documentId == possibleId }?.operation) {
            ActionOperation.Create -> {
                return true
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$SedType kan ikke opaprettes i RINA (mulig det allerede finnes et utkast)")
        }
    }

    fun getRinaAksjon(): List<ActionsItem> {
        val actionlist = buc.actions ?: emptyList()
        return actionlist.asSequence()
            .filterNot { it.documentType == null }
            .toList()
            .sortedBy { it.documentType }
    }

    fun getRinaAksjonSedType(sedType: SedType) : List<ActionsItem> {
        return getRinaAksjon().filter { acition -> acition.documentType == sedType }
    }

    fun getRinaAksjonOperationSedType(sedType: SedType, operation: ActionOperation? = null) : List<ActionOperation> {
        return if (operation == null) {
            getRinaAksjonSedType(sedType).mapNotNull { it.operation }.toList().ifEmpty { emptyList() }
        } else {
            getRinaAksjonSedType(sedType).filter { it.operation == operation }.mapNotNull { it.operation }.ifEmpty { emptyList() }
        }
    }

    fun isValidSedtypeOperation(sedType: SedType, operation: ActionOperation) : Boolean {
        if (operation == ActionOperation.Create && getRinaAksjonOperationSedType(sedType, operation).isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Utkast av type $sedType, finnes allerede i BUC")
        } else if (getRinaAksjonOperationSedType(sedType, operation).isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Du kan ikke uføre action: $operation. for SED av type $sedType")
        } else {
            return true
        }
    }

    fun getParticipantsAsInstitusjonItem(): List<InstitusjonItem> {
        return getParticipants()
            .map {
                InstitusjonItem(
                    country = it.organisation?.countryCode ?: "",
                    institution = it.organisation?.id ?: "",  //kan hende må være id?!
                    name = it.organisation?.name ?: "" //name optinal
                )
            }
    }

    fun findNewParticipants(potentialNewParticipants: List<InstitusjonItem>): List<InstitusjonItem> {
        val currentParticipants = getParticipantsAsInstitusjonItem()
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
