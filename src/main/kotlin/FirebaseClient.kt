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
import org.jivesoftware.smack.sm.predicates.ForEveryStanza


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
                println("Sent: ${packet.toXML(null)}")
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
        println("Processing packet in thread ${Thread.currentThread().name} - ${Thread.currentThread().id}")
        println("Received: ${packet.toXML(null)}")
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