import api.UpstreamRequestHandler
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import utils.jsonStringToFirebasePacket
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
        UpstreamRequestHandler.handleUpstreamRequests(firebaseClientMock, firebasePacket)

        // THEN
        verify { firebaseClientMock.sendAck(userFrom, messageId) }
    }
}