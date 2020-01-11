package api

import mu.KLogging
import pki.PublicKeyManager
import utils.FirebasePacket
import utils.jsonStringToJson
import xmpp.FirebaseClient
import javax.json.Json
import javax.json.JsonObject
import utils.JsonKeyword as Jk

/**
 *  Handles JSON-level messaging between client app instances and self.
 */
object UpstreamRequestHandler : KLogging() {

    /**
     *  Handles all upstream requests. This currently supports:
     *  - Registering public key for new notification token.
     */
    fun handleUpstreamRequests(fc: FirebaseClient, pkm: PublicKeyManager, packet: FirebasePacket) {
        logger.info("This message is an upstream message.")
        fc.sendAck(packet.from, packet.messageId)

        // An upstream packet must have a JSON in the data component.
        val data = jsonStringToJson(packet.data)
        when(data.getString(Jk.UPSTREAM_TYPE.text)) {
            Jk.NEW_PUBLIC_KEY.text -> handleNewPublicKeyRequest(fc, pkm, data, packet.from, packet.messageId)
            else -> logger.warn("Upstream message type ${data.getString(Jk.UPSTREAM_TYPE.text)} unsupported.")
        }
    }

    /**
     *  Handle upstream client request to register their public key.
     *
     *  @param pkm PublicKeyManager reference.
     *  @param data JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "new_public_key",
     *          "user_token" : "<user-instance-id-token>",
     *          "public_key" : "<public-key>"
     *      }
     *  @param userId name of user requesting to register their public key.
     *  @param messageId messageId of request.
     */
    private fun handleNewPublicKeyRequest(fc: FirebaseClient, pkm: PublicKeyManager, data: JsonObject, userId: String, messageId: String) {
        val userToken = data.getString(Jk.USER_TOKEN.text)
        val publicKey = data.getString(Jk.PUBLIC_KEY.text)
        val outcome = pkm.maybeAddPublicKey(userToken, publicKey)
        sendRequestOutcomeResponse(fc, userId, messageId, outcome)
    }

    /**
     *  Sends message back to client informing them of an outcome to their message with some messageId.
     *  An example of a successful outcome is shown below:
     *      {
     *          "to": "user-abc",               // user who made request.
     *          "data": {
     *              "json_type": "response",    // indicates that this is response.
     *              "response_id": "123",       // message_id of request this is responding to.
     *              "success": true             // outcome.
     *          }
     *      }
     *
     */
    private fun sendRequestOutcomeResponse(
        fc: FirebaseClient, userId: String, requestId: String, outcome: Boolean) {
        val responseJson = Json.createObjectBuilder()
            .add(Jk.TO.text, userId)
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.JSON_TYPE.text, Jk.RESPONSE.text)
                .add(Jk.RESPONSE_ID.text, requestId)
                .add(Jk.SUCCESS.text, outcome)
            )
            .build().toString()

        fc.sendJson(responseJson)
    }

}
