import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import xmpp.FirebaseClient
import org.jivesoftware.smack.util.PacketParserUtils
import org.junit.jupiter.api.Assertions.assertTrue


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirebaseClientTest {

    @Test
    fun `given an upstream packet, processStanza sends an ack with correct params`() {
        // GIVEN
        // Mock xmpp connection and store calls to sendStanza.
        val xmppConnMock = mockk<XMPPTCPConnection>()
        val result = slot<Stanza>()
        every { xmppConnMock.sendStanza(capture(result)) } answers {}
        val firebaseClient = FirebaseClient(xmppConnMock)
        // Mock receipt of the following message
        val xmlString = """
            <message xmlns="jabber:client" to="senderid@fcm.googleapis.com" from="devices@gcm.googleapis.com" type="normal">
                <gcm xmlns="google:mobile:data" xmlns:stream="http://etherx.jabber.org/streams">
                    {
                        "data": {
                            "test": "message",
                        },
                        "time_to_live": 86400,
                        "from": "abc",
                        "message_id": "123",
                        "category": "test-category"
                    }
                </gcm>
            </message>
        """.trimIndent().replace("\n", "").replace("\\s+".toRegex(), " ")
        println(xmlString)
        val stanza = PacketParserUtils.parseStanza<Stanza>(xmlString)

        // WHEN
        firebaseClient.processStanza(stanza)

        // THEN
        val actualAck = removeWhitespacesAndNewlines(result.captured.toXML(null).toString())
        val expectedAckRegex = removeWhitespacesAndNewlines("""
            <message xmlns='jabber:client' id='(.)+'>
                <gcm xmlns="google:mobile:data">
                    \{
                        "message_type":"ack",
                        "from":"abc",
                        "message_id":"123"
                    \}
                </gcm>
            </message>
        """).toRegex()//.trimIndent().replace("\n", "").replace("\\s+".toRegex(), "").toRegex()
        assertTrue(expectedAckRegex matches actualAck)
    }

    private fun removeWhitespacesAndNewlines(s: String): String =
        s.replace("\n", "").replace("\\s+".toRegex(), "")
}
