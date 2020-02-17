package api

import mu.KLogging
import utils.*
import javax.json.*
import utils.JsonKeyword as Jk

/**
 *  Handles JSON-level messaging between client app instances and self.
 */
object UpstreamRequestHandler : KLogging() {

    /**
     *  Handles all upstream requests.
     *
     *  @param mr MockableRes reference.
     *  @param message A Json object describing the upstream packet. An example is shown below.
     *      {
     *          "from": "<user-instance-id>",
     *          "message_id": "<message-id>",
     *          "time_to_live": <ttl>,
     *          "category": "com.karensarmiento.collaborationapp"
     *          "data": {
     *              "enc_key": <aes-key-encrypted-with-server-public-key>
     *              "enc_message": <message-encrypted-with-aes-key>
     *          }
     *      }
     *
     *  The decrypted message should be in the following format:
     *      {
     *           "upstream_type": <upstream-type>,
     *           <upstream-type-specific fields>
     *      }
     *
     *  @param from User token of requesting device.
     *  @param email Email of requesting user.
     *  @param messageId Message Id of the request.
     */
    fun handleUpstreamRequests(mr: MockableRes, message: JsonObject, from: String, email: String, messageId: String) {
        // All upstream messages must be ACKed.
        mr.fc.sendAck(from, messageId)

        // Determine type of upstream packet.
        val upstreamType = getStringOrNull(message, Jk.UPSTREAM_TYPE.text, logger) ?: return

        logger.info("Received an upstream packet of type: $upstreamType")
        when(upstreamType) {
            Jk.FORWARD_MESSAGE.text -> handleForwardMessageRequest(mr, message, from)
            Jk.GET_NOTIFICATION_KEY.text -> handleGetNotificationKeyRequest(mr, message, from, email, messageId)
            Jk.REGISTER_PUBLIC_KEY.text -> handleRegisterPublicKeyRequest(mr, message, from, email, messageId)
            Jk.CREATE_GROUP.text -> handleCreateGroupRequest(mr, message, from, email, messageId)
            else -> logger.warn(
                "Received an unsupported upstream message type: $upstreamType.")
        }
    }

    /**
     *  Handle upstream client request to forward a json update.
     *
     *  @param mr MockableRes reference
     *  @param request JSON request from client. An example is shown below:
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
     *          "request" : {
     *              "downstream_type": "json_update",
     *              "json_update": "<json-to-be-forwarded>"
     *              "from": <token-belonging-to-sending-user>
     *          }
     *      }
     */
    private fun handleForwardMessageRequest(mr: MockableRes, request: JsonObject, from: String) {
        val jsonUpdate = getStringOrNull(request, Jk.JSON_UPDATE.text, logger) ?: return
        val groupId = getStringOrNull(request, Jk.FORWARD_TOKEN_ID.text, logger) ?: return
        mr.emh.sendEncryptedGroupMessage(mr, groupId, jsonUpdate, getUniqueId(), from)
    }

    /**
     *  Handle upstream request to create a new group with members corresponding to the given emails and the user userEmail.
     *
     *  @param mr MockableRes reference.
     *  @param request JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "create_group",
     *          "group_id": <group-id>
     *          "member_emails" : "["<user-userEmail-1>", "<user-userEmail-2>"]"
     *      }
     *  @param userToken notification key of user making request.
     *  @param userEmail email of user making request.
     *  @param requestId id of message containing request.
     *
     *  Then the response would indicate success or failure:
     *      {
     *          "to" : "<token-to-reply-to>",
     *          "message_id" : "<some-new-message-id>",
     *          "data" [ENCRYPTED]: {
     *              "downstream_type": "create_group_response",
     *              "request_id": "<message-id-of-incoming-request>"
     *              "group_name": "<human-readable-group-name>"
     *              "group_id": "<unique-group-id>"
     *              "failed_emails: []
     *              "success" : true
     *          }
     *      }
     */
    private fun handleCreateGroupRequest(
        mr: MockableRes, request: JsonObject, userToken: String, userEmail: String, requestId: String) {
        // Get request information.
        val groupId = getStringOrNull(request, Jk.GROUP_ID.text, logger) ?: return
        val memberEmailsString = getStringOrNull(request, Jk.MEMBER_EMAILS.text, logger) ?: return
        val memberEmails =  jsonStringToJsonArray(memberEmailsString)
        val groupName = getStringOrNull(request, Jk.GROUP_NAME.text, logger) ?: groupId

        // Request to create new group.
        val users = getTokensAndKeysForRegisteredUsers(mr, memberEmails)
        val memberTokens = getTokensAsJsonArray(users.registered, userToken)
        val groupKey = mr.gm.maybeCreateGroup(groupId, memberTokens)
        val members = createMembersJsonArray(mr, users.registered, userEmail, userToken)

        // Handle result and send responses.
        if (groupKey == null || members == null) {
            sendRequestOutcomeResponse(mr, Jk.CREATE_GROUP_RESPONSE.text, userToken, userEmail, requestId, false)
        } else {
            mr.gm.registerGroup(groupId, groupKey, users.registered.keys)

            // Notify requesting user of success.
            sendSuccessfulCreateGroupResponse(mr, userToken, userEmail, requestId, groupName, groupId, users.unregistered, members)

            // Notify added users that they have been added to a group.
            for ((email, user) in users.registered) {
                sendAddedToGroupMessage(mr, user.token, groupName, groupId, email, members)
            }
        }
    }

