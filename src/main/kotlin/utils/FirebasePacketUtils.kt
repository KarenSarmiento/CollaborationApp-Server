package utils

import java.io.StringReader
import javax.json.Json
import javax.json.JsonObject

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
    PUBLIC_KEY("public_key"),

    // Downstream Packets
    TO("to"),
    JSON_TYPE("json_type"), RESPONSE("response"),
    RESPONSE_ID("response_id"),
    SUCCESS("success")
}

fun jsonStringToJson(jsonString: String): JsonObject = Json.createReader(StringReader(jsonString)).readObject()

fun jsonStringToFirebasePacket(json: String) : FirebasePacket {
    val parsedJson = jsonStringToJson(json)
    return FirebasePacket(
        parsedJson.getJsonObject(JsonKeyword.DATA.text).toString(),
        parsedJson.getInt(JsonKeyword.TIME_TO_LIVE.text),
        parsedJson.getString(JsonKeyword.FROM.text),
        parsedJson.getString(JsonKeyword.MESSAGE_ID.text),
        parsedJson.getString(JsonKeyword.MESSAGE_TYPE.text, null)
    )
}

data class FirebasePacket(
    val data: String, val ttl: Int, val from: String, val messageId: String, val messageType: String?)