package com.novelnexus.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun get(
        url: String,
        referer: String? = null,
        retries: Int = 2
    ): String = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        repeat(retries + 1) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .apply {
                        if (!referer.isNullOrBlank()) header("Referer", referer)
                    }
                    .build()

                client.newCall(request).execute().use { response ->
                    return@withContext response.requireBody(url)
                }
            } catch (t: Throwable) {
                last = t
                if (attempt < retries) delay((attempt + 1) * 700L)
            }
        }
        throw IOException("Failed to GET $url", last)
    }

    private fun Response.requireBody(url: String): String {
        if (!isSuccessful) throw IOException("HTTP $code for $url")
        return body?.string().orEmpty()
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36 NovelNexus/0.1.1"
    }
}
