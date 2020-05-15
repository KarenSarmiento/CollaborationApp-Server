package api

import mu.KLogging
import pki.EncryptionManager
import pki.PublicKeyManager
import utils.*
import javax.json.Json
import utils.JsonKeyword as Jk
import javax.json.JsonObject

object EncryptedMessageHandler : KLogging() {
    /**
     * Decrypts and handles an upstream message.
     */
    fun handleEncryptedMessage(mr: MockableRes, packet: JsonObject) {
        logger.info("Received encrypted packet")
        // All upstream packets must have to following fields. If null, then log error and return.
        val from = getStringOrNull(packet, Jk.FROM.text, logger) ?: return
        val messageId = getStringOrNull(packet, Jk.MESSAGE_ID.text, logger) ?: return
        logger.info("with id $messageId")

        // Authenticate the message.
        var authenticated = false
        val data = getJsonObjectOrNull(packet, Jk.DATA.text, logger) ?: return
        val email = getStringOrNull(data, Jk.EMAIL.text, logger) ?: return
        val signature = getStringOrNull(data, Jk.SIGNATURE.text, logger)
        if (signature != null) {
            val encryptedMessage = getStringOrNull(data, Jk.ENC_MESSAGE.text, logger) ?: return
            val senderPublicKey = PublicKeyManager.getPublicKey(email) ?: return
            if (!mr.em.authenticateSignature(signature, encryptedMessage, senderPublicKey)) return
            authenticated = true
        }

        // Decrypt data to find the request message JSON.
        val message = mr.emh.getDecryptedMessage(mr, data) ?: return

        // Handle the decrypted request.
        mr.urh.handleUpstreamRequests(mr, message, from, email, messageId, authenticated)
    }

    /**
     *  Decrypts data part of received packet and returns JSON object.
     */
    private fun getDecryptedMessage(mr: MockableRes, data: JsonObject): JsonObject? {
        // Get encrypted AES key and data.
        val encryptedKey = getStringOrNull(data, Jk.ENC_KEY.text, logger) ?: return null
        val encryptedMessage = getStringOrNull(data, Jk.ENC_MESSAGE.text, logger) ?: return null

        // Decrypt AES key and obtain SecretKey object.
        val decryptedKey = mr.em.maybeDecryptRSA(encryptedKey, mr.em.PRIVATE_KEY_STRING) ?: return null
        val secretKey = mr.em.stringToKeyAESGCM(decryptedKey)

        // Decrypt Message and return as JSON object.
        val decryptedMessage = mr.em.decryptAESGCM(encryptedMessage, secretKey)
        return jsonStringToJsonObject(decryptedMessage)
    }

    /**
     *  Send encrypted group message using symmetric group key.
     */
    fun sendEncryptedGroupMessage(mr: MockableRes, groupId: String, groupMessage: JsonObject, from: String) {
        // Get members of group.
        val members = mr.gm.getGroupMembers(groupId)
        if (members == null) {
            logger.error("Could not get members for group with id: $groupId. Will ignore message.")
            return
        }

        // Construct message to send.
        val forwardMessage = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.FORWARD_TO_GROUP.text)
            .add(Jk.GROUP_MESSAGE.text, groupMessage)
            .add(Jk.ORIGINATOR.text, from)
            .add(Jk.GROUP_ID.text, groupId)
            .build().toString()

        // For each member, send an encrypted message.
        for (memberEmail in members) {
            logger.info("Sending encrypted group message to $memberEmail")
            val memberToken = mr.pkm.getNotificationKey(memberEmail)
            if (memberToken != from && memberToken != null) {
                val messageId = getUniqueId()
                sendEncryptedResponseJson(mr, forwardMessage, memberToken, memberEmail, messageId)
            }
        }
    }

    /**
     *  Encrypt packet using user's public key, store this in a message, and send that to toToken.
     */
    fun sendEncryptedResponseJson(mr: MockableRes, response: String, toToken: String, toEmail: String, messageId: String) {
        // Encrypt JSON response.
        val encryptedData = encryptMessage(mr, response, toEmail) ?: return

        // Create digital signature.
        val signature = EncryptionManager.createDigitalSignature(encryptedData.message)
        // Create response.
        val responseJson = Json.createObjectBuilder()
            .add(Jk.TO.text, toToken)
            .add(Jk.MESSAGE_ID.text, messageId)
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.ENC_MESSAGE.text, encryptedData.message)
                .add(Jk.ENC_KEY.text, encryptedData.key)
                .add(Jk.SIGNATURE.text, signature)
            ).add(Jk.ANDROID.text, Json.createObjectBuilder()
                .add(Jk.PRIORITY.text, Jk.NORMAL.text)
            ).build().toString()

        logger.info("Sent message to $toEmail")
        // Send encrypted JSON.
        mr.fc.sendJson(responseJson)
    }

    private fun encryptMessage(mr: MockableRes, value: String, email: String): EncryptedData? {
        val publicKey = mr.pkm.getPublicKey(email)
        if (publicKey == null) {
            logger.error(
                "Cannot send response to $email since they do not have a registered public key." +
                    " No response will be sent.")
            return null
        }
        val aesKey = mr.em.generateKeyAESGCM()
        val encryptedValue = mr.em.encryptAESGCM(value, aesKey)
        val aesKeyString = mr.em.keyAsString(aesKey)
        val encryptedKey = mr.em.maybeEncryptRSA(aesKeyString, publicKey)
        if (encryptedKey == null) {
            UpstreamRequestHandler.logger.error("Could not encrypt request. Will not send message.")
            return null
        }
        return EncryptedData(encryptedKey, encryptedValue)
    }

    data class EncryptedData(val key: String, val message: String)
}