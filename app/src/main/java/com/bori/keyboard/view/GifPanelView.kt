package com.bori.keyboard.view

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bori.keyboard.gif.GifItem
import com.bori.keyboard.gif.KlipyClient
import com.bori.keyboard.gif.MediaLoader
import com.bori.keyboard.theme.KeyboardTheme

/**
 * GIF panel shown in place of the keys (opened from the emoji panel's GIF button):
 * [ABC] [🔍 search] [😊] on top, then Recent / Trending / quick searches, then the results
 * grid. Search results come from KLIPY ("Powered by KLIPY", shown as their terms ask).
 * Typing a search happens in [SearchBarView]; this panel only shows results.
 */
class GifPanelView(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onGifClose()
        fun onGifEmoji()
        fun onGifSearchTap(current: String)
        fun onGifPicked(item: GifItem, query: String?)
        fun onGifLongPress(item: GifItem)
        fun onGifSettings()
    }

    private sealed class Mode {
        object Recent : Mode()
        object Trending : Mode()
        data class Search(val q: String) : Mode()
    }

    var listener: Listener? = null
    lateinit var client: KlipyClient
    lateinit var loader: MediaLoader
    /** ISO country code for KLIPY's locale parameter. */
    var locale = "us"

    private var theme = KeyboardTheme.light()
    private var gifOn = false
    private var hasKey = false
    private var spanish = false
    private var recents: List<GifItem> = emptyList()
    private var mode: Mode = Mode.Trending
    private var items = mutableListOf<GifItem>()
    private var page = 1
    private var hasNext = false
    private var loading = false
    /** Bumped on every new listing so late answers from an old one are dropped. */
    private var generation = 0

    private val density = resources.displayMetrics.density
    private val topRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val chipScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(chips)
    }
    private val grid = Grid(context)
    private val message = TextView(context).apply {
        textSize = 14f
        gravity = Gravity.CENTER
    }
    private val action = TextView(context).apply {
        textSize = 14f
        gravity = Gravity.CENTER
        setOnClickListener { listener?.onGifSettings() }
    }
    private val messageBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(message, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(action, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, (40 * resources.displayMetrics.density).toInt()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = (12 * resources.displayMetrics.density).toInt()
        })
    }
    private val credit = TextView(context).apply {
        text = "Powered by KLIPY"
        textSize = 11f
        gravity = Gravity.CENTER
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(grid, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(credit, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, (28 * resources.displayMetrics.density).toInt()))
    }
    private val scroll = ScrollView(context).apply { addView(content) }
    private val body = FrameLayout(context).apply {
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(messageBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    init {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(topRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        column.addView(chipScroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))
        column.addView(body, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            if (!loading && hasNext && mode != Mode.Recent && content.height - (y + scroll.height) < dp(300)) loadMore()
        }
        rebuildTop()
    }

    fun setTheme(theme: KeyboardTheme) {
        this.theme = theme
        setBackgroundColor(theme.solidBackground)
        message.setTextColor(theme.hint)
        credit.setTextColor(theme.hint)
        action.setTextColor(theme.textFunction)
        action.background = rounded(theme.keyFunction)
        action.setPadding(dp(16), 0, dp(16), 0)
        rebuildTop()
        rebuildChips()
    }

    /** Called each time the panel opens. */
    fun open(gifOn: Boolean, hasKey: Boolean, recents: List<GifItem>, spanish: Boolean, query: String? = null) {
        this.gifOn = gifOn
        this.hasKey = hasKey
        this.recents = recents
        this.spanish = spanish
        rebuildTop()
        if (!gifOn || !hasKey) {
            showMessage(
                if (!gifOn) {
                    "GIF search is off. It's the only part of Bori that goes online: your search words go to KLIPY."
                } else {
                    "Add your free KLIPY API key in Bori's settings to search GIFs."
                },
                "Open GIF settings",
            )
            rebuildChips()
            return
        }
        if (query != null) {
            search(query)
            return
        }
        // Keep the last search when coming back from typing; otherwise Recent or Trending.
        val m = mode
        if (m is Mode.Search && items.isNotEmpty()) {
            rebuildChips()
            resumeAnimations()
            return
        }
        show(if (recents.isNotEmpty()) Mode.Recent else Mode.Trending)
    }

    fun search(q: String) {
        val t = q.trim()
        if (!gifOn || !hasKey) return
        show(if (t.isEmpty()) Mode.Trending else Mode.Search(t))
    }

    fun setRecents(recents: List<GifItem>) {
        this.recents = recents
        if (mode == Mode.Recent) show(Mode.Recent) else rebuildChips()
    }

    /** The panel was hidden: stop animating offscreen GIFs. */
    fun onHidden() {
        for (i in 0 until grid.childCount) ((grid.getChildAt(i) as? ImageView)?.drawable as? Animatable)?.stop()
    }

    private fun resumeAnimations() {
        for (i in 0 until grid.childCount) ((grid.getChildAt(i) as? ImageView)?.drawable as? Animatable)?.start()
    }

    private fun dp(v: Int) = (v * density).toInt()

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = theme.cornerRadiusDp * density
    }

    private fun button(label: String, filled: Boolean) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = if (label.length > 2 && label[0].isLetter()) 14f else 18f
        maxLines = 1
        setTextColor(theme.textFunction)
        if (filled) background = rounded(theme.keyFunction)
    }

    private fun rebuildTop() {
        topRow.removeAllViews()
        val lp = { w: Int -> LinearLayout.LayoutParams(w, LayoutParams.MATCH_PARENT).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) } }
        topRow.addView(button("ABC", filled = true).apply { setOnClickListener { listener?.onGifClose() } }, lp(dp(56)))
        val q = (mode as? Mode.Search)?.q
        topRow.addView(
            TextView(context).apply {
                text = if (q != null) "🔍  $q" else "🔍  Search KLIPY"
                textSize = 14f
                maxLines = 1
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                setTextColor(if (q != null) theme.text else theme.hint)
                background = GradientDrawable().apply {
                    setColor(theme.keyLetter)
                    cornerRadius = 18 * density
                }
                setOnClickListener {
                    if (gifOn && hasKey) listener?.onGifSearchTap(q.orEmpty()) else listener?.onGifSettings()
                }
            },
            LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) },
        )
        topRow.addView(button("😊", filled = false).apply {
            contentDescription = "Emoji"
            setOnClickListener { listener?.onGifEmoji() }
        }, lp(dp(48)))
    }

    private fun rebuildChips() {
        chips.removeAllViews()
        if (!gifOn || !hasKey) return
        val list = mutableListOf<Pair<String, Mode>>()
        if (recents.isNotEmpty()) list += "🕘 Recent" to Mode.Recent
        list += (if (spanish) "🔥 Tendencias" else "🔥 Trending") to Mode.Trending
        for (t in if (spanish) QUICK_ES else QUICK_EN) list += t to Mode.Search(t)
        for ((label, m) in list) {
            val selected = m == mode
            chips.addView(
                TextView(context).apply {
                    text = label
                    textSize = 13f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(dp(12), 0, dp(12), 0)
                    setTextColor(if (selected) theme.onAccent else theme.textFunction)
                    background = GradientDrawable().apply {
                        setColor(if (selected) theme.accent else theme.keyFunction)
                        cornerRadius = 14 * density
                    }
                    setOnClickListener { show(m) }
                },
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) },
            )
        }
    }

    private fun showMessage(text: String, actionLabel: String? = null) {
        messageBox.visibility = VISIBLE
        scroll.visibility = INVISIBLE
        message.text = text
        message.setPadding(dp(24), 0, dp(24), 0)
        action.visibility = if (actionLabel != null) VISIBLE else GONE
        action.text = actionLabel.orEmpty()
    }

    private fun showGrid() {
        messageBox.visibility = GONE
        scroll.visibility = VISIBLE
    }

    private fun show(m: Mode) {
        mode = m
        generation++
        items.clear()
        grid.removeAllViews()
        page = 1
        hasNext = false
        loading = false
        scroll.scrollTo(0, 0)
        rebuildTop()
        rebuildChips()
        if (m == Mode.Recent) {
            if (recents.isEmpty()) {
                showMessage("GIFs you send show up here.")
            } else {
                showGrid()
                add(recents)
            }
            return
        }
        showMessage(if (spanish) "Cargando…" else "Loading…")
        loadMore()
    }

    private fun loadMore() {
        val m = mode
        val gen = generation
        loading = true
        client.fetch((m as? Mode.Search)?.q, page, locale) { result, err ->
            if (gen != generation) return@fetch
            loading = false
            if (result == null) {
                if (items.isEmpty()) {
                    showMessage(
                        if (err?.contains("key") == true) "KLIPY didn't accept the API key. Check it in Settings → GIFs." else "Couldn't reach KLIPY. Check your connection and try again.",
                        if (err?.contains("key") == true) "Open GIF settings" else null,
                    )
                }
                return@fetch
            }
            page++
            hasNext = result.hasNext
            if (items.isEmpty() && result.items.isEmpty()) {
                showMessage(if (m is Mode.Search) "No GIFs for “${m.q}”." else "Nothing here right now.")
                return@fetch
            }
            showGrid()
            add(result.items)
        }
    }

    private fun add(more: List<GifItem>) {
        val animated = android.os.Build.VERSION.SDK_INT >= 28
        for (item in more) {
            items += item
            val cell = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(theme.keyLetter)
                clipToOutline = true
                contentDescription = item.title.ifEmpty { "GIF" }
                setOnClickListener { listener?.onGifPicked(item, (mode as? Mode.Search)?.q) }
                setOnLongClickListener {
                    listener?.onGifLongPress(item)
                    true
                }
            }
            grid.addView(cell)
            val url = if (animated) item.preview else item.previewStill.ifEmpty { item.preview }
            cell.tag = url
            val gen = generation
            loader.preview(url) { d ->
                if (gen != generation || cell.tag != url || d == null) return@preview
                cell.setImageDrawable(d)
                if (d is Animatable && isShown) d.start()
            }
        }
    }

    /** Cells in equal columns (3 on a phone, more on wide screens), slightly wider than tall. */
    private inner class Grid(context: Context) : ViewGroup(context) {
        private val gap get() = dp(4)

        private fun columns(w: Int) = maxOf(3, w / dp(130))

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val cols = columns(w)
            val cw = (w - gap * (cols + 1)) / cols
            val ch = (cw * 0.8f).toInt()
            val spec = { s: Int -> MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY) }
            for (i in 0 until childCount) getChildAt(i).measure(spec(cw), spec(ch))
            val rows = (childCount + cols - 1) / cols
            setMeasuredDimension(w, rows * (ch + gap) + gap)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val cols = columns(r - l)
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                val x = gap + (i % cols) * (c.measuredWidth + gap)
                val y = gap + (i / cols) * (c.measuredHeight + gap)
                c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
            }
        }
    }

    companion object {
        private val QUICK_EN = listOf("Hi", "LOL", "Love", "Yes", "No", "Wow", "Thank you", "Sad", "Happy", "Dance", "OK", "Bye")
        private val QUICK_ES = listOf("Hola", "Jaja", "Amor", "Sí", "No", "Wow", "Gracias", "Triste", "Feliz", "Baile", "Ok", "Adiós")
    }
}
