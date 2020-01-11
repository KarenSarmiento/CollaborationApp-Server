import api.UpstreamRequestHandler
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import pki.PublicKeyManager
import utils.FirebasePacket
import utils.jsonStringToFirebasePacket
import utils.removeWhitespacesAndNewlines
import xmpp.FirebaseClient

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpstreamRequestHandlerTest {
    @Test
    fun `given an arbitrary firebase packet, an ack is sent with the correct params`() {
        // GIVEN
        val userFrom = "user-abc"
        val messageId = "123"
        val firebasePacket = jsonStringToFirebasePacket("""
            {
                "data": {
                    "upstream_type": "test",
                },
                "time_to_live": 1,
                "from": "$userFrom",
                "message_id": "$messageId",
                "category": "test-category"
            }
        """.trimIndent())
        val firebaseClientMock = spyk<FirebaseClient>()
        every { firebaseClientMock.sendAck(userFrom, messageId) } answers {}

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(firebaseClientMock, spyk(), firebasePacket)

        // THEN
        verify { firebaseClientMock.sendAck(userFrom, messageId) }
    }

    @Test
    fun `an upstream request to register a new notification key results in sending a successful response packet`() {
        testRegistrationOfPublicKey(true)
    }

    @Test
    fun `an upstream request to register an existing key results in sending a failure response packet`() {
        testRegistrationOfPublicKey(false)
    }

    private fun testRegistrationOfPublicKey(pkmRegistrationSuccess: Boolean) {
        // GIVEN
        val fcMock = spyk<FirebaseClient>()
        every { fcMock.sendJson(any()) } answers {}
        every { fcMock.sendAck(any(), any()) } answers {}
        val pkmMock = spyk<PublicKeyManager>()
        every { pkmMock.maybeAddPublicKey(any(), any()) } answers { pkmRegistrationSuccess }
        val ttl = 1
        val from = "user-abc"
        val messageId = "123"
        val messageType = null
        val publicKey = "key-456"
        val data = """
            {
                "upstream_type" : "new_public_key",
                "user_token" : "$from",
                "public_key" : "$publicKey"
            }
        """.trimIndent()
        val firebasePacket = FirebasePacket(data, ttl, from, messageId, messageType)

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(fcMock, pkmMock, firebasePacket)

        // THEN
        val expectedJson = removeWhitespacesAndNewlines("""
            {
                "to": "$from",
                "data": {
                    "json_type": "response",
                    "response_id": "$messageId",
                    "success": $pkmRegistrationSuccess
                }
            }
        """)
        verify(exactly = 1) {
            fcMock.sendJson(any()) // ack
            fcMock.sendJson(expectedJson)
        }
    }
}