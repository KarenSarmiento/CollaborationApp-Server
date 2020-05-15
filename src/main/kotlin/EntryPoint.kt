import firebaseconnection.FirebaseXMPPClient
import java.util.concurrent.CountDownLatch

fun main() {
    FirebaseXMPPClient.connectToFirebase()
    while (true) {
        val input = readLine()
        if (input == "k" || input == "kill" || input == "q" || input == "quit" || input == "c" || input == "close") {
            FirebaseXMPPClient.closeConnection()
            break
        }
    }
}
