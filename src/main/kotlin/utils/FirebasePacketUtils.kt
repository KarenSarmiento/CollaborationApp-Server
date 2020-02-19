package utils

import mu.KLogger
import java.io.StringReader
import java.util.*
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonObject

enum class JsonKeyword(val text: String) {
    // All Packets
    DATA("data"), TIME_TO_LIVE("time_to_live"), FROM("from"),
    MESSAGE_ID("message_id"), MESSAGE_TYPE("message_type"), CATEGORY("category"),
    ENC_MESSAGE("enc_message"), ENC_KEY("enc_key"),

    // Packet Types
    ACK("ack"), NACK("nack"), CONTROL("control"),

    // Upstream Packets
    UPSTREAM_TYPE("upstream_type"),
    REGISTER_PUBLIC_KEY("register_public_key"), PUBLIC_KEY("public_key"), EMAIL("email"),
    FORWARD_TO_GROUP("forward_to_group"), FORWARD_TOKEN_ID("forward_token_id"), GROUP_MESSAGE("group_message"),
    GET_NOTIFICATION_KEY("get_notification_key"), NOTIFICATION_KEY("notification_key"),
    CREATE_GROUP("create_group"), GROUP_ID("group_id"), MEMBER_EMAILS("member_emails"),

    // Downstream Packets
    TO("to"), DOWNSTREAM_TYPE("downstream_type"), REQUEST_ID("request_id"), SUCCESS("success"),
    GET_NOTIFICATION_KEY_RESPONSE("get_notification_key_response"),
    REGISTER_PUBLIC_KEY_RESPONSE("register_public_key_response"),
    ORIGINATOR("originator"),
    CREATE_GROUP_RESPONSE("create_group_response"), FAILED_EMAILS("failed_emails"), MEMBERS("members"),
    ADDED_TO_GROUP("added_to_group"), GROUP_NAME("group_name"),
    FORWARD_TO_PEER("forward_to_peer"), PEER_EMAIL("peer_email"), PEER_MESSAGE("peer_message"),
}

data class UserContact(val token: String, val publicKey: String)
data class Users(val registered: MutableMap<String, UserContact>, val unregistered: JsonArray)

fun jsonStringToJsonObject(jsonString: String): JsonObject = Json.createReader(StringReader(jsonString)).readObject()

fun jsonStringToJsonArray(jsonString: String): JsonArray = Json.createReader(StringReader(jsonString)).readArray()

fun getUniqueId(): String = UUID.randomUUID().toString()

fun getStringOrNull(jsonObject: JsonObject, fieldName: String, logger: KLogger): String? {
    val field = jsonObject.getString(fieldName, null)
    if (field == null)
        logger.error("Missing field \"$fieldName\" in packet ${prettyFormatJSON(jsonObject.toString())}")
    return field
}

fun getJsonObjectOrNull(jsonObject: JsonObject, fieldName: String, logger: KLogger): JsonObject? {
    val field = jsonObject.getJsonObject(fieldName)
    if (field == null)
        logger.error("Missing field \"$fieldName\" in packet ${prettyFormatJSON(jsonObject.toString())}")
    return field
}

fun getJsonArrayOrNull(jsonObject: JsonObject, fieldName: String, logger: KLogger): JsonArray? {
    val field = jsonObject.getJsonArray(fieldName)
    if (field == null)
        logger.error("Missing field \"$fieldName\" in packet ${prettyFormatJSON(jsonObject.toString())}")
    return field
}