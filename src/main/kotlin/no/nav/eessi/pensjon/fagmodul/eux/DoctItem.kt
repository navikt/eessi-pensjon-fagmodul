package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.VersionsItemNoUser

data class DocItem(
    val type: SedType,
    val bucid: String,
    val documentID: String,
    val fraLand: String?,
    val sisteVersjon: VersionsItemNoUser?
)
