package xmpp

import utils.Constants
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Stanza

class FcmPacketExtension(private val json: String) : ExtensionElement {

    override fun toXML(enclosingNamespace: String): String {
        return "<$elementName xmlns=\"$namespace\">$json</${Constants.FCM_ELEMENT_NAME}>"
    }

    fun toPacket(): Stanza {
        val message = Message()
        message.addExtension(this)
        return message
    }

    override fun getElementName(): String {
        return Constants.FCM_ELEMENT_NAME
    }

    override fun getNamespace(): String {
        return Constants.FCM_NAMESPACE
    }
}