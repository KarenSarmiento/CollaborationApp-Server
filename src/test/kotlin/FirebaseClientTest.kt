import api.UpstreamRequestHandler
import io.mockk.*
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.util.PacketParserUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import xmpp.FirebaseClient
import org.junit.jupiter.api.Assertions.assertTrue
import pki.PublicKeyManager
import utils.FirebasePacket
import utils.removeWhitespacesAndNewlines


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirebaseClientTest {

    @Test
    fun `processStanza detects and handles arbitrary upstream packets (no message type)`() {
        // GIVEN
        val dataJson = "{\"upstream_type\":\"test\"}"
        val ttl = 1
        val userFrom = "user-abc"
        val messageId = "123"
        val messageType = null
        val xmlString = """
            <message xmlns="jabber:client" to="senderid@fcm.googleapis.com" from="devices@gcm.googleapis.com" type="normal">
                <gcm xmlns="google:mobile:data" xmlns:stream="http://etherx.jabber.org/streams">
                    {
                        "data": $dataJson,
                        "time_to_live": "$ttl",
                        "from": "$userFrom",
                        "message_id": "$messageId"
                    }
                </gcm>
            </message>
        """.trimIndent().replace("\n", "").replace("\\s+".toRegex(), " ")
        val firebaseClientMock = spyk<FirebaseClient>()
        val pkmMock = spyk<PublicKeyManager>()
        val urhMock = spyk<UpstreamRequestHandler>()
        every { urhMock.handleUpstreamRequests(any(), any(), any()) } answers {}
        val testStanza = PacketParserUtils.parseStanza<Stanza>(xmlString)

        // WHEN
        firebaseClientMock.processStanzaTestable(urhMock, pkmMock, testStanza)

        // THEN
        val expectedFirebasePacket = FirebasePacket(dataJson, ttl, userFrom, messageId, messageType)
        verify {urhMock.handleUpstreamRequests(firebaseClientMock, pkmMock, expectedFirebasePacket)}
    }

    // TODO: Test cases such as invalid user id or message id
    @Test
    fun `sendAck creates ack in correct format`() {
        // GIVEN
        val from = "user-abc"
        val messageId = "123"
        val firebaseClientMock = spyk<FirebaseClient>()
        val result = slot<Stanza>()
        every { firebaseClientMock["sendStanza"](capture(result)) } answers {}

        // WHEN
        firebaseClientMock.sendAck(from, messageId)

        // THEN
        val actualAck = removeWhitespacesAndNewlines(result.captured.toXML(null).toString())
        val expectedAckRegex = removeWhitespacesAndNewlines("""
            <message xmlns='jabber:client' id='(.)+'>
                <gcm xmlns="google:mobile:data">
                    \{
                        "message_type":"ack",
                        "from":"user-abc",
                        "message_id":"123"
                    \}
                </gcm>
            </message>
        """).toRegex()
        assertTrue(expectedAckRegex matches actualAck)
    }
}
