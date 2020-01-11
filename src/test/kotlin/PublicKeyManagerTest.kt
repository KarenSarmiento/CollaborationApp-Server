import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import pki.PublicKeyManager


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicKeyManagerTest {

    @Test
    fun `given a stored key, it can later be accessed`() {
        // GIVEN
        val user = "user1"
        val key = "key1"

        // WHEN
        PublicKeyManager.maybeAddPublicKey(user, key)

        // THEN
        val actualKey = PublicKeyManager.getPublicKey(user)
        assertEquals(key, actualKey)
    }

    @Test
    fun `prevents overwriting of existing key`() {
        // GIVEN
        val user = "user1"
        val key1 = "key1"
        val key2 = "key2"

        // WHEN
        PublicKeyManager.maybeAddPublicKey(user, key1)
        PublicKeyManager.maybeAddPublicKey(user, key2)

        // THEN
        val actualKey = PublicKeyManager.getPublicKey(user)
        assertEquals(key1, actualKey)
    }

    @Test
    fun `updates old notification key with a new one`() {
        // GIVEN
        val user1 = "user1"
        val user2 = "user2"
        val key = "key1"

        // WHEN
        PublicKeyManager.maybeAddPublicKey(user1, key)
        PublicKeyManager.updateNotificationKey(user1, user2)

        // THEN
        val actualOldKey = PublicKeyManager.getPublicKey(user1)
        val actualNewKey = PublicKeyManager.getPublicKey(user2)
        assertEquals(null, actualOldKey)
        assertEquals(key, actualNewKey)
    }

}
