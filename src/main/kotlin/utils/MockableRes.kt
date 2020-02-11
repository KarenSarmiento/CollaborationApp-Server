package utils

import api.EncryptedMessageHandler
import api.UpstreamRequestHandler
import devicegroups.GroupManager
import firebaseconnection.FirebaseXMPPClient
import pki.EncryptionManager
import pki.PublicKeyManager

object MockableRes {
    val fc = FirebaseXMPPClient
    val gm = GroupManager
    val pkm = PublicKeyManager
    val urh = UpstreamRequestHandler
    val em = EncryptionManager
    val emh = EncryptedMessageHandler
}