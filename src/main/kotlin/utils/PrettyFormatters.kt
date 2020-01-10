package utils

import org.json.JSONObject
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

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