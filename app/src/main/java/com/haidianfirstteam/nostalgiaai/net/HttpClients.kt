package com.haidianfirstteam.nostalgiaai.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * OkHttpClient should be shared. Creating it per request can burn memory/threads,
 * and is especially risky on low-memory legacy devices.
 */
object HttpClients {

    @Volatile
    private var openAiClient: OkHttpClient? = null

    @Volatile
    private var tavilyClient: OkHttpClient? = null

    @Volatile
    private var musicClient: OkHttpClient? = null

    fun openAi(): OkHttpClient {
        return openAiClient ?: synchronized(this) {
            openAiClient ?: LegacyTls.enableTls12OnPreLollipop(
                OkHttpClient.Builder()
            )
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
                .also { openAiClient = it }
        }
    }

    fun tavily(): OkHttpClient {
        return tavilyClient ?: synchronized(this) {
            tavilyClient ?: LegacyTls.enableTls12OnPreLollipop(
                OkHttpClient.Builder()
            )
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
                .also { tavilyClient = it }
        }
    }

    fun music(): OkHttpClient {
        return musicClient ?: synchronized(this) {
            musicClient ?: LegacyTls.enableTls12OnPreLollipop(
                OkHttpClient.Builder()
            )
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
                .also { musicClient = it }
        }
    }
}
