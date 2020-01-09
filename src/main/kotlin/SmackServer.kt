import java.util.concurrent.CountDownLatch

fun main() {
    val firebaseClient = FirebaseClient()
    firebaseClient.connectToFirebase()
    waitIndefinitely()
}

fun waitIndefinitely() {
    try {
        val latch = CountDownLatch(1)
        latch.await()
    } catch (e: InterruptedException) {
        println("An error occurred while latch was waiting. Error: ${e.message}")
    }
}