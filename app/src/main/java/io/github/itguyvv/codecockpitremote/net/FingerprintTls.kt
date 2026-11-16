package io.github.itguyvv.codecockpitremote.net

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * TOFU trust for the desktop's self-signed certificate.
 *
 * The trust manager accepts the handshake but records the SHA-256 of the leaf
 * certificate; the client then compares that fingerprint against the one the user
 * confirmed on first connect (stored per desktop). This mirrors what RustDesk-style
 * tools do: verify out of band once, pin afterwards.
 */
class FingerprintTls {
    /** Fingerprint of the leaf certificate seen on the most recent handshake */
    val seenFingerprint = AtomicReference<String>("")

    val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            // Record first, decide later — the comparison runs once the socket is up
            chain.firstOrNull()?.let { seenFingerprint.set(fingerprintOf(it)) }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    fun sslContext(): SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }

    companion object {
        fun fingerprintOf(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256")
                .digest(cert.encoded)
                .joinToString("") { "%02x".format(it) }

        /** Grouped 8×4 hex for human comparison against the desktop settings display */
        fun grouped(fp: String): String =
            fp.chunked(8).joinToString(" ")
    }
}
