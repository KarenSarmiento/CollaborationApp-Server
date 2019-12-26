import com.google.firebase.FirebaseApp
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.Message
import com.karensarmiento.collaborationapp.messaging.FirebaseMessageSendingService.sendMessageToTopic
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException



private const val topic = "myTestTopic"

fun main() {
    initialiseFirebaseSdk()
    sendToTopicWithSmack()
    sendToTopic()
}

fun sendToTopicWithSmack() {
    println("Sending SMACK message to test topic!")
    val messagePayload = """
    {
        "updateType" : "addTodo",
        "update" : {
            "label" : title
        }
    }
    """.trimIndent()
    sendMessageToTopic(topic, messagePayload)
    println("SMACK message sent!")
}

@Throws(FirebaseMessagingException::class)
fun sendToTopic() {
    println("Sending message to test topic!")
    // The topic name can be optionally prefixed with "/topics/".

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

fun initialiseFirebaseSdk() {
    println("Initilaising Firebase Sdk...")
    // TODO: See using OAuth2 - https://firebase.google.com/docs/admin/setup#use-oauth-2-0-refresh-token
    val options = FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .build()

    FirebaseApp.initializeApp(options)
    println("Firebase Sdk Initialisation Complete!")
}