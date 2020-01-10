package pki

import mu.KLogging

object PublicKeyManager : KLogging() {

    // TODO: Persist keys
    // Maps firebase instance id (notification key) to public key.
    private val userKeys: HashMap<String, String> = hashMapOf()

    fun addPublicKey(user: String, publicKey: String) {
        if (user in userKeys)
            logger.warn("User $user already has a registered public key.")
        else
        userKeys[user] = publicKey
    }

    fun getPublicKey(user: String): String? {
        val publicKey = userKeys[user]
        if (publicKey == null)
            logger.warn("No user has instance ID: $user.")
        return publicKey
    }

    fun updateNotificationKey(oldKey: String, newKey: String) {
        if (oldKey in userKeys) {
            val publicKey: String = userKeys[oldKey]!!
            userKeys.remove(oldKey)
            userKeys[newKey] = publicKey
        } else {
            logger.warn("No user has instance ID: $oldKey.")
        }

    }
}