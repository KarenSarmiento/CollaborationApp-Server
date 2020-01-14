package utils

import org.jivesoftware.smack.packet.Stanza
import org.json.JSONObject
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

fun prettyFormatXML(xmlString: String, indent: Int): String {
    val xmlInput = StreamSource(StringReader(xmlString))
    val stringWriter = StringWriter()
    val xmlOutput = StreamResult(stringWriter)
    val transformerFactory = TransformerFactory.newInstance()
    transformerFactory.setAttribute("indent-number", indent)
    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.transform(xmlInput, xmlOutput)
    return xmlOutput.writer.toString()
}

fun prettyFormatJSON(jsonString: String): String {
    return JSONObject(jsonString).toString(2)
}

fun packetDetails(packet: Stanza): String {
    return """
        packet.from: ${packet.from}
        packet.to: ${packet.to}
        packet.language: ${packet.language}
        packet.extensions: ${packet.extensions}
        packet.stanzaId: ${packet.stanzaId}
        packet.error: ${packet.error}
        packet.toXML(null): ${prettyFormatXML(packet.toXML(null).toString(), 2)}
    """.trimIndent()
}

fun removeWhitespacesAndNewlines(s: String): String =
    s.replace("\n", "").replace("\\s+".toRegex(), "")