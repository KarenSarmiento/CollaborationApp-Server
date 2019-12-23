import com.google.firebase.FirebaseApp
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseOptions

fun main() {
    initialiseFirebaseSdk()
}

fun initialiseFirebaseSdk() {
    val options = FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
//        .setDatabaseUrl("https://<DATABASE_NAME>.firebaseio.com/")
        .build()

    FirebaseApp.initializeApp(options)
    print("YO WE DID IT")
}