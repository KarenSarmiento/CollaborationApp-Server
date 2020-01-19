import api.UpstreamRequestHandler
import io.mockk.*
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.util.PacketParserUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import firebaseconnection.FirebaseXMPPClient
import org.junit.jupiter.api.Assertions.assertTrue
import utils.MockableRes
import utils.jsonStringToJsonObject
import utils.removeWhitespacesAndNewlines
import utils.JsonKeyword as Jk
import javax.json.Json


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirebaseXMPPClientTest {

    @Test
    fun `processStanza detects and handles arbitrary upstream packets (no message type)`() {
        // GIVEN
        val dataJsonString = "{\"upstream_type\":\"test\"}"
        val ttl = 1
        val userFrom = "user-abc"
        val messageId = "123"
        val xmlString = """
            <message xmlns="jabber:client" to="senderid@fcm.googleapis.com" from="devices@gcm.googleapis.com" type="normal">
                <gcm xmlns="google:mobile:data" xmlns:stream="http://etherx.jabber.org/streams">
                    {
                        "data": $dataJsonString,
                        "time_to_live": $ttl,
                        "from": "$userFrom",
                        "message_id": "$messageId"
                    }
                </gcm>
            </message>
        """.trimIndent().replace("\n", "").replace("\\s+".toRegex(), " ")
        val fcMock = spyk<FirebaseXMPPClient>()
        val urhMock = spyk<UpstreamRequestHandler>()
        every { urhMock.handleUpstreamRequests(any(), any()) } answers {}
        val mrMock = spyk<MockableRes>()
        every { mrMock.fc } answers { fcMock }
        every { mrMock.urh } answers { urhMock }

        val testStanza = PacketParserUtils.parseStanza<Stanza>(xmlString)

        // WHEN
        fcMock.processStanzaTestable(mrMock, testStanza)

        // THEN
        val dataJson = jsonStringToJsonObject(dataJsonString)
        val expectedRequestJson = Json.createObjectBuilder()
            .add(Jk.DATA.text, dataJson)
            .add(Jk.TIME_TO_LIVE.text, ttl)
            .add(Jk.FROM.text, userFrom)
            .add(Jk.MESSAGE_ID.text, messageId)
            .build()
        verify {urhMock.handleUpstreamRequests(mrMock, expectedRequestJson)}
    }

    // TODO: Test cases such as invalid user id or message id
    @Test
    fun `sendAck creates ack in correct format`() {
        // GIVEN
        val from = "user-abc"
        val messageId = "123"
        val fcMock = spyk<FirebaseXMPPClient>()
        val result = slot<Stanza>()
        every { fcMock["sendStanza"](capture(result)) } answers {}

        // WHEN
        fcMock.sendAck(from, messageId)

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
