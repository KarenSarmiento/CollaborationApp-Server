import mu.KotlinLogging
import java.util.concurrent.CountDownLatch

fun main() {
    val firebaseClient = FirebaseClient()
    firebaseClient.connectToFirebase()
    SmackServer.waitIndefinitely()
}

object SmackServer {
    private val logger = KotlinLogging.logger {}

    fun waitIndefinitely() {
        try {
            val latch = CountDownLatch(1)
            latch.await()
        } catch (e: InterruptedException) {
            logger.info("An error occurred while latch was waiting. Error: ${e.message}")
        }
    }
}