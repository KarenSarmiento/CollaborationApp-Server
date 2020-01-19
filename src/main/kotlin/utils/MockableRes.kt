package utils

import api.UpstreamRequestHandler
import devicegroups.GroupManager
import firebaseconnection.FirebaseXMPPClient
import pki.PublicKeyManager

object MockableRes {
    val fc = FirebaseXMPPClient
    val gm = GroupManager
    val pkm = PublicKeyManager
    val urh = UpstreamRequestHandler
}