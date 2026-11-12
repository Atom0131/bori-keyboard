package com.bori.keyboard.gif

import android.os.Handler
import android.os.Looper
import com.bori.keyboard.ime.DiagLog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * KLIPY GIF API (https://docs.klipy.com). Only used when GIF search is on in Settings; sends
 * the search words, a random per-install id KLIPY asks for, the country code and the content
 * filter. Nothing else from the keyboard goes online.
 */
class KlipyClient(
    private val key: () -> String,
    private val customerId: () -> String,
    private val filter: () -> String,
) {
    class Page(val items: List<GifItem>, val hasNext: Boolean)

    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    /** [query] null = trending. Calls [done] on the main thread with null on failure. */
    fun fetch(query: String?, page: Int, locale: String, done: (Page?, String?) -> Unit) {
        val k = key()
        if (k.isBlank()) {
            done(null, "no key")
            return
        }
        val base = "$BASE/${enc(k)}/gifs/" + if (query == null) "trending" else "search"
        val url = buildString {
            append(base).append("?page=").append(page).append("&per_page=").append(PER_PAGE)
            if (query != null) append("&q=").append(enc(query))
            append("&customer_id=").append(enc(customerId()))
            append("&locale=").append(enc(locale))
            append("&content_filter=").append(enc(filter()))
        }
        io.execute {
            var err: String? = null
            val page2 = runCatching {
                val json = JSONObject(get(url))
                if (!json.optBoolean("result", true)) error(json.optString("message", "KLIPY said no"))
                val data = json.getJSONObject("data")
                val arr = data.getJSONArray("data")
                Page((0 until arr.length()).mapNotNull { GifItem.fromKlipy(arr.getJSONObject(it)) }, data.optBoolean("has_next"))
            }.getOrElse {
                err = it.message ?: it.javaClass.simpleName
                DiagLog.event("gif fetch failed: ${it.javaClass.simpleName} ${it.message?.take(80)}")
                null
            }
            main.post { done(page2, err) }
        }
    }

    /** Tells KLIPY a GIF was sent (their share trigger, used for ranking). Fire and forget. */
    fun share(slug: String, query: String?) {
        val k = key()
        if (k.isBlank() || slug.isBlank()) return
        io.execute {
            runCatching {
                val c = URL("$BASE/${enc(k)}/gifs/share/${enc(slug)}").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().put("customer_id", customerId()).put("q", query.orEmpty()).toString()
                c.outputStream.use { it.write(body.toByteArray()) }
                c.responseCode
                c.disconnect()
            }
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code == 401 || code == 403) error("API key not accepted ($code)")
            if (code !in 200..299) error("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val BASE = "https://api.klipy.com/api/v1"
        const val PER_PAGE = 24
    }
}
