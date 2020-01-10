import com.google.firebase.FirebaseApp
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message

private const val topic = "myTestTopic"

fun initialiseFirebaseSdk() {
    println("Initialising Firebase Sdk...")
    val options = FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .build()

    FirebaseApp.initializeApp(options)
    println("Firebase Sdk Initialisation Complete!")
}

@Throws(FirebaseMessagingException::class)
fun sendTestMessageToTopic() {
    println("Sending message to test topic!")
    // See documentation on defining a message payload.
    val message = Message.builder()
        .putData("fromUserId", "SERVER")
        .putData("message", "SERVER MESSAGE")
        .setTopic(topic)
        .build()

    // Send a message to the devices subscribed to the provided topic.
    val response = FirebaseMessaging.getInstance().send(message)
    // Response is a message ID string.
    println("Successfully sent message: $response")
}