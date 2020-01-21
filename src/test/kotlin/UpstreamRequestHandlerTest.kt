import api.UpstreamRequestHandler
import devicegroups.GroupManager
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import pki.PublicKeyManager
import utils.JsonKeyword as Jk
import utils.jsonStringToJsonObject
import utils.removeWhitespacesAndNewlines
import firebaseconnection.FirebaseXMPPClient
import org.junit.jupiter.api.BeforeEach
import utils.MockableRes
import javax.json.Json

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpstreamRequestHandlerTest {

    private lateinit var mrMock: MockableRes

    @BeforeEach
    fun setUp() {

        // Create mocks for object classes.
        val fcMock = spyk<FirebaseXMPPClient>()
        val gmMock = spyk<GroupManager>()
        val pkmMock = spyk<PublicKeyManager>()
        val urhMock = spyk<UpstreamRequestHandler>()
        mrMock = spyk()
        every { mrMock.fc } answers { fcMock }
        every { mrMock.gm } answers { gmMock }
        every { mrMock.pkm } answers { pkmMock }
        every { mrMock.urh } answers { urhMock }

        // Prevent attempting to send JSONs or ACKs.
        every { mrMock.fc.sendJson(any()) } answers {}
        every { mrMock.fc.sendAck(any(), any()) } answers {}
    }

    @Test
    fun `given an arbitrary firebase packet, an ack is sent with the correct params`() {
        // GIVEN
        val userFrom = "user-abc"
        val messageId = "123"
        val upstreamJsonPacket = jsonStringToJsonObject("""
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

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, upstreamJsonPacket)

        // THEN
        verify { mrMock.fc.sendAck(userFrom, messageId) }
    }

    @Test
    fun `upstream requests of type forward_message are forwarded correctly`() {
        // GIVEN
        val deviceGroupId = "device-group-id"
        val deviceGroupToken = "device-group-token"
        val jsonUpdate = "test-update-json"
        val from = "user-1"

        every { mrMock.gm.getGroupKey(deviceGroupId) } returns deviceGroupToken

        val upstreamJsonPacket = jsonStringToJsonObject("""
            {
                "data": {
                    "upstream_type": "forward_message",
                    "forward_token_id": "$deviceGroupId",
                    "json_update": "$jsonUpdate"
                },
                "time_to_live": 1,
                "from": "$from",
                "message_id": "123",
                "category": "test-category"
            }
        """.trimIndent())

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, upstreamJsonPacket)

        // THEN
        val expectedJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$deviceGroupToken",
                "message_id": "(.)+",
                "data": \{
                    "downstream_type": "json_update",
                    "json_update": "$jsonUpdate",
                    "originator": "$from"
                \}
            \}
        """).toRegex()

        verify {
//            mrMock.fc.sendJson(any()) // ack
            mrMock.fc.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedJson
            } )
        }
    }

    @Test
    fun `given create_group upstream request, sends success message if valid`() {
        // GIVEN
        val groupId = "group-id"
        val groupName = "group-name"
        val email1 = "email-1"
        val email2 = "email-2"
        val notKey1 = "not_key-1"
        val from = "user"
        val requestId = "request-123"
        val groupKey = "groupKey-abc123"

        every { mrMock.pkm.getNotificationKey(email1) } returns notKey1
        every { mrMock.pkm.getNotificationKey(email2) } returns null
        every { mrMock.gm.maybeCreateGroup(any(), any()) } returns groupKey
        every { mrMock.gm.registerGroup(any(), any()) } answers {}

        val upstreamJsonPacket = jsonStringToJsonObject("""
            {
                "data": {
                    "upstream_type": "create_group",
                    "group_name": "$groupName",
                    "group_id": "$groupId",
                    "member_emails": "[\"$email1\", \"$email2\"]"
                },
                "time_to_live": 1,
                "from": "$from",
                "message_id": "$requestId",
                "category": "test-category"
            }
        """.trimIndent())

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, upstreamJsonPacket)

        // THEN
        verify { mrMock.gm.registerGroup(groupId, groupKey) }
        val expectedResponseJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$from",
                "message_id": "(.)+",
                "data": \{
                    "downstream_type": "create_group_response",
                    "request_id": "$requestId",
                    "group_name": "$groupName",
                    "group_id": "$groupId",
                    "success": true,
                    "failed_emails": \["$email2"\]
                \}
            \}
        """).toRegex()
        val expectedNotifyJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$notKey1",
                "message_id": "(.)+",
                "data": \{
                    "downstream_type": "added_to_group",
                    "group_name": "$groupName",
                    "group_id": "$groupId"
                \}
            \}
        """).toRegex()
        verify {
            mrMock.fc.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedResponseJson
            } )
            mrMock.fc.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedNotifyJson
            } )
        }
    }

    @Test
    fun `given create_group upstream request, sends failure message if invalid`() {
        // GIVEN
        val groupId = "group-id"
        val email1 = "email-1"
        val from = "user"
        val requestId = "request-123"

        every { mrMock.pkm.getNotificationKey(email1) } returns null
        every { mrMock.gm.maybeCreateGroup(any(), any()) } returns null

        val upstreamJsonPacket = jsonStringToJsonObject("""
            {
                "data": {
                    "upstream_type": "create_group",
                    "group_id": "$groupId",
                    "member_emails": "[\"$email1\"]"
                },
                "time_to_live": 1,
                "from": "$from",
                "message_id": "$requestId",
                "category": "test-category"
            }
        """.trimIndent())

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, upstreamJsonPacket)

        // THEN
        val expectedResponseJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$from",
                "message_id": "(.)+",
                "data": \{
                    "downstream_type": "create_group_response",
                    "request_id": "$requestId",
                    "success": false
                \}
            \}
        """).toRegex()
        verify {
            mrMock.fc.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedResponseJson
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

        every { mrMock.pkm.getNotificationKey(userEmail) } answers { notificationKey }

        val upstreamJsonPacket = jsonStringToJsonObject("""
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
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, upstreamJsonPacket)

        // THEN
        val expectedJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$from",
                "message_id": "(.)+",
                "data": \{
                    "downstream_type": "get_notification_key_response",
                    "success": true,
                    "notification_key": "$notificationKey",
                    "request_id": "$requestId"
                \}
            \}
        """).toRegex()

        verify {
            mrMock.fc.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedJson
            } )
        }
    }

    @Test
    fun `upstream requests of type get_notification_key return notification key if not valid`() {
        // GIVEN
        val userEmail = "email-abc"
        val from = "user-1"
        val requestId = "request-123"

        every { mrMock.pkm.getNotificationKey(userEmail) } answers { null }

        val upstreamJsonPacket = jsonStringToJsonObject("""
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
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, upstreamJsonPacket)

        // THEN
        val expectedJson = removeWhitespacesAndNewlines("""
            \{
                "to": "$from",
                "message_id": "(.)+",
                "data": \{
                    "downstream_type": "get_notification_key_response",
                    "success": false,
                    "request_id": "$requestId"
                \}
            \}
        """).toRegex()

        verify {
            mrMock.fc.sendJson( match {
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
        every { mrMock.pkm.maybeAddPublicKey(any(), any(), any()) } answers { pkmRegistrationSuccess }
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
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, requestJson)

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
        verify {
            mrMock.fc.sendJson( match {
                removeWhitespacesAndNewlines(it) matches expectedJson
            } )
        }
    }
}