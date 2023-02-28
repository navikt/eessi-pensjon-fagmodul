package no.nav.eessi.pensjon.fagmodul.eux.bucmodel

import com.fasterxml.jackson.annotation.JsonCreator
import no.nav.eessi.pensjon.eux.model.SedType

//class ActionsItem(
//
//        val documentType: SedType? = null,
//        val displayName: String? = null,
//        val documentId: String? = null,
//        val operation: ActionOperation? = null,
//)
//
//enum class ActionOperation {
//        Create,
//        Read,
//        Update,
//        Delete,
//        Send,
//        LocalClose, // Close Case (Local)
//        ReadParticipants,
//        SendParticipants; //Send (Participants_Send)
//
//        companion object {
//                @JvmStatic
//                @JsonCreator
//                fun from(s: String): ActionOperation? {
//                        return try {
//                                valueOf(s)
//                        } catch (e: Exception) {
//                                null
//                        }
//                }
//        }
//}