import xmpp.FirebaseClient
import java.util.concurrent.CountDownLatch

fun main() {
    FirebaseClient.connectToFirebase()
    val latch = CountDownLatch(1)
    latch.await()
}
