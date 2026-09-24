package com.novelnexus.app.core.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpClient(context: Context, initialCacheMb: Int = 100) {
    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "novel_nexus_http"), initialCacheMb.coerceIn(25,250).toLong() * 1024L * 1024L))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful || chain.request().method != "GET") {
                response
            } else {
                val url = chain.request().url.toString().lowercase()
                val ttl = when {
                    "/chapter-" in url -> 86400
                    "/search" in url -> 120
                    "/sort/" in url || "/list/" in url -> 120
                    else -> 300
                }
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=$ttl")
                    .removeHeader("Pragma")
                    .build()
            }
        }
        .build()

    fun cacheSizeBytes(): Long = runCatching { client.cache?.size() ?: 0L }.getOrDefault(0L)

    fun clearCache() { runCatching { client.cache?.evictAll() } }

    suspend fun get(
        url: String,
        referer: String? = null,
        retries: Int = 2
    ): String = withContext(Dispatchers.IO) {
        var last: Throwable? = null

        repeat(retries + 1) { attempt ->
            try {
                client.newCall(buildRequest(url, referer)).execute().use { response ->
                    return@withContext response.requireBody(url)
                }
            } catch (t: Throwable) {
                last = t
                if (attempt < retries) delay((attempt + 1) * 650L)
            }
        }

        try {
            val stale = CacheControl.Builder()
                .onlyIfCached()
                .maxStale(7, TimeUnit.DAYS)
                .build()

            client.newCall(
                buildRequest(url, referer)
                    .newBuilder()
                    .cacheControl(stale)
                    .build()
            ).execute().use { response ->
                if (response.code != 504) {
                    return@withContext response.requireBody(url)
                }
            }
        } catch (_: Throwable) {
        }

        throw IOException("Failed to GET $url", last)
    }

    private fun buildRequest(url: String, referer: String?): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .apply {
                if (!referer.isNullOrBlank()) header("Referer", referer)
            }
            .build()

    private fun Response.requireBody(url: String): String {
        if (!isSuccessful) throw IOException("HTTP $code for $url")
        return body?.string().orEmpty()
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126 Mobile Safari/537.36 NovelNexus/0.4.0"
    }
}
