package utils

import mu.KLogger
import java.io.StringReader
import java.util.*
import javax.json.Json
import javax.json.JsonObject

enum class JsonKeyword(val text: String) {
    // All Packets
    DATA("data"), TIME_TO_LIVE("time_to_live"), FROM("from"),
    MESSAGE_ID("message_id"), MESSAGE_TYPE("message_type"), CATEGORY("category"),

    // Packet Types
    ACK("ack"), NACK("nack"), CONTROL("control"),

    // Upstream Packets
    UPSTREAM_TYPE("upstream_type"),
    REGISTER_PUBLIC_KEY("register_public_key"), PUBLIC_KEY("public_key"), EMAIL("email"),
    FORWARD_MESSAGE("forward_message"), FORWARD_TOKEN_ID("forward_token_id"), JSON_UPDATE("json_update"),
    GET_NOTIFICATION_KEY("get_notification_key"), NOTIFICATION_KEY("notification_key"),

    // Downstream Packets
    TO("to"), DOWNSTREAM_TYPE("downstream_type"), REQUEST_ID("request_id"), SUCCESS("success"),
    GET_NOTIFICATION_KEY_RESPONSE("get_notification_key_response"),
    REGISTER_PUBLIC_KEY_RESPONSE("register_public_key_response")

}

fun jsonStringToJson(jsonString: String): JsonObject = Json.createReader(StringReader(jsonString)).readObject()

fun getUniqueId(): String = UUID.randomUUID().toString()

fun getStringOrNull(jsonObject: JsonObject, fieldName: String, logger: KLogger): String? {
    val field = jsonObject.getString(fieldName, null)
    if (field == null)
        logger.error("Missing field \"$fieldName\" in packet ${prettyFormatJSON(jsonObject.toString(), 2)}")
    return field
}

fun getJsonObjectOrNull(jsonObject: JsonObject, fieldName: String, logger: KLogger): JsonObject? {
    val field = jsonObject.getJsonObject(JsonKeyword.DATA.text)
    if (field == null)
        logger.error("Missing field \"$fieldName\" in packet ${prettyFormatJSON(jsonObject.toString(), 2)}")
    return field
}