    private fun createMembersJsonArray(mr: MockableRes, successfulUsers: Map<String, UserContact>, requesterEmail: String, requesterToken: String): JsonArray? {
        val addedUsers = Json.createArrayBuilder()

        // Add requesting user to group if they are registered.
        val requesterPublicKey = mr.pkm.getPublicKey(requesterEmail) ?: return null
        addedUsers.add(Json.createObjectBuilder()
            .add(Jk.EMAIL.text, requesterEmail)
            .add(Jk.PUBLIC_KEY.text, requesterPublicKey)
            .add(Jk.NOTIFICATION_KEY.text, requesterToken))

        // Add all other members.
        for ((email, user) in successfulUsers) {
            addedUsers.add(Json.createObjectBuilder()
                .add(Jk.EMAIL.text, email)
                .add(Jk.PUBLIC_KEY.text, user.publicKey)
                .add(Jk.NOTIFICATION_KEY.text, user.token))
        }
        return addedUsers.build()
    }

    /**
     *  Identify which users are registered or not registered. Obtain token if they are registered.
     */
    private fun getTokensAndKeysForRegisteredUsers(mr: MockableRes, userEmails: JsonArray): Users {
        // Maps registered user emails to their tokens.
        val registeredUsers = mutableMapOf<String, UserContact>()
        // Create a JSON array containing all the unregistered emails.
        val unregisteredUsers = Json.createArrayBuilder()

        for (email in userEmails) {
            val emailString = email.toString().removeSurrounding("\"")
            val token = mr.pkm.getNotificationKey(emailString)
            val publicKey = mr.pkm.getPublicKey(emailString)
            if (token == null || publicKey == null)
                unregisteredUsers.add(emailString)
            else
                registeredUsers[emailString] = UserContact(token, publicKey)
        }
        return Users(registeredUsers, unregisteredUsers.build())
    }

    private fun getTokensAsJsonArray(emailsToTokens: MutableMap<String, UserContact>, requesterToken: String): JsonArray {
        val tokens = Json.createArrayBuilder()
        for ((_, user) in emailsToTokens) {
            tokens.add(user.token)
        }
        return tokens.add(requesterToken).build()
    }

    /**
     *  Send successful create group response.
     */
    private fun sendSuccessfulCreateGroupResponse(
        mr: MockableRes, userId: String, userEmail: String, requestId: String, groupName: String, groupId: String,
        failedEmails: JsonArray, successfulUsers: JsonArray) {
        val responseJson = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.CREATE_GROUP_RESPONSE.text)
            .add(Jk.REQUEST_ID.text, requestId)
            .add(Jk.GROUP_NAME.text, groupName)
            .add(Jk.GROUP_ID.text, groupId)
            .add(Jk.SUCCESS.text, true)
            .add(Jk.FAILED_EMAILS.text, failedEmails)
            .add(Jk.MEMBERS.text, successfulUsers)
            .build().toString()

        val messageId = getUniqueId()
        mr.emh.sendEncryptedResponseJson(mr, responseJson, userId, userEmail, messageId)

