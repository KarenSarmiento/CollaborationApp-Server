import api.EncryptedMessageHandler
import api.UpstreamRequestHandler
import devicegroups.GroupManager
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import firebaseconnection.FirebaseXMPPClient
import org.junit.jupiter.api.BeforeEach
import pki.EncryptionManager
import pki.PublicKeyManager
import utils.JsonKeyword as Jk
import utils.MockableRes
import utils.removeWhitespacesAndNewlines
import javax.json.Json


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncryptedMessageHandlerTest {

    private lateinit var mrMock: MockableRes

    @BeforeEach
    fun setUp() {
        // Create mocks for object classes.
        val fcMock = spyk<FirebaseXMPPClient>()
        val gmMock = spyk<GroupManager>()
        val pkmMock = spyk<PublicKeyManager>()
        val urhMock = spyk<UpstreamRequestHandler>()
        val emMock = spyk<EncryptionManager>()
        val emhMock = spyk<EncryptedMessageHandler>()
        mrMock = spyk()
        every { mrMock.fc } answers { fcMock }
        every { mrMock.gm } answers { gmMock }
        every { mrMock.pkm } answers { pkmMock }
        every { mrMock.urh } answers { urhMock }
        every { mrMock.em } answers { emMock }
        every { mrMock.emh } answers { emhMock }

        // Prevent actually handling upstream encrypted messages.
        every { mrMock.urh.handleUpstreamRequests(any(), any(), any(), any(), any()) } answers {}
        every { mrMock.fc.sendJson(any()) } answers {}
    }

    @Test
    fun `given register_public_key message, it is correctly decrypted and sent to be handled`() {
        val userFrom = "user-1"
        val userEmail = "test-email"
        val publicKey = "test-public-key"
        val messageId = "test-message-123"

        val requestJson = Json.createObjectBuilder()
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.ENC_KEY.text, """
                    OB1m0IpvN10kxWHAPJPg4PXd+xtc9pF5xcEqVXE0Lmnq/8XeywT/K09mXXGSibigcjugxPTwZUqR
                    atKJVyECyQnVBFRvqjhpplGmarQn3r7eCzR1Bb9Ai+YCAQR/coc2RlupOMSyWZJX6x7xBF4Iipql
                    xUjHr4tRDZZ3v9ALI70RDZ6qC6Ss2/gk04UOlZjJaZ7GS+eHGF3yDxVhv/pUcZSQ4HHYHNgOJUbF
                    6rK6J+poDATWUWOxeEhSOd0v22q1eKEVJvrgjm+UWI/oqZTw7ik6IvXmIYOSiaispXdT74WlImOE
                    odzEgj7bH9vJ7f+bYA4qRmThzzTJiUUze84bkQ==
                """.trimIndent())
                .add(Jk.ENC_MESSAGE.text, """
                    zKcacplnaHGCB47xFj+fYjXxG6jUqN7R6yD/Nb/lVSJh3HdoCrZ7OKxcb9bLk+JEvFSlWgVrVYr1
                    NR1hllKveVyCBOqXp/hdql3w2ctJs1WI3PeB1FQ2I4VWLzDjMWQHIacFIElt9xXrqvMixgmdq8t8
                    V3dZLic=
                """.trimIndent())
                .add(Jk.EMAIL.text, userEmail)
            )
            .add(Jk.TIME_TO_LIVE.text, 1)
            .add(Jk.FROM.text, userFrom)
            .add(Jk.MESSAGE_ID.text, messageId)
            .add(Jk.CATEGORY.text, "test-category")
            .build()


        // WHEN
        EncryptedMessageHandler.handleEncryptedMessage(mrMock, requestJson)

        // THEN
        val expectedDataJson = Json.createObjectBuilder()
            .add(Jk.UPSTREAM_TYPE.text, Jk.REGISTER_PUBLIC_KEY.text)
            .add(Jk.EMAIL.text, userEmail)
            .add(Jk.PUBLIC_KEY.text, publicKey)
            .build()
        verify { mrMock.urh.handleUpstreamRequests(mrMock, expectedDataJson, userFrom, userEmail, messageId) }
    }

    @Test
    fun `sendEncryptedJsonResponse encrypts and sends a json response`() {
        // GIVEN
        val response = "{test:json}"
        val to = "test-user"
        val toEmail = "test-user@gmail.com"
        val messageId = "test-message-123"
        val publicKey = """
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvyYNyotGPP78W55yNLLYJAbDsbNh+Vb0
            PB99r+ylWns8KNhLGa8JmN43ivgUGWCe+hcvBlC0pSjDWz8YFUOKLF8/B6gQrjvxc+zz0Q1dQIJe
            6/P43MvhW/fPbdH62sqpIx7P/NfD6EYI7g1ZPS5Xs09LJSI8an9wAlRKGhPoumUAbXJNFrHJK/mv
            66GTmeHZ5s+0TYWgqDJMk7hR3hPHqEvkHa8GrljII11GtPpN3t++R1McP7mot2KLPOh4LZONTfvk
            SXi79ngP2+mcgqZ7ivChbSDTIgno0CdS8McS1pH0MRquvjBhWZYbIEI9Ayvd9ML/a+lE/1O+BVps
            u3Lx0QIDAQAB
        """.trimIndent()
        every { mrMock.pkm.getPublicKey(toEmail) } answers { publicKey }

        // WHEN
        EncryptedMessageHandler.sendEncryptedResponseJson(mrMock, response, to, toEmail, messageId)

        // THEN
        // The actual encrypted string will not be verified due to randomness introduced by IV and AES key.
        val expectedJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$to",
                "message_id": "$messageId",
                "data": \{
                    "enc_message": "(.)+",
                    "enc_key": "(.)+
                \}
            \}
        """).toRegex()
        verify {
//            mrMock.em.generateKeyAESGCM()
//            mrMock.em.encryptAESGCM(response, any())
//            mrMock.em.maybeEncryptRSA(any(), publicKey)
            mrMock.fc.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedJson
            } )
        }
    }
}