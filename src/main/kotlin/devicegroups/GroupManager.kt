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
import javax.json.Json
import javax.json.JsonArray

object GroupManager : KLogging() {
    // Maps group id to group key.
    private val createdGroups = mutableMapOf<String, String>()

    /**
     *  Creates a group with a given group id and registration ids.
     *
     *  @param groupId id of created group.
     *  @param registrationIds array of ids to be added to the group.
     *
     *  @return The group key correspondind to the created group.
     */
    fun maybeCreateGroup(groupId: String, registrationIds: JsonArray): String? {
        // Configure HTTP Connection
        val url = URL(Constants.DEVICE_GROUP_URL)
        val con = url.openConnection() as HttpURLConnection
        con.doOutput = true

        // HTTP request header
        con.setRequestProperty(Jk.CONTENT_TYPE.text, Jk.APPLICATION_JSON.text)
        con.setRequestProperty(Jk.AUTHORISATION.text, "key=${Constants.SERVER_KEY}")
        con.setRequestProperty(Jk.PROJECT_ID.text, Constants.SENDER_ID)
        con.requestMethod = Jk.POST.text
        con.connect()

        // HTTP request
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
        // Read the response into a string
        val responseString = getStringFromStream(con.inputStream)
        con.disconnect()
        logger.info("Received create group response: $responseString.")

        // Parse the JSON string and return the group key
        val response = jsonStringToJsonObject(responseString)
        val groupKey = getStringOrNull(response, Jk.NOTIFICATION_KEY.text, logger)
        return groupKey
    }

    /**
     *  Get group key for corresponding group id.
     *
     *  @param groupId groupId of interest.
     *
     *  @return corresponding groupKey.
     */
    fun getGroupKey(groupId: String): String? {
        return createdGroups[groupId]
    }

    /**
     * Register group key.
     */
    fun registerGroup(groupId: String, groupKey: String) {
        createdGroups[groupId] = groupKey
    }

    private fun getStringFromStream(stream: InputStream): String {
        val responseString = Scanner(stream, "UTF-8").useDelimiter("\\A").next()
        stream.close()
        return responseString
    }

}
