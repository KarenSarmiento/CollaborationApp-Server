package api

import mu.KLogging
import utils.*
import javax.json.Json
import utils.JsonKeyword as Jk
import javax.json.JsonObject

object EncryptedMessageHandler : KLogging() {
    /**
     * Decrypts and handles an upstream message.
     */
    fun handleEncryptedMessage(mr: MockableRes, packet: JsonObject) {
        // All upstream packets must have to following fields. If null, then log error and return.
        val from = getStringOrNull(packet, Jk.FROM.text, logger) ?: return
        val messageId = getStringOrNull(packet, Jk.MESSAGE_ID.text, logger) ?: return

        // Decrypt data to find the request message JSON.
        val data = getJsonObjectOrNull(packet, Jk.DATA.text, logger) ?: return
        val email = getStringOrNull(data, Jk.EMAIL.text, logger) ?: return
        val message = mr.emh.getDecryptedMessage(mr, data) ?: return

        // Handle the decrypted request.
        mr.urh.handleUpstreamRequests(mr, message, from, email, messageId)
    }


    /**
     *  Decrypts data part of received packet and returns JSON object.
     */
    private fun getDecryptedMessage(mr: MockableRes, data: JsonObject): JsonObject? {
        // Get encrypted AES key and data.
        val encryptedKey = getStringOrNull(data, Jk.ENC_KEY.text, logger) ?: return null
        val encryptedMessage = getStringOrNull(data, Jk.ENC_MESSAGE.text, logger) ?: return null

        // Decrypt AES key and obtain SecretKey object.
        val decryptedKey = mr.em.maybeDecryptRSA(encryptedKey, mr.em.PRIVATE_KEY) ?: return null
        val secretKey = mr.em.stringToKeyAESGCM(decryptedKey)

        // Decrypt Message and return as JSON object.
        val decryptedMessage = mr.em.decryptAESGCM(encryptedMessage, secretKey)
        print("Decrypted and got: ${jsonStringToJsonObject(decryptedMessage)}")
        return jsonStringToJsonObject(decryptedMessage)
    }

    /**
     *  Send encrypted group message using symmetric group key.
     */
    fun sendEncryptedGroupMessage(mr: MockableRes, groupId: String, jsonUpdate: String, messageId: String, from: String) {
        val groupToken = mr.gm.getFirebaseId(groupId)
        if (groupToken == null) {
            logger.error("There exists no group registered with id: $groupId. Will ignore message.")
            return
        }
        val forwardJson = Json.createObjectBuilder()
            .add(Jk.TO.text, groupToken)
            .add(Jk.MESSAGE_ID.text, messageId)
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.DOWNSTREAM_TYPE.text, Jk.JSON_UPDATE.text)
                .add(Jk.JSON_UPDATE.text, jsonUpdate)
                .add(Jk.ORIGINATOR.text, from)
                .add(Jk.GROUP_ID.text, groupId)
            ).build().toString()
        // TODO: sendEncryptedGROUPJson instead - group key version
        mr.fc.sendJson(forwardJson)
    }

    /**
     *  Encrypt packet using user's public key, store this in a message, and send that to the user.
     */
    fun sendEncryptedResponseJson(mr: MockableRes, response: String, to: String, toEmail: String, messageId: String) {
        // Encrypt JSON response.
        val encryptedData = encryptMessage(mr, response, toEmail) ?: return

        // Create response.
        val responseJson = Json.createObjectBuilder()
            .add(Jk.TO.text, to)
            .add(Jk.MESSAGE_ID.text, messageId)
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.ENC_MESSAGE.text, encryptedData.message)
                .add(Jk.ENC_KEY.text, encryptedData.key)
            ).build().toString()

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