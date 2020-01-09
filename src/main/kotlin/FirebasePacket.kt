import org.json.JSONObject

fun jsonStringToFirebasePacket(json: String) : FirebasePacket {
    val parsedJson = JSONObject(json)
    return FirebasePacket(
        parsedJson.optString("data"),
        parsedJson.optString("time_to_live").toInt(),
        parsedJson.optString("from"),
        parsedJson.optString("message_id")
    )
}

// TODO: Create further data classes to encapsulate different message types e.g. ACK.
data class FirebasePacket(val data: String, val ttl: Int, val from: String, val message_id: String)