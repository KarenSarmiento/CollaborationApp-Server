package api

import mu.KLogging
import pki.PublicKeyManager
import utils.*
import xmpp.FirebaseClient
import javax.json.Json
import javax.json.JsonObject
import utils.JsonKeyword as Jk

/**
 *  Handles JSON-level messaging between client app instances and self.
 */
object UpstreamRequestHandler : KLogging() {

    /**
     *  Handles all upstream requests. Upstream packets come in the following form:
     *  {
     *      "from": "<user-instance-id>",
     *      "message_id": "<message-id>",
     *      "time_to_live": <ttl>,
     *      "category": "com.karensarmiento.collaborationapp"
     *      "data": {
     *          "upstream_type": <upstream-type>,
     *          <upstream-type-specific fields>
     *      }
     *  }
     *
     *  This method currently supports the following upstream types:
     *  - Registering public key for new notification token.
     */
    fun handleUpstreamRequests(fc: FirebaseClient, pkm: PublicKeyManager, packet: JsonObject) {
        logger.info("This message is an upstream message.")
        // All upstream packets must have to following fields. If null, then log error and return.
        val from = getStringOrNull(packet, Jk.FROM.text, logger) ?: return
        val messageId = getStringOrNull(packet, Jk.MESSAGE_ID.text, logger) ?: return
        val data = getJsonObjectOrNull(packet, Jk.DATA.text, logger) ?: return
        val upstreamType = getStringOrNull(data, Jk.UPSTREAM_TYPE.text, logger) ?: return

        fc.sendAck(from, messageId)

        // Determine type of upstream packet.
        when(upstreamType) {
            Jk.FORWARD_MESSAGE.text -> handleForwardMessageRequest(fc, data)
            Jk.GET_NOTIFICATION_KEY.text -> handleGetNotificationKeyRequest(fc, pkm, data, from, messageId)
            Jk.REGISTER_PUBLIC_KEY.text -> handleNewPublicKeyRequest(fc, pkm, data, from, messageId)
            else -> logger.warn("Upstream message type ${data.getString(Jk.UPSTREAM_TYPE.text)} unsupported.")
        }
    }

    /**
     *  Handle upstream client request to forward a json update.
     *
     *  @param fc FirebaseClient reference
     *  @param data JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "forward_message",
     *          "forward_token_id" : "<token-to-forward-to>"
     *          "json_update" : "<json-to-be-forwarded>"
     *      }
     *
     *  The forwarded packet would then be:
     *      {
     *          "to" : "<token-to-forward-to>",
     *          "message_id" : "<some-new-message-id>",
     *          "data" : {
     *              "json_update": "<json-to-be-forwarded>"
     *          }
     *      }
     */
    private fun handleForwardMessageRequest(fc: FirebaseClient, data: JsonObject) {
        val forwardId = getStringOrNull(data, Jk.FORWARD_TOKEN_ID.text, logger) ?: return
        val jsonUpdate = getStringOrNull(data, Jk.JSON_UPDATE.text, logger) ?: return
        val forwardJson = Json.createObjectBuilder()
            .add(Jk.TO.text, forwardId)
            .add(Jk.MESSAGE_ID.text, getUniqueId())
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.JSON_UPDATE.text, jsonUpdate)
            ).build().toString()
        fc.sendJson(forwardJson)
    }

    /**
     *  Handle upstream client request to find a users notification key.
     *
     *  @param fc FirebaseClient reference
     *  @param pkm PublicKeyManager reference.
     *  @param data JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "get_notification_key",
     *          "email" : "<user-email>"
     *      }
     *  @param userId notification key of user making request.
     *  @param requestId id of message containing request.
     *
     *  TODO: Add downstream type.
     *  The response would then be:
     *      {
     *          "to" : "<token-to-reply-to>",
     *          "message_id" : "<some-new-message-id>",
     *          "data" : {
     *              "notification_key": "<user-notification-key>"
     *              "request_id": "<message-id-of-incoming-request>"
     *          }
     *      }
     */
    private fun handleGetNotificationKeyRequest(
        fc: FirebaseClient, pkm: PublicKeyManager, data: JsonObject, userId: String, requestId: String) {
        val userEmail = getStringOrNull(data, Jk.EMAIL.text, logger) ?: return
        val notificationKey = pkm.getNotificationKey(userEmail)
        val responseJson = Json.createObjectBuilder()
            .add(Jk.TO.text, userId)
            .add(Jk.MESSAGE_ID.text, getUniqueId())
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.NOTIFICATION_KEY.text, notificationKey)
                .add(Jk.REQUEST_ID.text, requestId)
            ).build().toString()
        fc.sendJson(responseJson)
    }

    /**
     *  Handle upstream client request to register their public key.
     *
     *  @param fc FirebaseClient reference
     *  @param pkm PublicKeyManager reference.
     *  @param data JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "register_public_key",
     *          "public_key" : "<public-key>"
     *      }
     *  @param userId name of user requesting to register their public key.
     *  @param requestId requestId of request.
     */
    private fun handleNewPublicKeyRequest(
        fc: FirebaseClient, pkm: PublicKeyManager, data: JsonObject, userId: String, requestId: String) {
        val email = data.getString(Jk.EMAIL.text)
        val publicKey = data.getString(Jk.PUBLIC_KEY.text)
        val outcome = pkm.maybeAddPublicKey(email, userId, publicKey)
        sendRequestOutcomeResponse(fc, userId, requestId, outcome)
    }

    /**
     *  Sends message back to client informing them of an outcome to their message with some messageId.
     *  An example of a successful outcome is shown below:
     *      {
     *          "to": "<user-who-made-request>",
     *          "data": {
     *              "json_type": "response",
     *              "request_id": "<message-id-of-incoming-request>",
     *              "success": true
     *          }
     *      }
     *
     */
    private fun sendRequestOutcomeResponse(
        fc: FirebaseClient, userId: String, requestId: String, outcome: Boolean) {
        val responseJson = Json.createObjectBuilder()
            .add(Jk.TO.text, userId)
            .add(Jk.MESSAGE_ID.text, getUniqueId())
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.JSON_TYPE.text, Jk.RESPONSE.text)
                .add(Jk.REQUEST_ID.text, requestId)
                .add(Jk.SUCCESS.text, outcome)
            ).build().toString()
        fc.sendJson(responseJson)
    }

}
