import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import pki.EncryptionManager

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncryptionManagerTest {
    @Test
    fun encryptingPlaintextOutputsNonEqualCiphertextAES_GCM() {
        // GIVEN
        val plaintext = "This is a long secret test message. " +
                "This is a long secret test message. This is a long secret test message. " +
                "This is a long secret test message. This is a long secret test message."
        val secretKey = EncryptionManager.generateKeyAESGCM()

        // WHEN
        val ciphertext = EncryptionManager.encryptAESGCM(plaintext, secretKey)

        // THEN
        assertNotEquals(plaintext, ciphertext)
    }

    @Test
    fun decryptingCiphertextRecoversPlaintextAES_GCM() {
        // GIVEN
        val plaintext = "This is a long secret test message. " +
                "This is a long secret test message. This is a long secret test message. " +
                "This is a long secret test message. This is a long secret test message."
        val secretKey = EncryptionManager.generateKeyAESGCM()

        // WHEN
        val ciphertext = EncryptionManager.encryptAESGCM(plaintext, secretKey)
        val recoveredPlaintext = EncryptionManager.decryptAESGCM(ciphertext, secretKey)

        // THEN
        assertEquals(plaintext, recoveredPlaintext)
    }


    @Test
    fun encryptingPlaintextOutputsNonEqualCiphertextRSA() {
        // GIVEN
        val plaintext = "This is a secret test message."
        val keyPair = EncryptionManager.generateKeyPairRSA()
        val publicKey = EncryptionManager.keyAsString(keyPair!!.public)

        // WHEN
        val ciphertext = EncryptionManager.maybeEncryptRSA(plaintext, publicKey)

        // THEN
        assertNotEquals(plaintext, ciphertext)
    }

    @Test
    fun decryptingCiphertextRecoversPlaintextRSA() {
        // GIVEN
        val plaintext = "This is a secret test message."
        val keyPair = EncryptionManager.generateKeyPairRSA()
        val publicKey = EncryptionManager.keyAsString(keyPair!!.public)
        val privateKey = EncryptionManager.keyAsString(keyPair.private)
        val ciphertext = EncryptionManager.maybeEncryptRSA(plaintext, publicKey)

        // WHEN
        val recoveredPlaintext = EncryptionManager.maybeDecryptRSA(ciphertext!!, privateKey)

        // THEN
        assertEquals(plaintext, recoveredPlaintext)
    }
}
