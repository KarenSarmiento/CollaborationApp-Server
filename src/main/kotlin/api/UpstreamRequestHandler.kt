package api

import mu.KLogging
import org.json.JSONObject
import pki.PublicKeyManager
import utils.FirebasePacket
import xmpp.FirebaseClient
import utils.JsonKeyword as Jk

/**
 *  Handles JSON-level messaging between client app instances and self.
 */
object UpstreamRequestHandler : KLogging() {

    fun handleUpstreamRequests(firebaseClient: FirebaseClient, packet: FirebasePacket) {
        logger.info("This message is an upstream message.")
        firebaseClient.sendAck(packet.from, packet.messageId)

        // An upstream packet must have a JSON in the data component.
        val data = JSONObject(packet.data)
        when(data.getString(Jk.UPSTREAM_TYPE.text)) {
            Jk.NEW_PUBLIC_KEY.text -> handleNewPublicKeyRequest(data)
            else -> logger.warn("Upstream message type ${data.getString(Jk.UPSTREAM_TYPE.text)} unsupported.")
        }
    }

    /**
     *  Handle upstream client request to register their public key.
     *
     *  @param data JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "new_public_key",
     *          "user_token" : "<user-instance-id-token>",
     *          "public_key" : "<public-key>"
     *      }
     */
    private fun handleNewPublicKeyRequest(data: JSONObject) {
        val userToken = data.getString(Jk.USER_TOKEN.text)
        val publicKey = data.getString(Jk.PUBLIC_KEY.text)
        val result = PublicKeyManager.maybeAddPublicKey(userToken, publicKey)
        if (!result) {
            // TODO: Send error back.
        }
    }

}
