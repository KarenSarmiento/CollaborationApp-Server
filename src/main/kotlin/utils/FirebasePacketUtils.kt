package utils

import org.json.JSONObject

enum class JsonKeyword(val text: String) {
    // All Packets
    DATA("data"), TIME_TO_LIVE("time_to_live"), FROM("from"),
    MESSAGE_ID("message_id"), MESSAGE_TYPE("message_type"),

    // Packet Types
    ACK("ack"), NACK("nack"), CONTROL("control"),

    // Upstream Packets
    UPSTREAM_TYPE("upstream_type"),
    NEW_PUBLIC_KEY("new_public_key"),
    USER_TOKEN("user_token"),
    PUBLIC_KEY("public_key")
}

fun jsonStringToFirebasePacket(json: String) : FirebasePacket {
    val parsedJson = JSONObject(json)
    return FirebasePacket(
        parsedJson.optString(JsonKeyword.DATA.text),
        parsedJson.optString(JsonKeyword.TIME_TO_LIVE.text).toInt(),
        parsedJson.optString(JsonKeyword.FROM.text),
        parsedJson.optString(JsonKeyword.MESSAGE_ID.text),
        parsedJson.optString(JsonKeyword.MESSAGE_TYPE.text).ifEmpty { null }
    )
}

data class FirebasePacket(
    val data: String, val ttl: Int, val from: String, val messageId: String, val messageType: String?)