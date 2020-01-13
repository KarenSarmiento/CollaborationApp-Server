import api.UpstreamRequestHandler
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import pki.PublicKeyManager
import utils.JsonKeyword as Jk
import utils.jsonStringToJson
import utils.removeWhitespacesAndNewlines
import xmpp.FirebaseClient
import javax.json.Json

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpstreamRequestHandlerTest {
    @Test
    fun `given an arbitrary firebase packet, an ack is sent with the correct params`() {
        // GIVEN
        val userFrom = "user-abc"
        val messageId = "123"
        val upstreamJsonPacket = jsonStringToJson("""
            {
                "data": {
                    "upstream_type": "test"
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
        UpstreamRequestHandler.handleUpstreamRequests(firebaseClientMock, spyk(), upstreamJsonPacket)

        // THEN
        verify { firebaseClientMock.sendAck(userFrom, messageId) }
    }

    @Test
    fun `upstream requests of type forward_message are forwarded correctly`() {
        // GIVEN
        val fcMock = spyk<FirebaseClient>()
        every { fcMock.sendJson(any()) } answers {}
        every { fcMock.sendAck(any(), any()) } answers {}
        val deviceGroupToken = "device-group-abc"
        val jsonUpdate = "test-update-json"
        val upstreamJsonPacket = jsonStringToJson("""
            {
                "data": {
                    "upstream_type": "forward_message",
                    "forward_token_id": "$deviceGroupToken",
                    "json_update": "$jsonUpdate"
                },
                "time_to_live": 1,
                "from": "user-1",
                "message_id": "123",
                "category": "test-category"
            }
        """.trimIndent())

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(fcMock, spyk(), upstreamJsonPacket)

        // THEN
        val expectedJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$deviceGroupToken",
                "message_id": "(.)+",
                "data": \{
                    "downstream_type": "json_update",
                    "json_update": "$jsonUpdate"
                \}
            \}
        """).toRegex()

        verify(exactly = 1) {
            fcMock.sendJson(any()) // ack
            fcMock.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedJson
            } )
        }
    }

    @Test
    fun `upstream requests of type get_notification_key return notification key if valid`() {
        // GIVEN
        val userEmail = "email-abc"
        val notificationKey = "not-key1"
        val from = "user-1"
        val requestId = "request-123"

        val fcMock = spyk<FirebaseClient>()
        every { fcMock.sendJson(any()) } answers {}
        every { fcMock.sendAck(any(), any()) } answers {}
        val pkmMock = spyk<PublicKeyManager>()
        every { pkmMock.getNotificationKey(userEmail) } answers { notificationKey }

        val upstreamJsonPacket = jsonStringToJson("""
            {
                "data": {
                    "upstream_type": "get_notification_key",
                    "email": "$userEmail"
                },
                "time_to_live": 1,
                "from": "$from",
                "message_id": "$requestId",
                "category": "test-category"
            }
        """.trimIndent())

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(fcMock, pkmMock, upstreamJsonPacket)

        // THEN
        val expectedJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$from",
                "message_id": "(.)+",
                "data": \{
                    "downstream_type": "get_notification_key_response",
                    "notification_key": "$notificationKey",
                    "request_id": "$requestId"
                \}
            \}
        """).toRegex()

        verify(exactly = 1) {
            fcMock.sendJson(any()) // ack
            fcMock.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedJson
            } )
        }
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
        every { pkmMock.maybeAddPublicKey(any(), any(), any()) } answers { pkmRegistrationSuccess }
        val from = "user-abc"
        val messageId = "123"
        val ttl = 1
        val category = "testCategory"
        val email = "test@google.com"
        val publicKey = "key-456"
        val requestJson = Json.createObjectBuilder()
            .add(Jk.FROM.text, from)
            .add(Jk.MESSAGE_ID.text, messageId)
            .add(Jk.TIME_TO_LIVE.text, ttl)
            .add(Jk.CATEGORY.text, category)
            .add(Jk.DATA.text, Json.createObjectBuilder()
                .add(Jk.UPSTREAM_TYPE.text, Jk.REGISTER_PUBLIC_KEY.text)
                .add(Jk.EMAIL.text, email)
                .add(Jk.PUBLIC_KEY.text, publicKey)
            ).build()

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(fcMock, pkmMock, requestJson)

        // THEN
        val expectedJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$from",
                "message_id": "(.)+",
                "data": \{
                    "downstream_type": "register_public_key_response",
                    "request_id": "$messageId",
                    "success": $pkmRegistrationSuccess
                \}
            \}
        """).toRegex()
        verify(exactly = 1) {
            fcMock.sendJson(any()) // ack
            fcMock.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedJson
            } )
        }
    }
}