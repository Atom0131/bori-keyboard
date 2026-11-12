package com.bori.keyboard.gif

import org.json.JSONArray
import org.json.JSONObject

/**
 * One GIF from KLIPY. [preview] is a small animated WebP for the grid (still JPG on Android
 * 8), [send] the GIF file put into the app, [link] the URL typed when the app can't take GIFs.
 */
data class GifItem(
    val slug: String,
    val title: String,
    val preview: String,
    val previewStill: String,
    val send: String,
    val link: String,
    val width: Int,
    val height: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("slug", slug).put("title", title).put("preview", preview).put("still", previewStill)
        .put("send", send).put("link", link).put("w", width).put("h", height)

    companion object {
        fun fromJson(o: JSONObject) = GifItem(
            o.getString("slug"), o.optString("title"), o.getString("preview"), o.optString("still"),
            o.getString("send"), o.optString("link"), o.optInt("w"), o.optInt("h"),
        )

        fun listFromJson(s: String): List<GifItem> = runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull() }
        }.getOrDefault(emptyList())

        fun listToJson(items: List<GifItem>): String = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()

        /**
         * Reads one result of KLIPY's search/trending API. Items without the files we need
         * (e.g. ads, when an API key has them turned on) give null.
         */
        fun fromKlipy(o: JSONObject): GifItem? {
            if (o.optString("type", "gif") != "gif") return null
            val file = o.optJSONObject("file") ?: return null
            fun fmt(size: String, kind: String) = file.optJSONObject(size)?.optJSONObject(kind)
            val small = fmt("sm", "webp") ?: fmt("xs", "webp") ?: fmt("sm", "gif") ?: return null
            val still = fmt("sm", "jpg") ?: fmt("xs", "jpg") ?: small
            // The GIF sent: medium if it's a sensible size, else small.
            val md = fmt("md", "gif")
            val send = md?.takeIf { it.optLong("size") in 1..MAX_SEND_BYTES } ?: fmt("sm", "gif") ?: md ?: return null
            val link = (fmt("hd", "gif") ?: md ?: send).optString("url")
            return GifItem(
                slug = o.optString("slug").ifEmpty { o.optString("id") },
                title = o.optString("title"),
                preview = small.getString("url"),
                previewStill = still.getString("url"),
                send = send.getString("url"),
                link = link,
                width = small.optInt("width"),
                height = small.optInt("height"),
            )
        }

        private const val MAX_SEND_BYTES = 4L * 1024 * 1024
    }
}
