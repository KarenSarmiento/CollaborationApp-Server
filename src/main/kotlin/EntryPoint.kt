import xmpp.FirebaseClient
import java.util.concurrent.CountDownLatch

fun main() {
    val firebaseClient = FirebaseClient()
    firebaseClient.connectToFirebase()
    val latch = CountDownLatch(1)
    latch.await()
}
