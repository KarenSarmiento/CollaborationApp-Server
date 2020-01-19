import firebaseconnection.FirebaseXMPPClient
import java.util.concurrent.CountDownLatch

fun main() {
    FirebaseXMPPClient.connectToFirebase()
    val latch = CountDownLatch(1)
    latch.await()
}
