package com.karensarmiento.collaborationapp.messaging

import com.karensarmiento.collaborationapp.Utils
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.ReconnectionManager
import org.jivesoftware.smack.SASLAuthentication
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.json.JSONObject
import java.security.SecureRandom
import javax.net.ssl.SSLContext

/**
 * This makes use of Smack in order to open an XMPP connection with the Firebase server and send
 * messages.
 */
object FirebaseMessageSendingService {
    private var xmppConn: XMPPTCPConnection? = null

    init {
        connectToFirebase()
    }

    private fun connectToFirebase() {
        // Allow connection to be resumed if it is ever lost.
        XMPPTCPConnection.setUseStreamManagementResumptionDefault(true)
        XMPPTCPConnection.setUseStreamManagementDefault(true)

        // SSL (TLS) is a cryptographic protocol. This object contains configurations for this.
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, SecureRandom())

        // Specify connection configurations.
        println("Connecting to the FCM XMPP Server...")
        val config = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain("FCM XMPP Client Connection Server")
            .setHost(Utils.FCM_SERVER)
            .setPort(Utils.FCM_TEST_PORT)
            .setSendPresence(false)
            .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
            .setCompressionEnabled(true)
            .setSocketFactory(sslContext.socketFactory)
            .setCustomSSLContext(sslContext)
            .build()

        // Connect
        xmppConn = XMPPTCPConnection(config)
        xmppConn?.let {
            it.connect()

            // Allow reconnection to be automatic.
            ReconnectionManager.getInstanceFor(it).enableAutomaticReconnection()

            // Disable Roster (contact list). This will be managed directly with Firebase.
            Roster.getInstanceFor(it).isRosterLoadedAtLogin = false

            // FCM requires a SASL PLAIN authentication mechanism.
            SASLAuthentication.unBlacklistSASLMechanism("PLAIN")
            SASLAuthentication.blacklistSASLMechanism("DIGEST-MD5")

            // Login to Firebase server.
            val username = "${Utils.SENDER_ID}@${Utils.FCM_SERVER_AUTH_CONNECTION}"
            it.login(username, Utils.SERVER_KEY)
            println("Connected to the FCM XMPP Server!")
        }
    }

    fun sendMessageToTopic(topic: String, payload: String) {
        // TODO: Use messageIds to ensure messages are sent when implementing buffering.
        val messageId = Utils.getUniqueId()
        val toToken = "/topics/$topic"

        val xmppMessage = createXMPPMessage(toToken, messageId, payload)
        xmppConn?.let {
            it.sendStanza(xmppMessage)
            println("Sent XMPP Message.")
        }
    }

    private fun createXMPPMessage(to: String, messageId: String, payload: String): Stanza {
        val payloadMap = HashMap<String, String>()
        payloadMap["message"] = payload
        payloadMap["fromUserId"] = Utils.APP_USER_ID

        val message = HashMap<Any?, Any?>()
        message["to"] = to
        message["message_id"] = messageId
        message["data"] = payloadMap

        val jsonRequest = JSONObject(message).toString()
        return FcmMessageExtension(jsonRequest).toPacket()
    }
}