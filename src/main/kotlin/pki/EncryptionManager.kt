package pki

import java.nio.ByteBuffer
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EncryptionManager {

    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_SIZE = 256
    private const val AES_IV_LENGTH = 12
    private const val AES_AUTH_TAG_LENGTH = 128

    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING"
    private const val RSA_KEY_SIZE = 2048

    const val PRIVATE_KEY = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCXiHloy5BbTv/YzIrZQvgbgtQdY3gpPrxYh5xX9XF716o32O5XplTaPlF4JjfjnNxADm99hN+1p1QF31Bt8ngbbD0WojrPWiuI6zz2k/H0XbwdarPtkpYMwD1NWLfqqCi9Do9CMm4SZEbY+ksVf09uyLIOV8jTstz9gudU7TY/2OOkHGPU17Ncd8mUh5MbQzDuoT+cTOfg3bYnfglo3PIjLyOfg3P9b6zJ61/RRm3blcgM+Upk5KdvKR6SIuE2KlI19JC+21XWTcxrXAXcbU5Ua55UEDzV3GkBELOlIc1zi0CibEvEKQOB7ffkwJ0hyiJbcC6kRW9DePtoEhjMkkSLAgMBAAECggEAMAu+DwuoOENKnMdx6OQyfaqULcNJqK4zEtDgsgTpGA6v8mguXg0nj1E+DJ31j44/SXIqSH6WXebxnbEKM+oyyeMeVWxXwEIDVrTbjgUnrMcBq8QWy6d0OxPC/CC6o8Twsc0JgEA0JVG1IvvtTaIhoesxhZmw2+q05g6Y4ZUa49AYobBTbUFVml7+R6v9IfjTnX7O1jQpEhdGYb5nc3Hdg9BQBDfsbS6BOKvyhVQY4anbe8eT693K9GqS8W7w/OIyWzg66bD1+PgOJFTXmCu+++bxnzH5vRCJSuwv8iRq0sk7thD36YQ2VR4p0EfMWK9hy4ZRl1ieRXDY5WhD6BbtwQKBgQDJ3XyO2fcB/vtit+NLRIOFwaq33wPqxClkRhiSOoS/IPtqcJkRn+eQiQY85THkS1rhk3a8sniATX+2X52JTZiyim1OyyPJkgDZLS6NTht2Q+lQL47luX66gHkyg8cwGHGwZskiL1TI5Ah4Wg8vVIl6SwadyUGPRJDbil4FUV7QywKBgQDAK5LW2gLmjbRgB0u79b7z+cmQnZ30imPHfdSAd0SUlezWTpWdpzxmZ4cqHRMi75t3rkGv5cNDL/VkLlVjsDpb/4rad2SXHYLg/hC98n6GsK4YhmQq8ALa/V+d+rItpkAJzSgyy/ordZYGF/HcLnjC4rK+hetht3mRe4l4YeqjQQKBgQCK+vvj2jtO23+2MsbBrnUi5PilyVyICPA6gmwuWS3F7W5LlSQ91yr1/vEVgfL8q8jxX7azKej+5NyV8nSi8JK98cJaKlAEWopM++d+EBWmMhFzTJsEnNacjxFibwn3mgzEF7BI4e9stFsEiXTE8F4KnZb7kXGasulMzZH39VLjSwKBgQCYAR96QSIwOgBmQP8f4wezNm7ArFwn9VttjdOL9ktR+LFI5wojlQgKvHNG1Y6wgLUJ2tVsjCKCv6msH5Y9b0UKRj0QB4aSna5Lx8t4ZBq+8XwUPCF5cTXhALAkZwuPXkSjPBtC6uOsgqszkLcoAb5V8TmPyKBiP92yPPSFO3Z8wQKBgQCm2g8RZFtIG1xJQzHq9spZsoBsPx808HallXF/XMIBnzpOjmBcQ2+FoPWYcZBX6e0tDBQldn/kjaN+ILQYYMNjh6NlfCMmWPB/QXD8IiYWP3FC62j47Ehmsrv0yhXF2zV8xh69fdvup4hG99sFJp6q/QOFlxn7CGUTI33wJqT9TA=="

    /**
     *  AES encryption
     */
    fun generateKeyAESGCM(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE)
        return keyGen.generateKey()
    }

    fun encryptAESGCM(plaintext: String, key: SecretKey): String {
        // Create an initialisation vector.
        val secureRandom = SecureRandom()
        val iv = ByteArray(AES_IV_LENGTH)
        secureRandom.nextBytes(iv)

        // Initialise cipher and encrypt.
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val paramSpec = GCMParameterSpec(AES_AUTH_TAG_LENGTH, iv) //128 bit auth tag length
        cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(charset("UTF-8")))

        // Concatenate initialisation vector with ciphertext.
        val byteBuffer = ByteBuffer.allocate(iv.size + ciphertext.size)
        byteBuffer.put(iv)
        byteBuffer.put(ciphertext)
        val cipherMessage = byteBuffer.array()

        // Encode to string.
        return String(Base64.getEncoder().encode(cipherMessage))
    }

    fun decryptAESGCM(ciphertext: String, key: SecretKey): String {
        // Decode from string.
        val encryptedBytes = Base64.getDecoder().decode(ciphertext)

        // Deconstruct the message.
        val byteBuffer = ByteBuffer.wrap(encryptedBytes)
        val iv = ByteArray(AES_IV_LENGTH)
        byteBuffer.get(iv)
        val cipherText = ByteArray(byteBuffer.remaining())
        byteBuffer.get(cipherText)

        // Initialise cipher and decrypt.
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AES_AUTH_TAG_LENGTH, iv))
        val plainText = cipher.doFinal(cipherText)

        // Encode to string.
        return String(plainText)
    }

    /**
     *  RSA encryption
     */
    fun maybeEncryptRSA(plaintext: String, publicKey: String): String? {
        var encryptedString: String? = null
        try {
            val key = stringToPublicKeyRSA(publicKey)
            encryptedString = encryptFromKeyRSA(plaintext, key)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return encryptedString?.replace("(\\r|\\n)".toRegex(), "")
    }

    fun maybeDecryptRSA(ciphertext: String, privateKey: String): String? {
        var decryptedString: String? = null
        try {
            val key = stringToPrivateKeyRSA(privateKey)
            decryptedString = decryptFromKeyRSA(ciphertext, key)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return decryptedString
    }

    fun generateKeyPairRSA(): KeyPair? {
        var kp: KeyPair? = null
        try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(RSA_KEY_SIZE)
            kp = kpg.generateKeyPair()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return kp
    }

    private fun stringToPublicKeyRSA(publicKey: String): PublicKey {
        val keyFac = KeyFactory.getInstance("RSA")
        // Convert the public key string into X509EncodedKeySpec format.
        val keySpec = X509EncodedKeySpec(
            Base64.getDecoder().decode(publicKey.toByteArray() //TODO: Check for surrounding spaces
            ) // key as bit string
        )
        // Create Android PublicKey object from X509EncodedKeySpec object.
        return keyFac.generatePublic(keySpec)
    }

    private fun stringToPrivateKeyRSA(privateKey: String): PrivateKey {
        val keyFac = KeyFactory.getInstance("RSA")
        // Convert the public key string into PKCS8EncodedKeySpec format.
        val keySpec = PKCS8EncodedKeySpec(
            Base64.getDecoder().decode(privateKey) //TODO: Check for surrounding spaces
        )
        // Create Android PrivateKey object from PKCS8EncodedKeySpec object.
        return keyFac.generatePrivate(keySpec)
    }

    private fun encryptFromKeyRSA(plaintext: String, publicKey: PublicKey): String {
        // Get an RSA cipher object and print the provider.
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        // Encrypt the plaintext and return as string.
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(charset("UTF-8")))
        return String(Base64.getEncoder().encode(encryptedBytes))
    }


    private fun decryptFromKeyRSA(ciphertext: String, privateKey: PrivateKey): String {
        // Get an RSA cipher object and print the provider
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        // Decrypt the plaintext and return as string.
        val encryptedBytes = Base64.getDecoder().decode(ciphertext)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }

    /**
     *  Useful functions.
     */

    fun keyAsString(key: Key): String {
        return String(Base64.getEncoder().encode(key.encoded))
    }
}