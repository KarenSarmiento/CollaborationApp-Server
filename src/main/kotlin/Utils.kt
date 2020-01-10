
import org.json.JSONObject
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import java.io.StringWriter
import java.io.StringReader
import javax.xml.transform.stream.StreamSource

object Utils {
    const val FCM_SERVER = "fcm-xmpp.googleapis.com"
    const val FCM_PROD_PORT = 5235
    const val FCM_TEST_PORT = 5236
    const val FCM_SERVER_AUTH_CONNECTION = "fcm.googleapis.com"

    const val FCM_ELEMENT_NAME = "gcm"
    const val FCM_NAMESPACE = "google:mobile:data"

    const val SENDER_ID = "849641919488"
    const val SERVER_KEY = "AAAAxdKa1AA:APA91bEPghKBhTv8xaQnzP6NFaLiuUJmg4sbI92__5CkoIe8kBAFXYD" +
            "GH72RX_LKcQ3TixxkHuVELDSHCQt9SWW_wyJVEVmYULaLI6b9nim7CSJkIJKSoKJos4KPmk019jP-GxKY4d_C"

    fun prettyFormatXML(xmlString: String, indent: Int): String {
        try {
            val xmlInput = StreamSource(StringReader(xmlString))
            val stringWriter = StringWriter()
            val xmlOutput = StreamResult(stringWriter)
            val transformerFactory = TransformerFactory.newInstance()
            transformerFactory.setAttribute("indent-number", indent)
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.transform(xmlInput, xmlOutput)
            return xmlOutput.writer.toString()
        } catch (e: Exception) {
            throw RuntimeException(e) // simple exception handling, please review it
        }
    }

    fun prettyFormatJSON(jsonString: String, indent: Int): String {
        return JSONObject(jsonString).toString(indent)
    }
}