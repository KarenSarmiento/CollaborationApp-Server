package api

import mu.KLogging
import utils.*
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
    fun handleUpstreamRequests(mr: MockableRes, packet: JsonObject) {
        // All upstream packets must have to following fields. If null, then log error and return.
        val from = getStringOrNull(packet, Jk.FROM.text, logger) ?: return
        val messageId = getStringOrNull(packet, Jk.MESSAGE_ID.text, logger) ?: return
        val data = getJsonObjectOrNull(packet, Jk.DATA.text, logger) ?: return
        val upstreamType = getStringOrNull(data, Jk.UPSTREAM_TYPE.text, logger) ?: return

        mr.fc.sendAck(from, messageId)

        // Determine type of upstream packet.
        logger.info("Received an upstream packet of type: $upstreamType")
        when(upstreamType) {
            Jk.FORWARD_MESSAGE.text -> handleForwardMessageRequest(mr, data)
            Jk.GET_NOTIFICATION_KEY.text -> handleGetNotificationKeyRequest(mr, data, from, messageId)
            Jk.REGISTER_PUBLIC_KEY.text -> handleRegisterPublicKeyRequest(mr, data, from, messageId)
            Jk.CREATE_GROUP.text -> handleCreateGroupRequest(mr, data, from, messageId)
            else -> logger.warn(
                "Received an unsupported upstream message type: ${data.getString(Jk.UPSTREAM_TYPE.text)}.")
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
     *              "downstream_type": "json_update",
     *              "json_update": "<json-to-be-forwarded>"
     *          }
     *      }
     */
    private fun handleForwardMessageRequest(mr: MockableRes, data: JsonObject) {
        // TODO: ForwardId would be group id. Remap this to group token.
        val forwardId = getStringOrNull(data, Jk.FORWARD_TOKEN_ID.text, logger) ?: return
        val jsonUpdate = getStringOrNull(data, Jk.JSON_UPDATE.text, logger) ?: return
        val forwardJson = Json.createObjectBuilder()
            .add(Jk.TO.text, forwardId)
            .add(Jk.MESSAGE_ID.text, getUniqueId())
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.DOWNSTREAM_TYPE.text, Jk.JSON_UPDATE.text)
                .add(Jk.JSON_UPDATE.text, jsonUpdate)
            ).build().toString()
        mr.fc.sendJson(forwardJson)
    }

    /**
     *  Handle upstream request to create a new group with members corresponding to the given emails and the user email.
     *
     *  @param fc FirebaseXMPPClient reference
     *  @param fhr FirebaseHTTPRequester reference
     *  @param data JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "create_group",
     *          "group_id": <group-id>
     *          "member_emails" : "["<user-email-1>", "<user-email-2>"]"
     *      }
     *  @param userId notification key of user making request.
     *  @param requestId id of message containing request.
     *
     *  Then the response would indicate success or failure:
     *      {
     *          "to" : "<token-to-reply-to>",
     *          "message_id" : "<some-new-message-id>",
     *          "data" : {
     *              "downstream_type": "create_group_response",
     *              "request_id": "<message-id-of-incoming-request>"
     *              "group_name": "<human-readable-group-name>"
     *              "group_id": "<unique-group-id>"
     *              "failed_emails: []
     *              "success" : true
     *          }
     *      }
     */
    private fun handleCreateGroupRequest(mr: MockableRes, data: JsonObject, userId: String, requestId: String) {
        val groupId = getStringOrNull(data, Jk.GROUP_ID.text, logger) ?: return
        val memberEmailsString = getStringOrNull(data, Jk.MEMBER_EMAILS.text, logger) ?: return
        val groupName = getStringOrNull(data, Jk.GROUP_NAME.text, logger) ?: groupId
        val memberEmails = jsonStringToJsonArray(memberEmailsString)

        // Add the sender to the JsonArray of members.
        val allMembersBuilder = Json.createArrayBuilder()
        val failedEmails = Json.createArrayBuilder()// assumes all emails sent to firebase are successfully added.
        for (peerEmail in memberEmails) {
            val peerEmailString = peerEmail.toString().removeSurrounding("\"")
            val peerToken = mr.pkm.getNotificationKey(peerEmailString)
            if (peerToken == null)
                failedEmails.add(peerEmailString)
            else
                allMembersBuilder.add(peerToken)
        }
        val allMembers = allMembersBuilder.add(userId).build()

        // Crate a new group and return result.
        val groupKey = mr.gm.maybeCreateGroup(groupId, allMembers)
        if (groupKey == null) {
            sendRequestOutcomeResponse(mr, Jk.CREATE_GROUP_RESPONSE.text, userId, requestId, false)
        } else {
            mr.gm.registerGroup(groupId, groupKey)
            val responseJson = Json.createObjectBuilder()
                .add(Jk.TO.text, userId)
                .add(Jk.MESSAGE_ID.text, getUniqueId())
                .add(Jk.DATA.text, Json.createObjectBuilder()
                    .add(Jk.DOWNSTREAM_TYPE.text, Jk.CREATE_GROUP_RESPONSE.text)
                    .add(Jk.REQUEST_ID.text, requestId)
                    .add(Jk.GROUP_NAME.text, groupName)
                    .add(Jk.GROUP_ID.text, groupId)
                    .add(Jk.SUCCESS.text, true)
                    .add(Jk.FAILED_EMAILS.text, failedEmails.build())
                ).build().toString()
            mr.fc.sendJson(responseJson)

            for (rawPeerToken in allMembers) {
                val peerToken = rawPeerToken.toString().removeSurrounding("\"")
                if (peerToken != userId)
                    sendAddedToGroupMesasage(mr, peerToken, groupName, groupId)
            }
        }
    }

    /**
     *  Notify client that they have been added to a group.
     *
     *  @param to notification token for client to be notified.
     *  @param groupName a non-unique human-readable string used to identify the group.
     *  @param groupId id of the group that they have been added to.
     *
     *  An example message is shown below:
     *      {
     *          "to" : "<token-of-client-added-to-group>",
     *          "message_id" : "<some-message-id>",
     *          "data" : {
     *              "downstream_type": "added_to_group",
     *              "group_id": "<group-id>"
     *              "group_name": "<group-name>"
     *          }
     *      }
     */
    private fun sendAddedToGroupMesasage(mr: MockableRes, to: String, groupName: String, groupId: String) {
        val responseJson = Json.createObjectBuilder()
            .add(Jk.TO.text, to)
            .add(Jk.MESSAGE_ID.text, getUniqueId())
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.DOWNSTREAM_TYPE.text, Jk.ADDED_TO_GROUP.text)
                .add(Jk.GROUP_NAME.text, groupName)
                .add(Jk.GROUP_ID.text, groupId)
            ).build().toString()
        mr.fc.sendJson(responseJson)
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
     *  The response would then be:
     *      {
     *          "to" : "<token-to-reply-to>",
     *          "message_id" : "<some-new-message-id>",
     *          "data" : {
     *              "downstream_type": "get_notification_key_response",
     *              "success": true,
     *              "notification_key": "<user-notification-key>",      // omitted if success is false.
     *              "request_id": "<message-id-of-incoming-request>"
     *          }
     *      }
     */
    private fun handleGetNotificationKeyRequest(mr: MockableRes, data: JsonObject, userId: String, requestId: String) {
        val userEmail = getStringOrNull(data, Jk.EMAIL.text, logger) ?: return
        val notificationKey = mr.pkm.getNotificationKey(userEmail)
        logger.info("Sending notification key for $userEmail to $userId")

        if (notificationKey == null) {
            val responseJson = Json.createObjectBuilder()
                .add(Jk.TO.text, userId)
                .add(Jk.MESSAGE_ID.text, getUniqueId())
                .add(Jk.DATA.text, Json.createObjectBuilder()
                    .add(Jk.DOWNSTREAM_TYPE.text, Jk.GET_NOTIFICATION_KEY_RESPONSE.text)
                    .add(Jk.SUCCESS.text, false)
                    .add(Jk.REQUEST_ID.text, requestId)
                ).build().toString()
            mr.fc.sendJson(responseJson)
        } else {
            val responseJson = Json.createObjectBuilder()
                .add(Jk.TO.text, userId)
                .add(Jk.MESSAGE_ID.text, getUniqueId())
                .add(Jk.DATA.text, Json.createObjectBuilder()
                    .add(Jk.DOWNSTREAM_TYPE.text, Jk.GET_NOTIFICATION_KEY_RESPONSE.text)
                    .add(Jk.SUCCESS.text, true)
                    .add(Jk.NOTIFICATION_KEY.text, notificationKey)
                    .add(Jk.REQUEST_ID.text, requestId)
                ).build().toString()
            mr.fc.sendJson(responseJson)
        }
    }

    /**
     *  Handle upstream client request to register their public key.
     *
     *  @param fc FirebaseClient reference
     *  @param pkm PublicKeyManager reference.
     *  @param data JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "register_public_key",
     *          "email" : "<user-email>",
     *          "public_key" : "<public-key>"
     *      }
     *  @param userId name of user requesting to register their public key.
     *  @param requestId requestId of request.
     *
     *  Note that the notification key is given by the "from" entry in the packet (not data part).
     */
    private fun handleRegisterPublicKeyRequest(mr: MockableRes, data: JsonObject, userId: String, requestId: String) {
        val email = data.getString(Jk.EMAIL.text)
        val publicKey = data.getString(Jk.PUBLIC_KEY.text)
        logger.info("Registering $email with notification key $userId and public key $publicKey")

        val outcome = mr.pkm.maybeAddPublicKey(email, userId, publicKey)
        sendRequestOutcomeResponse(mr, Jk.REGISTER_PUBLIC_KEY_RESPONSE.text, userId, requestId, outcome)
    }

    /**
     *  Sends message back to client informing them of an outcome to their message with some messageId.
     *  An example of a successful outcome is shown below:
     *      {
     *          "to": "<user-who-made-request>",
     *          "data": {
     *              "downstream_type": "register_public_key_response",
     *              "request_id": "<message-id-of-incoming-request>",
     *              "success": true
     *          }
     *      }
     *
     */
    private fun sendRequestOutcomeResponse(
        mr: MockableRes, downstreamType: String, userId: String, requestId: String, outcome: Boolean) {
        val responseJson = Json.createObjectBuilder()
            .add(Jk.TO.text, userId)
            .add(Jk.MESSAGE_ID.text, getUniqueId())
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.DOWNSTREAM_TYPE.text, downstreamType)
                .add(Jk.REQUEST_ID.text, requestId)
                .add(Jk.SUCCESS.text, outcome)
            ).build().toString()
        mr.fc.sendJson(responseJson)
    }

}
