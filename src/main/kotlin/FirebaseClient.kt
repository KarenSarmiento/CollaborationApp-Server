import org.jivesoftware.smack.*
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import org.jivesoftware.smack.ReconnectionListener
import org.jivesoftware.smack.ReconnectionManager
import java.lang.Exception
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.StandardExtensionElement
import org.jivesoftware.smack.sm.predicates.ForEveryStanza
import org.json.JSONObject
import java.util.HashMap


class FirebaseClient : StanzaListener, ConnectionListener, ReconnectionListener {

    private var xmppConn: XMPPTCPConnection? = null

    fun connectToFirebase() {
        // Allow connection to be resumed if it is ever lost.
        XMPPTCPConnection.setUseStreamManagementResumptionDefault(true)
        XMPPTCPConnection.setUseStreamManagementDefault(true)

        // SSL (TLS) is a cryptographic protocol. This object contains configurations for this.
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, SecureRandom())

        // Specify connection configurations.
        println("Connecting to the FCM XMPP Server...")
        val config = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain(Utils.FCM_SERVER)
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
        xmppConn?.connect()

        // Allow reconnection to be automatic.
        ReconnectionManager.getInstanceFor(xmppConn).enableAutomaticReconnection()
        ReconnectionManager.getInstanceFor(xmppConn).addReconnectionListener(this)

        // Disable Roster (contact list). This will be managed directly with Firebase.
        Roster.getInstanceFor(xmppConn).isRosterLoadedAtLogin = false

        // FCM requires a SASL PLAIN authentication mechanism.
        SASLAuthentication.unBlacklistSASLMechanism("PLAIN")
        SASLAuthentication.blacklistSASLMechanism("DIGEST-MD5")

        // Handle connection errors
        xmppConn?.addConnectionListener(this)

        // Log all outgoing packets
        xmppConn?.addStanzaInterceptor(
            StanzaListener { packet ->
                val xmlString = Utils.prettyFormatXML(packet.toXML(null).toString(), 2)
                println("Sent: $xmlString")
            },
            ForEveryStanza.INSTANCE
        )

        // Handle incoming packets and reject messages that are not from FCM CCS
        xmppConn?.addSyncStanzaListener(
            this,
            StanzaTypeFilter(Message::class.java)
        )

        // Login to Firebase server.
        val username = "${Utils.SENDER_ID}@${Utils.FCM_SERVER_AUTH_CONNECTION}"
        xmppConn?.login(username, Utils.SERVER_KEY)
    }

    override fun processStanza(packet: Stanza) {
        println("\n---- Processing packet in thread ${Thread.currentThread().name} - ${Thread.currentThread().id} ----")
        printPacketDetails(packet)

        val extendedPacket = packet.getExtension(Utils.FCM_NAMESPACE) as StandardExtensionElement
        println("extendedPacket.text: ${Utils.prettyFormatJSON(extendedPacket.text, 2)}")
        val firebasePacket = jsonStringToFirebasePacket(extendedPacket.text)

        when(firebasePacket.messageType) {
            "ack" -> println("Warning: ACK receipt not yet supported.")
            "nack" -> println("Warning: NACK receipt not yet supported.")
            "control" -> println("Warning: Control message receipt not yet supported.")
            else -> handleTestMessageReceipt(firebasePacket) // upstream
        }
        println("---- End of packet processing ----\n")
    }

    private fun printPacketDetails(packet: Stanza) {
        println("packet.from: ${packet.from}")
        println("packet.to: ${packet.to}")
        println("packet.language: ${packet.language}")
        println("packet.extensions: ${packet.extensions}")
        println("packet.stanzaId: ${packet.stanzaId}")
        println("packet.error: ${packet.error}")
        println("packet.toXML(null): ${Utils.prettyFormatXML(packet.toXML(null).toString(), 2)}")
    }

    private fun handleTestMessageReceipt(packet: FirebasePacket) {
        println("This message is an upstream message.")
        sendAck(packet.from, packet.messageId)
    }

    private fun sendAck(from: String, messageId: String) {
        // Create ACK JSON
        val ackMap = HashMap<String?, Any?>()
        ackMap["message_type"] = "ack"
        ackMap["from"] = from
        ackMap["message_id"] = messageId
        val jsonString = JSONObject(ackMap).toString()
        val ack = FcmPacketExtension(jsonString).toPacket()

        // TODO: Exponential backoff in case of connection failure?
        // Send
        xmppConn?.sendStanza(ack)
    }

    /**
     *  Logging Functions
     */

    // Connection Listener
    override fun connected(connection: XMPPConnection?) {
        println("Connection established!")
    }

    override fun connectionClosed() {
        println("Connection closed!")
    }

    override fun connectionClosedOnError(e: Exception?) {
        println("Connection closed with error: $e")
    }

    override fun authenticated(connection: XMPPConnection?, resumed: Boolean) {
        println("User authenticated.")
    }

    // Reconnection Listener
    override fun reconnectionFailed(e: Exception) {
        println("Reconnection failed! Error: ${e.message}")
    }

    override fun reconnectingIn(seconds: Int) {
        println("Reconnecting in $seconds...")
    }

}