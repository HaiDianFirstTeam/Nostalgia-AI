package com.haidianfirstteam.nostalgiaai.net

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.CipherSuite
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import org.conscrypt.Conscrypt

/**
 * Enable TLS 1.2 on legacy Android (API 16-19) for OkHttp 3.12.
 */
object LegacyTls {

    fun enableTls12OnPreLollipop(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (Build.VERSION.SDK_INT >= 21) return builder

        try {
            // Install a modern TLS provider on legacy devices.
            val conscryptProvider = try {
                Conscrypt.newProvider()
            } catch (_: Throwable) {
                null
            }
            try {
                if (conscryptProvider != null) {
                    // Insert at highest priority.
                    Security.insertProviderAt(conscryptProvider, 1)
                }
            } catch (_: Throwable) {
                // ignore
            }

            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            val trustManagers = trustManagerFactory.trustManagers
            val trustManager = trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
                ?: return builder

            // Force using Conscrypt SSLContext when available.
            val sslContext = if (conscryptProvider != null) {
                SSLContext.getInstance("TLS", conscryptProvider)
            } else {
                SSLContext.getInstance("TLS")
            }
            sslContext.init(null, arrayOf<TrustManager>(trustManager), null)

            // Still patch enabledProtocols to ensure TLS is enabled on old stacks.
            val socketFactory = PatchedTlsSocketFactory(sslContext.socketFactory)

            try {
                if (conscryptProvider != null && Conscrypt.isConscrypt(socketFactory)) {
                    // Prefer Conscrypt engine sockets when possible.
                    Conscrypt.setUseEngineSocket(socketFactory, true)
                }
            } catch (_: Throwable) {
                // ignore
            }

            builder.sslSocketFactory(socketFactory, trustManager)

            // Prefer TLS 1.2 for modern endpoints (Cloudflare etc.).
            // Explicit cipher suites to improve interoperability with strict servers.
            val commonSuites = arrayOf(
                // ECDSA
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                // RSA
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                // CBC fallbacks
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
            )

            val modern = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .cipherSuites(*commonSuites)
                .build()
            val compat = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .cipherSuites(*commonSuites)
                .build()
            builder.connectionSpecs(listOf(modern, compat, ConnectionSpec.CLEARTEXT))
        } catch (_: Exception) {
            // ignore; fallback to default
        }
        return builder
    }

    private class PatchedTlsSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(s: java.net.Socket, host: String, port: Int, autoClose: Boolean): java.net.Socket {
            val socket = delegate.createSocket(s, host, port, autoClose)
            return patch(socket)
        }

        override fun createSocket(host: String, port: Int): java.net.Socket = patch(delegate.createSocket(host, port))
        override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): java.net.Socket =
            patch(delegate.createSocket(host, port, localHost, localPort))

        override fun createSocket(host: java.net.InetAddress, port: Int): java.net.Socket = patch(delegate.createSocket(host, port))
        override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int): java.net.Socket =
            patch(delegate.createSocket(address, port, localAddress, localPort))

        private fun patch(socket: java.net.Socket): java.net.Socket {
            if (socket is SSLSocket) {
                try {
                    val supported = socket.supportedProtocols?.toSet() ?: emptySet()
                    // Prefer TLSv1.2 only.
                    val desired = listOf("TLSv1.2")
                    val enabled = desired.filter { supported.contains(it) }
                    if (enabled.isNotEmpty()) {
                        socket.enabledProtocols = enabled.toTypedArray()
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }
            return socket
        }
    }
}
