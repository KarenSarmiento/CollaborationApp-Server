package xmpp

import api.FirebasePacket
import api.JsonKeyword as Jk
import api.jsonStringToFirebasePacket
import mu.KLogging
import utils.Constants
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
import utils.prettyFormatJSON
import utils.prettyFormatXML
import java.util.HashMap


class FirebaseClient() : StanzaListener, ConnectionListener, ReconnectionListener {

    private companion object: KLogging()
    private var xmppConn: XMPPTCPConnection? = null

    constructor(xmppConn: XMPPTCPConnection?): this() {
        this.xmppConn = xmppConn
    }

    fun connectToFirebase() {
        // Allow connection to be resumed if it is ever lost.
        XMPPTCPConnection.setUseStreamManagementResumptionDefault(true)
        XMPPTCPConnection.setUseStreamManagementDefault(true)

        // SSL (TLS) is a cryptographic protocol. This object contains configurations for this.
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, SecureRandom())

        // Specify connection configurations.
        logger.info("Connecting to the FCM XMPP Server...")
        val config = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain(Constants.FCM_SERVER)
            .setHost(Constants.FCM_SERVER)
            .setPort(Constants.FCM_TEST_PORT)
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
                val xmlString = prettyFormatXML(packet.toXML(null).toString(), 2)
                logger.info("Sent: $xmlString")
            },
            ForEveryStanza.INSTANCE
        )

        // Handle incoming packets and reject messages that are not from FCM CCS
        xmppConn?.addSyncStanzaListener(
            this,
            StanzaTypeFilter(Message::class.java)
        )

        // Login to Firebase server.
        val username = "${Constants.SENDER_ID}@${Constants.FCM_SERVER_AUTH_CONNECTION}"
        xmppConn?.login(username, Constants.SERVER_KEY)
    }

    override fun processStanza(packet: Stanza) {
        logger.info("\n---- Processing packet in thread ${Thread.currentThread().name} - ${Thread.currentThread().id} ----")
        val xmlString = prettyFormatXML(packet.toXML(null).toString(), 2)
        logger.info("Received: $xmlString")

        val extendedPacket = packet.getExtension(Constants.FCM_NAMESPACE) as StandardExtensionElement
        logger.info("extendedPacket.text: ${prettyFormatJSON(extendedPacket.text, 2)}")
        val firebasePacket = jsonStringToFirebasePacket(extendedPacket.text)

        when(firebasePacket.messageType) {
            Jk.ACK.text -> logger.info("Warning: ACK receipt not yet supported.")
            Jk.NACK.text -> logger.info("Warning: NACK receipt not yet supported.")
            Jk.CONTROL.text -> logger.info("Warning: Control message receipt not yet supported.")
            else -> handleUpstreamMessage(firebasePacket) // upstream has unspecified message type.
        }
        logger.info("---- End of packet processing ----\n")
    }

    private fun handleUpstreamMessage(packet: FirebasePacket) {
        logger.info("This message is an upstream message.")
        sendAck(packet.from, packet.messageId)

        // An upstream packet must have a JSON in the data component.
        val data = JSONObject(packet.data)
        when(data.getString(Jk.UPSTREAM_TYPE.text)) {
            Jk.NEW_PUBLIC_KEY.text -> api.handleNewPublicKeyRequest(data)
            else -> logger.warn("Upstream message type ${data.getString(Jk.UPSTREAM_TYPE.text)} unsupported.")
        }
    }

    private fun sendAck(from: String, messageId: String) {
        // Create ACK JSON
        val ackMap = HashMap<String?, Any?>()
        ackMap[Jk.MESSAGE_TYPE.text] = Jk.ACK.text
        ackMap[Jk.FROM.text] = from
        ackMap[Jk.MESSAGE_ID.text] = messageId
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
        logger.info("Connection established!")
    }

    override fun connectionClosed() {
        logger.info("Connection closed!")
    }

    override fun connectionClosedOnError(e: Exception?) {
        logger.info("Connection closed with error: $e")
    }

    override fun authenticated(connection: XMPPConnection?, resumed: Boolean) {
        logger.info("User authenticated.")
    }

    // Reconnection Listener
    override fun reconnectionFailed(e: Exception) {
        logger.info("Reconnection failed! Error: ${e.message}")
    }

    override fun reconnectingIn(seconds: Int) {
        logger.info("Reconnecting in $seconds...")
    }

}