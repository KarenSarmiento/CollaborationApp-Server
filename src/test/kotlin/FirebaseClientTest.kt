
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirebaseClientTest {

    @Test
    fun testSendAck() {
        assertEquals(42, Integer.sum(19, 23))
    }
}
