package devicegroups

import mu.KLogging
import utils.Constants
import utils.getStringOrNull
import utils.JsonKeyword as Jk
import utils.jsonStringToJsonObject
import utils.prettyFormatJSON
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.crypto.SecretKey
import javax.json.Json
import javax.json.JsonArray

object GroupManager : KLogging() {

    /**
     *  Maps groupId to groupData.
     *
     *  groupId: A static random string, generated within my app, which identifies the group IN MY APP.
     *  This is different to the firebase id which identifies the group IN FIREBASE.
     */
    private var groups = mutableMapOf<String, GroupData>()

    /**
     *  Creates a group with a given group id and registration ids.
     *
     *  @param groupId id of created group.
     *  @param registrationIds array of ids to be added to the group.
     *
     *  @return The firebaseId corresponding to the created group.
     */
    fun maybeCreateGroup(groupId: String, registrationIds: JsonArray): String? {
        // Configure HTTP Connection.
        val url = URL(Constants.DEVICE_GROUP_URL)
        val con = url.openConnection() as HttpURLConnection
        con.doOutput = true

        // HTTP request header.
        con.setRequestProperty(Jk.CONTENT_TYPE.text, Jk.APPLICATION_JSON.text)
        con.setRequestProperty(Jk.AUTHORISATION.text, "key=${Constants.SERVER_KEY}")
        con.setRequestProperty(Jk.PROJECT_ID.text, Constants.SENDER_ID)
        con.requestMethod = Jk.POST.text
        con.connect()

        // HTTP request.
        val data = Json.createObjectBuilder()
            .add(Jk.OPERATION.text, Jk.CREATE.text)
            .add(Jk.NOTIFICATION_KEY_NAME.text, groupId)
            .add(Jk.REGISTRATION_IDS.text, registrationIds)
            .build()

        logger.info("Requesting create group: ${prettyFormatJSON(data.toString())}")
        val outputStream = con.outputStream
        outputStream.write(data.toString().toByteArray(charset("UTF-8")))
        outputStream.close()

        if (con.responseCode != HttpURLConnection.HTTP_OK) {
            val responseString = getStringFromStream(con.errorStream)
            logger.error("Got response code ${con.responseCode} (${con.responseMessage}): $responseString")
            con.disconnect()
            return null
        }
        // Read the response into a string.
        val responseString = getStringFromStream(con.inputStream)
        con.disconnect()
        logger.info("Received create group response: $responseString.")

        // Parse the JSON string and return the firebaseId.
        val response = jsonStringToJsonObject(responseString)
        return getStringOrNull(response, Jk.NOTIFICATION_KEY.text, logger)
    }

    /**
     *  Get firebase id for corresponding group id.
     *
     *  @param groupId groupId of interest.
     *
     *  @return corresponding firebaseId.
     */
    fun getFirebaseId(groupId: String): String? {
        return groups[groupId]?.firebaseId
    }

    /**
     *  Get members for corresponding group id.
     *
     *  @param groupId groupId of interest.
     *
     *  @return corresponding members.
     */
    fun getGroupMembers(groupId: String): Set<String>? {
        return groups[groupId]?.members?.toSet()
    }

    /**
     *  Get server group key for corresponding group id.
     *
     *  @param groupId groupId of interest.
     *
     *  @return corresponding group key.
     */
    fun getGroupKey(groupId: String): SecretKey {
        if (!groups.containsKey(groupId)) {
            logger.error("Cannot get key for group $groupId since this group doesn't exist.")
        }
        return groups[groupId]!!.serverSymKey
    }

    /**
     * Register group.
     */
    fun registerGroup(groupId: String, firebaseId: String, groupMembers: MutableSet<String>, serverSymKey: SecretKey) {
        if (groupId in groups) {
            logger.error("Cannot register group with id $groupId")
            return
        }
        val existingMembers = groups.getOrDefault(groupId, null)?.members ?: mutableSetOf<String>()
        val newMembers = existingMembers.union(groupMembers).toMutableSet()
        groups[groupId] = GroupData(firebaseId, newMembers, serverSymKey)
    }

    private fun getStringFromStream(stream: InputStream): String {
        val responseString = Scanner(stream, "UTF-8").useDelimiter("\\A").next()
        stream.close()
        return responseString
    }

    fun resetState() {
        groups = mutableMapOf()
    }
}

/**
 *  A class that holds all data about a group (besides the groupId).
 *
 *  firebaseId: A dynamic random string which identifies the group to the app server. Changes when members
 *            of the group change.
 *  members: A set which contains the emails of all the users in the group.
 */
data class GroupData(var firebaseId: String, val members: MutableSet<String>, val serverSymKey: SecretKey)