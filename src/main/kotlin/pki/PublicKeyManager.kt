package pki

import mu.KLogging

object PublicKeyManager : KLogging() {

    // TODO: Persist keys
    // Maps firebase email to instance id (notification key) and public key.
    private val userKeys: HashMap<String, NotificationAndPublicKey> = hashMapOf()

    fun maybeAddPublicKey(email: String, notKey: String, publicKey: String): Boolean {
        if (email in userKeys) {
            logger.warn("User email $email already has a registered public key.")
            return false
        }
        userKeys[email] = NotificationAndPublicKey(notKey, publicKey)
        return true
    }

    fun getPublicKey(email: String): String? {
        val publicKey = userKeys[email]?.publicKey
        if (publicKey == null)
            logger.warn("No user has email: $email.")
        return publicKey
    }

    fun updateNotificationKey(email: String, newNotKey: String) {
        if (email in userKeys) {
            val publicKey: String = userKeys[email]!!.publicKey
            userKeys[email] = NotificationAndPublicKey(newNotKey, publicKey)
        } else {
            logger.warn("No user has email: $email.")
        }

    }

    fun getNotificationKey(email: String): String? {
        val notKey = userKeys[email]?.notificationKey
        if (notKey == null)
            logger.warn("No user has email: $email.")
        return notKey
    }
}

data class NotificationAndPublicKey(var notificationKey: String, val publicKey: String)