        logger.info("Sent successful group created response to $userEmail: ${prettyFormatJSON(responseJson)}")
    }

    /**
     *  Notify client that they have been added to a group.
     *
     *  @param mr MockableRes reference.
     *  @param to notification token belonging to user to be notified.
     *  @param groupName a non-unique human-readable string used to identify the group.
     *  @param groupId id of the group that they have been added to.
     *  @param email email belonging to the user who is receiving the response.
     *
     */
    private fun sendAddedToGroupMessage(
        mr: MockableRes, to: String, groupName: String, groupId: String, email: String, successfulUsers: JsonArray) {
        val responseJson = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.ADDED_TO_GROUP.text)
            .add(Jk.GROUP_NAME.text, groupName)
            .add(Jk.GROUP_ID.text, groupId)
            .add(Jk.MEMBERS.text, successfulUsers)
            .build().toString()

        val messageId = getUniqueId()
        mr.emh.sendEncryptedResponseJson(mr, responseJson, to, email, messageId)

        logger.info("Notified peer $email that they have been added to group $groupName: " +
                prettyFormatJSON(responseJson)
        )
    }

    /**
     *  Handle upstream client request to find a users notification key.
     *
     *  @param mr MockableRes reference.
     *  @param request JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "get_notification_key",
     *          "email" : "<user-email>"
     *      }
     *  @param userToken notification key of user making request.
     *  @param userEmail email of user making request.
     *  @param requestId id of message containing request.
     *
     *  The response would then be:
     *      {
     *          "to" : "<token-to-reply-to>",
     *          "message_id" : "<some-new-message-id>",
     *          "request" : {
     *              "downstream_type": "get_notification_key_response",
     *              "success": true,
     *              "notification_key": "<user-notification-key>",      // omitted if success is false.
     *              "request_id": "<message-id-of-incoming-request>"
     *          }
     *      }
     */
    private fun handleGetNotificationKeyRequest(
        mr: MockableRes, request: JsonObject, userToken: String, userEmail: String, requestId: String) {
        // Get notification key.
        val requestedEmail = getStringOrNull(request, Jk.EMAIL.text, logger) ?: return
        val notificationKey = mr.pkm.getNotificationKey(requestedEmail)

        // Send response.
        sendNotificationKeyResponse(mr, userToken, userEmail, requestId, notificationKey)
    }

    /**
     *  Send response to notification key request.
     *
     *  Success is implied by the notificationKey field: null implies the request was unsuccessful.
     */
    private fun sendNotificationKeyResponse(
        mr: MockableRes, userToken: String, userEmail: String, requestId: String, notificationKey: String? = null) {
        // Create response .
        val responseJson = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.GET_NOTIFICATION_KEY_RESPONSE.text)
            .add(Jk.REQUEST_ID.text, requestId)

        // All successful responses must include a notification key in the packet.
        if (notificationKey == null) {
            responseJson.add(Jk.SUCCESS.text, false)
        } else {
            responseJson.add(Jk.SUCCESS.text, true)
            responseJson.add(Jk.NOTIFICATION_KEY.text, notificationKey)
        }

        // Send encrypted response.
        val responseString = responseJson.build().toString()
        val messageId = getUniqueId()
        mr.emh.sendEncryptedResponseJson(mr, responseString, userToken, userEmail, messageId)
    }

    /**
     *  Handle upstream client request to register their public key.
     *
     *  @param mr MockableRes reference.
     *  @param request JSON request from client. An example is shown below:
     *      {
     *          "upstream_type" : "register_public_key",
     *          "email" : "<user-email>",
     *          "public_key" : "<public-key>"
     *      }
     *  @param userId name of user requesting to register their public key.
     *  @param userEmail email of user making request.
     *  @param requestId requestId of request.
     *
     *  Note that the notification key is given by the "from" entry in the packet (not request part).
     */
    private fun handleRegisterPublicKeyRequest(
        mr: MockableRes, request: JsonObject, userId: String, userEmail: String, requestId: String) {
        val email = getStringOrNull(request, Jk.EMAIL.text, logger) ?: return
        val publicKey = getStringOrNull(request, Jk.PUBLIC_KEY.text, logger) ?: return
        logger.info("Registering $email with notification key $userId and public key $publicKey")

        val outcome = mr.pkm.maybeAddPublicKey(email, userId, publicKey)
        sendRequestOutcomeResponse(mr, Jk.REGISTER_PUBLIC_KEY_RESPONSE.text, userId, userEmail, requestId, outcome)
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
        mr: MockableRes, downstreamType: String, userToken: String, userEmail: String, requestId: String, outcome: Boolean) {
        val responseJson = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, downstreamType)
            .add(Jk.REQUEST_ID.text, requestId)
            .add(Jk.SUCCESS.text, outcome)
            .build().toString()

        val messageId = getUniqueId()
        mr.emh.sendEncryptedResponseJson(mr, responseJson, userToken, userEmail, messageId)
    }

}
