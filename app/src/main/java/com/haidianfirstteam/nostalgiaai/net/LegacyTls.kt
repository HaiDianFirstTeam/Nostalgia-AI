package com.haidianfirstteam.nostalgiaai.net

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.CipherSuite
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.security.KeyStore
import java.security.Security
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.io.ByteArrayInputStream
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

    @Volatile
    private var conscryptInstalled: Boolean = false

    fun enableTls12OnPreLollipop(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (Build.VERSION.SDK_INT >= 21) return builder

        try {
            // Install a modern TLS provider on legacy devices.
            val conscryptProvider = ensureConscryptInstalled()

            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            val trustManagers = trustManagerFactory.trustManagers
            val systemTrustManager = trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
                ?: return builder

            // Some modern endpoints (or misconfigured servers) can fail on API 19 with:
            // CertPathValidatorException: Trust anchor for certification path not found
            // because the system root store is outdated. Keep system trust as primary,
            // but add a small set of widely-used public roots as fallback.
            val extraTrustManager = buildExtraTrustManagerOrNull()
            val trustManager = if (extraTrustManager != null) {
                CompositeX509TrustManager(systemTrustManager, extraTrustManager)
            } else systemTrustManager

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
        } catch (_: Throwable) {
            // ignore; fallback to default
        }
        return builder
    }

    private fun buildExtraTrustManagerOrNull(): X509TrustManager? {
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(null, null)

            var i = 0
            for (pem in ExtraTrustAnchors.ALL_PEMS) {
                val cert = cf.generateCertificate(ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII)))
                ks.setCertificateEntry("extra_$i", cert)
                i++
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(ks)
            tmf.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
        } catch (_: Throwable) {
            null
        }
    }

    private class CompositeX509TrustManager(
        private val primary: X509TrustManager,
        private val fallback: X509TrustManager
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            primary.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            try {
                primary.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                // If system validation fails on legacy devices, try the bundled extra roots.
                fallback.checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
            val a = primary.acceptedIssuers
            val b = fallback.acceptedIssuers
            return (a.asList() + b.asList()).distinctBy { it.subjectX500Principal.name + "/" + it.serialNumber.toString(16) }
                .toTypedArray()
        }
    }

    private fun ensureConscryptInstalled(): java.security.Provider? {
        if (conscryptInstalled) {
            return try {
                Security.getProvider("Conscrypt")
            } catch (_: Throwable) {
                null
            }
        }
        synchronized(this) {
            if (conscryptInstalled) {
                return try {
                    Security.getProvider("Conscrypt")
                } catch (_: Throwable) {
                    null
                }
            }
            val provider = try {
                Conscrypt.newProvider()
            } catch (_: Throwable) {
                null
            }
            if (provider != null) {
                try {
                    // Insert at highest priority.
                    Security.insertProviderAt(provider, 1)
                } catch (_: Throwable) {
                    // ignore
                }
            }
            conscryptInstalled = true
            return provider
        }
    }

    private class PatchedTlsSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(s: java.net.Socket, host: String, port: Int, autoClose: Boolean): java.net.Socket {
            val socket = delegate.createSocket(s, host, port, autoClose)
            return patch(socket, host)
        }

        override fun createSocket(host: String, port: Int): java.net.Socket = patch(delegate.createSocket(host, port), host)
        override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): java.net.Socket =
            patch(delegate.createSocket(host, port, localHost, localPort), host)

        override fun createSocket(host: java.net.InetAddress, port: Int): java.net.Socket = patch(delegate.createSocket(host, port), null)
        override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int): java.net.Socket =
            patch(delegate.createSocket(address, port, localAddress, localPort), null)

        private fun patch(socket: java.net.Socket, hostnameForSni: String?): java.net.Socket {
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

                // Ensure SNI is set on legacy devices.
                // Without SNI, some CDNs will return a default certificate, which can fail on API 19
                // with CertPathValidatorException: Trust anchor for certification path not found.
                if (!hostnameForSni.isNullOrBlank()) {
                    try {
                        if (Conscrypt.isConscrypt(socket)) {
                            try {
                                Conscrypt.setUseSessionTickets(socket, true)
                            } catch (_: Throwable) {
                                // ignore
                            }
                            try {
                                Conscrypt.setHostname(socket, hostnameForSni)
                            } catch (_: Throwable) {
                                // ignore
                            }
                        } else {
                            // Reflection fallback for some platform SSLSocket implementations
                            // (OpenSSLSocketImpl) that support SNI via setHostname(String).
                            try {
                                val m = socket.javaClass.getMethod("setHostname", String::class.java)
                                m.isAccessible = true
                                m.invoke(socket, hostnameForSni)
                            } catch (_: Throwable) {
                                // ignore
                            }
                        }
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
            }
            return socket
        }
    }
}
