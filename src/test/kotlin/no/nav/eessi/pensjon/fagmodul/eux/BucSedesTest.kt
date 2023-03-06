package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.buc.*
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Test

class BucSedesTest {


    @Test
    fun `Buc Serdes`() {
        val serialized = javaClass.getResource("/json/buc/Buc.json")!!.readText()

        mapJsonToAny<Buc>(serialized, false)

//        val buc = Buc(
//            id = "1443996",
//            startDate = "2023-02-23T11:46:35.292+00:00",
//            lastUpdate = "2023-02-23T11:46:35.589+00:00",
//            status = "open",
//            subject = Subject(
//                birthday = "1967-04-09",
//                surname = "Plyndring",
//                sex = "m",
//                name = "Gøyal",
//                pid = "49446723137",
//                address = Address(
//                    country = "TZ",
//                    town = "CAPITAL WEST",
//                    street = "1KOLEJOWA 6/5",
//                    postalCode = "3000",
//                    region = "18-500 KOLNO",
//                )
//            ),
//            creator = Creator(
//                name = "system",
//                id = "NO:NAVAT07",
//                type = "User",
//                organisation = Organisation(
//                    address = Address(),
//                    activeSince = "",
//                    acronym = "",
//                    countryCode = "",
//                    name = "",
//                    id = ""
//
//                )
//            ),
//            documents = listOf(
//                DocumentsItem(
//                    conversations = listOf(
//                        ConversationsItem(
//                            userMessages = listOf(
//                                UserMessagesItem()
//                            ),
//                            id = "1443996",
//                            participants = listOf(
//                                Participant(
//                                    role = "Sender",
//                                    organisation = Organisation(
//                                        address = Address()
//                                    ),
//                                    selected = false
//                                )
//                        ),
//                    )),
//                )
//            ),
//            participants = null,
//            processDefinitionName = null,
//            applicationRoleId = null,
//            businessId = null,
//            internationalId = null,
//            processDefinitionVersion = null,
//            comments = null,
//            actions = null,
//            attachments = null,
//        )



    }
}