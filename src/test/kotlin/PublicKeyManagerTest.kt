import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import pki.PublicKeyManager


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicKeyManagerTest {

    @Test
    fun `given a stored key, it can later be accessed`() {
        // GIVEN
        val email = "email1"
        val notKey = "user1"
        val publicKey = "key1"

        // WHEN
        PublicKeyManager.maybeAddPublicKey(email, notKey, publicKey)

        // THEN
        val actualKey = PublicKeyManager.getPublicKey(email)
        assertEquals(publicKey, actualKey)
    }

    @Test
    fun `prevents overwriting of existing key`() {
        // GIVEN
        val email = "email1"
        val notKey = "notKey1"
        val publicKey1 = "key1"
        val publicKey2 = "key2"

        // WHEN
        PublicKeyManager.maybeAddPublicKey(email, notKey, publicKey1)
        PublicKeyManager.maybeAddPublicKey(email, notKey, publicKey2)

        // THEN
        val actualKey = PublicKeyManager.getPublicKey(email)
        assertEquals(publicKey1, actualKey)
    }

    @Test
    fun `updates old notification key with a new one`() {
        // GIVEN
        val email = "email1"
        val notKey1 = "notKey1"
        val notKey2 = "notKey2"
        val publicKey = "key1"

        // WHEN
        PublicKeyManager.maybeAddPublicKey(email, notKey1, publicKey)
        PublicKeyManager.updateNotificationKey(email, notKey2)

        // THEN
        val actualNotKey = PublicKeyManager.getNotificationKey(email)
        assertEquals(notKey2, actualNotKey)
    }

}
