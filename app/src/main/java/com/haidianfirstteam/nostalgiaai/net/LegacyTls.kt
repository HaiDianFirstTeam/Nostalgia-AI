package com.haidianfirstteam.nostalgiaai.net

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Enable TLS 1.2 on legacy Android (API 16-19) for OkHttp 3.12.
 */
object LegacyTls {

    fun enableTls12OnPreLollipop(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (Build.VERSION.SDK_INT >= 21) return builder

        try {
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            val trustManagers = trustManagerFactory.trustManagers
            val trustManager = trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
                ?: return builder

            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
            val socketFactory = Tls12SocketFactory(sslContext.socketFactory)

            builder.sslSocketFactory(socketFactory, trustManager)

            val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build()
            builder.connectionSpecs(listOf(spec, ConnectionSpec.CLEARTEXT))
        } catch (_: Exception) {
            // ignore; fallback to default
        }
        return builder
    }

    private class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
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
                    socket.enabledProtocols = arrayOf("TLSv1.2")
                } catch (_: Exception) {
                    // ignore
                }
            }
            return socket
        }
    }
}
