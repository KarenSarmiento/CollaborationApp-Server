import api.EncryptedMessageHandler
import api.UpstreamRequestHandler
import devicegroups.GroupManager
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import pki.PublicKeyManager
import firebaseconnection.FirebaseXMPPClient
import org.junit.jupiter.api.BeforeEach
import pki.EncryptionManager
import utils.JsonKeyword as Jk
import utils.MockableRes
import javax.json.Json

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpstreamRequestHandlerTest {

    private lateinit var mrMock: MockableRes
    private lateinit var userFrom: String
    private lateinit var userEmail: String
    private lateinit var messageId: String

    @BeforeEach
    fun setUp() {
        // Set fields.
        userFrom = "test-user"
        userEmail = "test-user@gmail.com"
        messageId = "upstream-message-id-123"

        // Create mocks for object classes.
        val fcMock = spyk<FirebaseXMPPClient>()
        val gmMock = spyk<GroupManager>()
        val pkmMock = spyk<PublicKeyManager>()
        val urhMock = spyk<UpstreamRequestHandler>(recordPrivateCalls = true)
        val emMock = spyk<EncryptionManager>()
        val emhMock = spyk<EncryptedMessageHandler>()
        mrMock = spyk()
        every { mrMock.fc } answers { fcMock }
        every { mrMock.gm } answers { gmMock }
        every { mrMock.pkm } answers { pkmMock }
        every { mrMock.urh } answers { urhMock }
        every { mrMock.em } answers { emMock }
        every { mrMock.emh } answers { emhMock }

        // Prevent attempting to send JSONs or ACKs.
        every { mrMock.fc.sendJson(any()) } answers {}
        every { mrMock.emh.sendEncryptedResponseJson(any(), any(), any(), any(), any()) } answers {}
        every { mrMock.emh.sendEncryptedGroupMessage(any(), any(), any(), any(), any()) } answers {}
        every { mrMock.fc.sendAck(any(), any()) } answers {}
    }

    @Test
    fun `given an arbitrary firebase packet, an ack is sent with the correct params`() {
        // GIVEN
        val message = Json.createObjectBuilder()
            .add(Jk.UPSTREAM_TYPE.text, "test")
            .build()

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, message, userFrom, userEmail, messageId)

        // THEN
        verify { mrMock.fc.sendAck(userFrom, messageId) }
    }

    @Test
    fun `upstream requests of type forward_message are forwarded correctly`() {
        // GIVEN
        val deviceGroupId = "device-group-id"
        val jsonUpdate = "{test-update-json}"
        val message = Json.createObjectBuilder()
            .add(Jk.UPSTREAM_TYPE.text, Jk.FORWARD_MESSAGE.text)
            .add(Jk.FORWARD_TOKEN_ID.text, deviceGroupId)
            .add(Jk.JSON_UPDATE.text, jsonUpdate)
            .build()

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, message, userFrom, userEmail, messageId)

        // THEN
        verify {
            mrMock.emh.sendEncryptedGroupMessage(mrMock, deviceGroupId, jsonUpdate, any(), userFrom)
        }
    }

    @Test
    fun `forward_to_peer request messages are correctly forwarded`() {
        // GIVEN
        val peerEmail = "peer-email"
        val peerMessage = "{hello there peer :)}"
        val peerToken = "peer-token"

        every { mrMock.pkm.getNotificationKey(peerEmail) } answers { peerToken }

        val message = Json.createObjectBuilder()
            .add(Jk.UPSTREAM_TYPE.text, Jk.FORWARD_TO_PEER.text)
            .add(Jk.PEER_EMAIL.text, peerEmail)
            .add(Jk.PEER_MESSAGE.text, peerMessage)
            .build()

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, message, userFrom, userEmail, messageId)

        // THEN
        val responseJson = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.FORWARD_TO_PEER.text)
            .add(Jk.PEER_MESSAGE.text, peerMessage)
            .build().toString()

        verify {
            mrMock.emh.sendEncryptedResponseJson(mrMock, responseJson, peerToken, peerEmail, any())
        }
    }

    @Test
    fun `given create_group upstream request, sends success message if valid`() {
        // GIVEN
        val userPublicKey = "user-public-key"
        val groupId = "group-id"
        val groupName = "group-name"
        val email1 = "email-1"
        val email2 = "email-2"
        val notKey1 = "not-key-1"
        val publicKey1 = "public-key-1"
        val publicKey2 = "public-key-2"
        val groupKey = "groupKey-abc123"

        every { mrMock.pkm.getPublicKey(userEmail) } returns userPublicKey
        every { mrMock.pkm.getPublicKey(email1) } returns publicKey1
        every { mrMock.pkm.getPublicKey(email2) } returns publicKey2
        every { mrMock.pkm.getNotificationKey(email1) } returns notKey1
        every { mrMock.pkm.getNotificationKey(email2) } returns null
        every { mrMock.gm.maybeCreateGroup(any(), any()) } returns groupKey
        every { mrMock.gm.registerGroup(any(), any(), any()) } answers {}

        val message = Json.createObjectBuilder()
            .add(Jk.UPSTREAM_TYPE.text, Jk.CREATE_GROUP.text)
            .add(Jk.GROUP_NAME.text, groupName)
            .add(Jk.GROUP_ID.text, groupId)
            .add(Jk.MEMBER_EMAILS.text, Json.createArrayBuilder()
                .add(email1)
                .add(email2).build().toString()
            ).build()

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, message, userFrom, userEmail, messageId)

        // THEN
        verify { mrMock.gm.registerGroup(groupId, groupKey, mutableSetOf(email1)) }
        val expectedResponseJson = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.CREATE_GROUP_RESPONSE.text)
            .add(Jk.REQUEST_ID.text, messageId)
            .add(Jk.GROUP_NAME.text, groupName)
            .add(Jk.GROUP_ID.text, groupId)
            .add(Jk.SUCCESS.text, true)
            .add(Jk.FAILED_EMAILS.text, Json.createArrayBuilder()
                .add(email2)
            )
            .add(Jk.MEMBERS.text, Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add(Jk.EMAIL.text, userEmail)
                    .add(Jk.PUBLIC_KEY.text, userPublicKey)
                    .add(Jk.NOTIFICATION_KEY.text, userFrom)
                )
                .add(Json.createObjectBuilder()
                    .add(Jk.EMAIL.text, email1)
                    .add(Jk.PUBLIC_KEY.text, publicKey1)
                    .add(Jk.NOTIFICATION_KEY.text, notKey1)
                )
            )
            .build().toString()
        verify { mrMock.emh.sendEncryptedResponseJson(mrMock, expectedResponseJson, userFrom, userEmail, any()) }
        val expectedNotifyJson = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.ADDED_TO_GROUP.text)
            .add(Jk.GROUP_NAME.text, groupName)
            .add(Jk.GROUP_ID.text, groupId)
            .add(Jk.MEMBERS.text, Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add(Jk.EMAIL.text, userEmail)
                    .add(Jk.PUBLIC_KEY.text, userPublicKey)
                    .add(Jk.NOTIFICATION_KEY.text, userFrom)
                )
                .add(Json.createObjectBuilder()
                    .add(Jk.EMAIL.text, email1)
                    .add(Jk.PUBLIC_KEY.text, publicKey1)
                    .add(Jk.NOTIFICATION_KEY.text, notKey1)
                )
            )
            .build().toString()
        verify { mrMock.emh.sendEncryptedResponseJson(mrMock, expectedNotifyJson, notKey1, email1, any()) }
    }

    @Test
    fun `given create_group upstream request, sends failure message if invalid`() {
        // GIVEN
        val userPublicKey = "user-public-key"
        val publicKey1 = "public-key-1"
        val groupId = "group-id"
        val groupName = "group-name"
        val email1 = "email-1"

        every { mrMock.pkm.getPublicKey(userEmail) } returns userPublicKey
        every { mrMock.pkm.getPublicKey(email1) } returns publicKey1
        every { mrMock.pkm.getNotificationKey(email1) } returns null
        every { mrMock.gm.maybeCreateGroup(any(), any()) } returns null

        val message = Json.createObjectBuilder()
            .add(Jk.UPSTREAM_TYPE.text, Jk.CREATE_GROUP.text)
            .add(Jk.GROUP_NAME.text, groupName)
            .add(Jk.GROUP_ID.text, groupId)
            .add(Jk.MEMBER_EMAILS.text, Json.createArrayBuilder()
                .add(email1).build().toString()
            ).build()

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, message, userFrom, userEmail, messageId)

        // THEN
        val expectedResponse = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.CREATE_GROUP_RESPONSE.text)
            .add(Jk.REQUEST_ID.text, messageId)
            .add(Jk.SUCCESS.text, false)
            .build().toString()
        verify { mrMock.emh.sendEncryptedResponseJson(mrMock, expectedResponse, userFrom, userEmail, any())}
    }

    @Test
    fun `upstream requests of type get_notification_key return notification key if valid`() {
        // GIVEN
        val notificationKey = "not-key1"
        val peerEmail = "friend@gmail.com"
        every { mrMock.pkm.getNotificationKey(peerEmail) } answers { notificationKey }

        val message = Json.createObjectBuilder()
            .add(Jk.UPSTREAM_TYPE.text, Jk.GET_NOTIFICATION_KEY.text)
            .add(Jk.EMAIL.text, peerEmail)
            .build()

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, message, userFrom, userEmail, messageId)

        // THEN
        val expectedResponse = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.GET_NOTIFICATION_KEY_RESPONSE.text)
            .add(Jk.REQUEST_ID.text, messageId)
            .add(Jk.SUCCESS.text, true)
            .add(Jk.NOTIFICATION_KEY.text, notificationKey)
            .build().toString()
        verify { mrMock.emh.sendEncryptedResponseJson(mrMock, expectedResponse, userFrom, userEmail, any()) }
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
        val publicKey = "key-456"
        every { mrMock.pkm.maybeAddPublicKey(any(), any(), any()) } answers { pkmRegistrationSuccess }
        val message = Json.createObjectBuilder()
            .add(Jk.UPSTREAM_TYPE.text, Jk.REGISTER_PUBLIC_KEY.text)
            .add(Jk.EMAIL.text, userEmail)
            .add(Jk.PUBLIC_KEY.text, publicKey)
            .build()

        // WHEN
        UpstreamRequestHandler.handleUpstreamRequests(mrMock, message, userFrom, userEmail, messageId)

        // THEN
        val expectedResponse = Json.createObjectBuilder()
            .add(Jk.DOWNSTREAM_TYPE.text, Jk.REGISTER_PUBLIC_KEY_RESPONSE.text)
            .add(Jk.REQUEST_ID.text, messageId)
            .add(Jk.SUCCESS.text, pkmRegistrationSuccess)
            .build().toString()

        verify { mrMock.emh.sendEncryptedResponseJson(mrMock, expectedResponse, userFrom, userEmail, any()) }
    }
}