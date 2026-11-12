package com.bori.keyboard.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import com.bori.keyboard.clipboard.ClipboardHistory
import com.bori.keyboard.dict.Dictionaries
import com.bori.keyboard.gif.GifItem
import com.bori.keyboard.gif.KlipyClient
import com.bori.keyboard.gif.MediaLoader
import com.bori.keyboard.layout.Key
import com.bori.keyboard.layout.KeyCode
import com.bori.keyboard.layout.Language
import com.bori.keyboard.layout.LayoutId
import com.bori.keyboard.layout.Layouts
import com.bori.keyboard.learn.UserStore
import com.bori.keyboard.settings.Prefs
import com.bori.keyboard.settings.SettingsActivity
import com.bori.keyboard.theme.KeyboardTheme
import com.bori.keyboard.view.BigScreen
import com.bori.keyboard.view.ClipboardPanelView
import com.bori.keyboard.view.EmojiPanelView
import com.bori.keyboard.view.GifPanelView
import com.bori.keyboard.view.InputRootView
import com.bori.keyboard.view.KeyboardView
import com.bori.keyboard.view.SearchBarView
import com.bori.keyboard.view.ShiftState

/**
 * Keyboard service: owns the views, layout switching and shift state. Text editing,
 * suggestions and autocorrect live in [TypingSession].
 */
class BoriImeService :
    InputMethodService(),
    KeyboardView.Listener,
    ClipboardPanelView.Listener,
    EmojiPanelView.Listener,
    GifPanelView.Listener,
    SearchBarView.Listener {

    private lateinit var prefs: Prefs
    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private lateinit var feedback: Feedback
    private lateinit var user: UserStore
    private lateinit var dictionaries: Dictionaries
    private lateinit var session: TypingSession
    private lateinit var clipboard: ClipboardHistory
    private var root: InputRootView? = null

    private var policy = EditorPolicy.DEFAULT
    private var language = Language.EN
    private var dictLanguage: Language? = null
    private var layoutId = LayoutId.ALPHA
    private var shift = ShiftState.OFF
    private var lastShiftDownAt = 0L
    /** What the current theme was resolved from (id, night, revision); null = re-resolve. */
    private var themeKey: String? = null

    /** What the keys type into while a search bar is up (null = the app's field). */
    private enum class SearchFor { GIF, CLIP }
    private var searchFor: SearchFor? = null
    private val query = StringBuilder()
    /** The layout before the search started (e.g. numbers), put back afterwards. */
    private var layoutBeforeSearch = LayoutId.ALPHA

    private val klipy by lazy {
        KlipyClient(key = { prefs.klipyKey }, customerId = { prefs.gifCustomerId }, filter = { prefs.gifFilter })
    }
    private val media by lazy { MediaLoader(resources, cacheDir) }

    private val imm get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onCreate() {
        super.onCreate()
        DiagLog.installCrashHandler(this)
        DiagLog.event("service onCreate ${screenInfo()} build=${buildStamp()}")
        prefs = Prefs(this)
        // Shows whether settings survive installing a new build (user reported resets).
        DiagLog.event(
            "settings trail=${prefs.glideTrailColor} width=${prefs.glideTrailWidth} " +
                "emoji=${prefs.emojiStyle} firstInstall=${installStamp(first = true)}",
        )
        feedback = Feedback(this, prefs)
        user = UserStore.get(this)
        dictionaries = Dictionaries(this)
        clipboard = ClipboardHistory(this).apply {
            recording = { prefs.clipboardHistory && !prefs.incognito }
            keepMs = { prefs.clipboardKeepMs }
            onChanged = {
                root?.let { r -> if (r.clipboardOpen) r.clipboard.setClips(items()) }
                if (::session.isInitialized) session.refresh()
            }
            startListening()
        }
        session = TypingSession(this, prefs, user).apply {
            capsProvider = { sentenceCapsNow() }
            geometryProvider = { root?.keyboard?.currentGeometry()?.takeIf { layoutId == LayoutId.ALPHA } }
            clipboard = this@BoriImeService.clipboard
        }
        // The split sliders in Settings move the keys live, so they can be tried in the
        // "Try it here" box while dragging.
        prefsListener = prefs.listen { key ->
            if (key == Prefs.SPLIT_GAP || key == Prefs.SPLIT_EDGE || key == Prefs.SPLIT_BIG_KEY) {
                root?.let { applySplitPrefs(it) }
            }
            // Height sliders also resize the keyboard live.
            if (key == Prefs.AIM_KEYS || key == Prefs.TOUCH_MAP) root?.let { applyAim(it) }
            if (key != null && key in Prefs.EFFECT_KEYS) root?.let { applyEffects(it) }
            if (key != null && key.startsWith(Prefs.KEY_HEIGHT_PREFIX)) {
                root?.let { it.keyboard.keyHeightScales = prefs.keyHeightScales() }
            }
            if (key != null && key in Prefs.THEME_KEYS) {
                // Effects in the theme's colour follow the new accent.
                root?.let { applyThemeIfNeeded(it); applyEffects(it) }
            }
            // Turning history off forgets what was kept, except pinned clips.
            if (key == Prefs.CLIPBOARD_HISTORY && !prefs.clipboardHistory) clipboard.clearUnpinned()
        }
    }

    override fun onDestroy() {
        DiagLog.event("service onDestroy")
        DiagLog.save(this)
        prefsListener?.let { prefs.unlisten(it) }
        feedback.release()
        clipboard.stopListening()
        super.onDestroy()
    }

    /** Screen facts for the diagnostics log. */
    private fun screenInfo(): String {
        val c = resources.configuration
        return "screen=${c.screenWidthDp}x${c.screenHeightDp}dp smallest=${c.smallestScreenWidthDp} " +
            "orient=${c.orientation} big=${BigScreen.isBig(this)}"
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        DiagLog.event("config changed ${screenInfo()}")
        super.onConfigurationChanged(newConfig)
        // The system dark mode (or the Material You wallpaper colours) can change while the
        // keyboard is open.
        themeKey = null
        root?.let { applyThemeIfNeeded(it) }
    }

    override fun onWindowShown() {
        DiagLog.event("window shown")
        super.onWindowShown()
    }

    override fun onWindowHidden() {
        DiagLog.event("window hidden")
        DiagLog.save(this)
        super.onWindowHidden()
    }

    override fun onCreateInputView(): View {
        DiagLog.event("onCreateInputView ${screenInfo()}")
        val r = InputRootView(this)
        r.addOnLayoutChangeListener { _, left, top, right, bottom, oldL, oldT, oldR, oldB ->
            if (right - left != oldR - oldL || bottom - top != oldB - oldT) {
                DiagLog.event("input view size ${right - left}x${bottom - top}px")
            }
        }
        r.keyboard.listener = this
        r.strip.listener = session
        r.clipboard.listener = this
        r.clipboard.imageFile = { clipboard.imageFile(it) }
        r.emoji.listener = this
        r.gif.listener = this
        r.gif.client = klipy
        r.gif.loader = media
        r.searchBar.listener = this
        session.strip = r.strip
        root = r
        // Theme the new views at once: after a fold the view can be rebuilt while the keyboard
        // stays open, and it must not show the default palette until the next field.
        themeKey = null
        applyThemeIfNeeded(r)
        return r
    }

    // Modern keyboards never take over the whole screen in landscape.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        DiagLog.event(
            "startInputView pkg=${info?.packageName} type=${info?.inputType} ime=${info?.imeOptions} " +
                "private=${info?.privateImeOptions} sel=${info?.initialSelStart},${info?.initialSelEnd} " +
                "split=${prefs.splitOnBigScreens && BigScreen.isBig(this)} restarting=$restarting ${screenInfo()}",
        )
        super.onStartInputView(info, restarting)
        policy = EditorPolicy.from(info)
        feedback.quiet = policy.isPassword
        language = Language.fromTag(currentLanguageTag(imm.currentInputMethodSubtype))
        layoutId = policy.initialLayout
        shift = ShiftState.OFF
        // Picks up anything copied while the keyboard was away (the listener can miss copies
        // if Android paused Bori); each copy is only recorded once.
        clipboard.capture()
        val r = root ?: return
        cancelSearch()
        r.show(InputRootView.Panel.KEYS)
        applyThemeIfNeeded(r)
        r.keyboard.keyPreviewEnabled = prefs.keyPreview
        r.keyboard.keyHeightScales = prefs.keyHeightScales()
        applyAim(r)
        applyEffects(r)
        r.keyboard.heightRows = if (prefs.numberRow) 5 else 4
        applySplitPrefs(r)
        r.keyboard.glideTrail = prefs.glideTrail
        r.keyboard.trailColor = prefs.glideTrailColor
        r.overlay.trailWidthDp = prefs.glideTrailWidth
        r.keyboard.spaceCursorEnabled = prefs.spaceCursor
        r.keyboard.deleteSlideEnabled = prefs.deleteSlide
        r.keyboard.keyFlicksEnabled = prefs.keyFlicks
        session.start(info, policy)
        loadDictionary()
        loadLayout()
        updateAutoShift()
        askBigScreenLayout()
        askCalibration()
    }

    /**
     * Press effects and LED lighting. Off in password fields (Stage 3 rule) and when the phone's
     * "Remove animations" is on (animator duration scale 0).
     */
    private fun applyEffects(r: InputRootView) {
        val animations = runCatching {
            android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
        val allowed = animations && !policy.isPassword
        val accent = r.keyboard.currentTheme().accent
        fun colorOf(v: String): Int? = when (v) {
            "rainbow" -> null
            "theme" -> accent
            else -> runCatching { android.graphics.Color.parseColor(v) }.getOrNull()
        }
        r.effects.effect = com.bori.keyboard.view.EffectsView.Effect.of(prefs.pressEffect)
        r.effects.color = colorOf(prefs.effectColor)
        r.effects.allowed = allowed
        r.keyboard.led.configure(
            com.bori.keyboard.view.LedLights.Mode.of(prefs.ledMode),
            com.bori.keyboard.view.LedLights.Target.of(prefs.ledTarget),
            colorOf(prefs.ledColor),
            prefs.ledSpeed,
            prefs.ledBrightness,
            allowed,
        )
        r.keyboard.invalidate()
    }

    /** "Move keys to fit my aim": the keyboard asks the heat map for this posture's shifts. */
    private fun applyAim(r: InputRootView) {
        val map = com.bori.keyboard.learn.TouchMap.get(this)
        r.keyboard.aimShifts = if (prefs.aimKeys && prefs.touchMap) {
            { map.shifts(com.bori.keyboard.view.Posture.of(this)) }
        } else {
            null
        }
    }

    /** Once (after the big-screen question): offer the thumb calibration. */
    private fun askCalibration() {
        val r = root ?: return
        if (prefs.calibrationAsked || prefs.calibrated.isNotEmpty() || policy.isPassword) return
        if (!prefs.splitAsked && BigScreen.isBig(this)) return
        r.strip.showPrompt("Fit the keys to your thumbs?", "Set up", "No thanks", sticky = true) { yes ->
            prefs.calibrationAsked = true
            if (yes) openSettings(SettingsActivity.SCREEN_CALIBRATE)
        }
    }

    /**
     * The selected theme, light or dark per Settings → Appearance (system, light, dark, or
     * dark at night). Only re-resolved when the theme, the mode or a saved edit changed.
     */
    private fun applyThemeIfNeeded(r: InputRootView) {
        val night = KeyboardTheme.wantsDark(this, prefs.themeMode)
        val id = prefs.themeId
        val overrides = prefs.themeOverrides()
        val key = "$id/$night/${prefs.themeRevision}/${overrides.key()}"
        if (themeKey == key) return
        val start = android.os.SystemClock.elapsedRealtime()
        val theme = runCatching { KeyboardTheme.forContext(this, night, id, overrides) }
            .onFailure { DiagLog.event("theme $id failed: $it") }
            .getOrElse { (if (night) KeyboardTheme.dark() else KeyboardTheme.light()).copy(keyShapes = overrides.keyShapes) }
        r.applyTheme(theme)
        themeKey = key
        DiagLog.event("theme=$id night=$night ${android.os.SystemClock.elapsedRealtime() - start}ms")
    }

    private fun applySplitPrefs(r: InputRootView) {
        r.keyboard.splitOnBigScreens = prefs.splitOnBigScreens
        r.keyboard.setSplitSpacing(prefs.splitGap, prefs.splitEdge)
    }

    /**
     * The first time the keyboard opens on a big screen (an unfolded foldable), it starts
     * split and asks once whether to keep it that way. Settings can change it later.
     */
    private fun askBigScreenLayout() {
        val r = root ?: return
        if (prefs.splitAsked || !BigScreen.isBig(this) || policy.isPassword) return
        r.strip.showPrompt("Big screen: split the keys?", "Split", "Full", sticky = true) { split ->
            prefs.splitOnBigScreens = split
            r.keyboard.splitOnBigScreens = split
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        DiagLog.event("finishInputView finishing=$finishingInput")
        root?.keyboard?.cancelAll()
        cancelSearch()
        root?.show(InputRootView.Panel.KEYS)
        session.finish()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        session.finish()
        super.onFinishInput()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        language = Language.fromTag(currentLanguageTag(newSubtype))
        session.finish()
        loadDictionary()
        if (layoutId == LayoutId.ALPHA) loadLayout()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Positions only: shows whether the app's field really takes what we send.
        DiagLog.event("sel $oldSelStart,$oldSelEnd -> $newSelStart,$newSelEnd composing=$candidatesStart,$candidatesEnd")
        session.onUpdateSelection(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoShift()
    }

    private fun loadDictionary() {
        val lang = language
        if (dictLanguage == lang) {
            // User data may have been edited in settings; reload is a no-op when unchanged.
            dictionaries.background { user.load(lang.tag) }
            return
        }
        dictLanguage = lang
        session.setDictionary(null, lang.locale)
        dictionaries.background { user.load(lang.tag) }
        dictionaries.get(lang) { dict ->
            DiagLog.event("dictionary ${lang.tag} ${if (dict != null) "loaded, ${dict.wordCount} words" else "FAILED to load"}")
            if (language == lang) {
                session.setDictionary(dict, lang.locale)
                updateGlide()
            }
        }
    }

    // ---------------------------------------------------------------- clipboard panel

    /** Opens the clipboard panel in place of the keys, or goes back to the keys. */
    fun toggleClipboard() {
        val r = root ?: return
        if (r.clipboardOpen) {
            r.showClipboard(false)
            session.refresh()
            return
        }
        // Picks up anything copied while Bori wasn't listening.
        clipboard.capture()
        session.beforeEditorAction()
        r.clipboard.filter = ""
        r.clipboard.setClips(clipboard.items())
        r.showClipboard(true)
        r.strip.clear()
    }

    override fun onClipPaste(clip: ClipboardHistory.Clip) {
        val r = root ?: return
        if (!clip.isImage) {
            r.showClipboard(false)
            session.paste(clip.text)
            return
        }
        val f = clipboard.imageFile(clip)
        val mime = clip.mime ?: "image/png"
        session.beforeEditorAction()
        val sent = f != null && ContentSender.send(this, currentInputConnection, currentInputEditorInfo, f, mime, clip.text)
        DiagLog.event("clip image paste $mime sent=$sent")
        if (sent) {
            r.showClipboard(false)
            session.refresh()
        } else {
            r.strip.showPrompt(
                if (f == null) "That picture is gone" else "This box can't take pictures",
                "OK", "", sticky = false,
            ) {}
        }
    }

    override fun onClipLongPress(clip: ClipboardHistory.Clip) {
        val r = root ?: return
        val preview = if (clip.isImage) "🖼 picture" else clip.text.trim().replace(Regex("\\s+"), " ").take(24)
        r.strip.showPrompt("“$preview”", if (clip.pinned) "Unpin" else "Pin", "Delete", sticky = false) { pin ->
            if (pin) clipboard.togglePin(clip.id) else clipboard.delete(clip.id)
        }
    }

    override fun onClipSearch() {
        startSearch(SearchFor.CLIP, root?.clipboard?.filter.orEmpty())
    }

    override fun onClipAction(action: Int) {
        val ic = currentInputConnection ?: return
        if (action == ClipboardPanelView.ACTION_CLEAR) {
            val removed = clipboard.clearUnpinned()
            if (removed.isNotEmpty()) {
                val n = removed.size
                root?.strip?.showPrompt("Cleared $n clip${if (n == 1) "" else "s"}", "Undo", "OK", sticky = false) { undo ->
                    if (undo) clipboard.restore(removed)
                }
            }
            return
        }
        session.beforeEditorAction()
        val id = when (action) {
            ClipboardPanelView.ACTION_SELECT_ALL -> android.R.id.selectAll
            ClipboardPanelView.ACTION_CUT -> android.R.id.cut
            ClipboardPanelView.ACTION_COPY -> android.R.id.copy
            else -> android.R.id.paste
        }
        ic.performContextMenuAction(id)
        // Pasting is done with the panel; select all/cut/copy keep it open for the next step.
        if (action == ClipboardPanelView.ACTION_PASTE) {
            root?.showClipboard(false)
            clipboard.consumeFresh()
            session.refresh()
        }
    }

    // ---------------------------------------------------------------- emoji panel

    private fun toggleEmoji() {
        val r = root ?: return
        if (r.emojiOpen) {
            onEmojiClose()
            return
        }
        try {
            r.emoji.open(prefs.recentEmoji, prefs.emojiStyle)
            r.showEmoji(true)
        } catch (t: Throwable) {
            // Never let the panel take the keyboard down: stay on the keys and note why.
            DiagLog.error("emoji panel open", t)
            DiagLog.save(this)
            runCatching { r.showEmoji(false) }
        }
    }

    override fun onEmoji(text: String) {
        DiagLog.event("emoji tap")
        try {
            feedback.onPress(EMOJI_KEY)
            session.onEmoji(text)
        } catch (t: Throwable) {
            DiagLog.error("emoji tap", t)
            DiagLog.save(this)
            return
        }
        // Recents are a record of what was typed, so they follow the same rule as learning.
        if (!prefs.incognito && !policy.noLearning) {
            val recent = (listOf(text) + prefs.recentEmoji.filter { it != text }).take(MAX_RECENT_EMOJI)
            prefs.recentEmoji = recent
            root?.emoji?.setRecents(recent)
        }
    }

    override fun onEmojiDelete() {
        feedback.onPress(DELETE_KEY)
        session.onDelete()
    }

    override fun onEmojiClose() {
        root?.showEmoji(false)
        updateAutoShift()
        session.refresh()
    }

    override fun onEmojiGif() = openGif(null)

    // ---------------------------------------------------------------- GIF panel

    /** Opens the GIF panel; [query] runs a search right away (coming back from the search bar). */
    private fun openGif(query: String?) {
        val r = root ?: return
        r.gif.locale = resources.configuration.locales[0].country.ifEmpty { "US" }.lowercase()
        r.gif.open(prefs.gifSearch, prefs.klipyKey.isNotBlank(), recentGifs(), language == Language.ES, query)
        r.show(InputRootView.Panel.GIF)
        DiagLog.event("gif open on=${prefs.gifSearch} key=${prefs.klipyKey.isNotBlank()} search=${query != null}")
    }

    private fun recentGifs(): List<GifItem> = GifItem.listFromJson(prefs.recentGifs)

    override fun onGifClose() {
        root?.show(InputRootView.Panel.KEYS)
        updateAutoShift()
        session.refresh()
    }

    override fun onGifEmoji() = toggleEmoji()

    override fun onGifSearchTap(current: String) = startSearch(SearchFor.GIF, current)

    override fun onGifSettings() = openSettings(SettingsActivity.SCREEN_GIFS)

    override fun onGifPicked(item: GifItem, query: String?) {
        val r = root ?: return
        feedback.onPress(EMOJI_KEY)
        session.beforeEditorAction()
        // Recents follow the same rule as learning: not in incognito or private fields.
        if (!prefs.incognito && !policy.noLearning) {
            val list = (listOf(item) + recentGifs().filter { it.slug != item.slug }).take(MAX_RECENT_GIFS)
            prefs.recentGifs = GifItem.listToJson(list)
            r.gif.setRecents(list)
        }
        klipy.share(item.slug, query)
        if (!ContentSender.accepts(currentInputEditorInfo, "image/gif")) {
            DiagLog.event("gif: field takes no GIFs, typing link")
            session.paste(item.link)
            r.strip.showPrompt("This app can't take GIFs here, so the link was typed", "OK", "", sticky = false) {}
            return
        }
        r.strip.showPrompt("Sending GIF…", "", "", sticky = false) {}
        media.fetchToSend(item) { file ->
            val ok = file != null && ContentSender.send(
                this, currentInputConnection, currentInputEditorInfo, file, "image/gif",
                item.title.ifEmpty { "GIF" }, android.net.Uri.parse(item.link),
            )
            DiagLog.event("gif send ok=$ok downloaded=${file != null}")
            if (ok) {
                session.refresh()
            } else {
                root?.strip?.showPrompt(
                    if (file == null) "Couldn't download that GIF" else "The app didn't take the GIF",
                    "OK", "", sticky = false,
                ) {}
            }
        }
    }

    override fun onGifLongPress(item: GifItem) {
        val r = root ?: return
        val inRecent = recentGifs().any { it.slug == item.slug }
        r.strip.showPrompt(
            "“${item.title.ifEmpty { "GIF" }.take(24)}”",
            "Type link", if (inRecent) "Forget" else "✕", sticky = false,
        ) { link ->
            if (link) {
                session.paste(item.link)
            } else if (inRecent) {
                val list = recentGifs().filter { it.slug != item.slug }
                prefs.recentGifs = GifItem.listToJson(list)
                r.gif.setRecents(list)
            }
        }
    }

    // ---------------------------------------------------------------- search bar

    /** The keys now type into the search bar instead of the app (GIF or clipboard search). */
    private fun startSearch(target: SearchFor, initial: String) {
        val r = root ?: return
        session.beforeEditorAction()
        if (searchFor == null) layoutBeforeSearch = layoutId
        searchFor = target
        query.setLength(0)
        query.append(initial)
        r.show(InputRootView.Panel.KEYS)
        r.showSearch(true)
        updateSearchBar()
        layoutId = LayoutId.ALPHA
        shift = ShiftState.OFF
        loadLayout()
        DiagLog.event("search start ${target.name.lowercase()}")
    }

    private fun updateSearchBar() {
        root?.searchBar?.set(
            query.toString(),
            if (searchFor == SearchFor.GIF) "Search KLIPY GIFs" else "Search your clips",
        )
    }

    /** Leaves the search bar and goes back to its panel; [submit] runs the search. */
    private fun endSearch(submit: Boolean) {
        val target = searchFor ?: return
        val r = root ?: return
        leaveSearch()
        val q = query.toString().trim()
        when (target) {
            SearchFor.GIF -> openGif(if (submit) q else null)
            SearchFor.CLIP -> {
                if (submit) r.clipboard.filter = q
                r.clipboard.setClips(clipboard.items())
                r.show(InputRootView.Panel.CLIPBOARD)
                r.strip.clear()
            }
        }
    }

    /** Drops the search bar (field changed, keyboard closed) without going to a panel. */
    private fun cancelSearch() {
        if (searchFor == null) return
        // The next field sets its own layout, so the old one isn't put back here.
        searchFor = null
        root?.showSearch(false)
    }

    private fun leaveSearch() {
        searchFor = null
        root?.showSearch(false)
        layoutId = layoutBeforeSearch
        shift = ShiftState.OFF
        loadLayout()
    }

    override fun onSearchBack() = endSearch(submit = false)

    override fun onSearchClear() {
        query.setLength(0)
        updateSearchBar()
    }

    /** A key while searching; false lets normal handling run (layout switches, shift). */
    private fun searchKey(key: Key): Boolean {
        when (val code = key.code) {
            is KeyCode.Text -> searchType(
                if (shift != ShiftState.OFF && key.isLetter) code.text.uppercase(language.locale) else code.text,
            )
            KeyCode.Space -> searchType(" ")
            KeyCode.Delete -> if (query.isNotEmpty()) {
                query.setLength(query.length - Character.charCount(query.codePointBefore(query.length)))
            }
            KeyCode.Enter -> {
                endSearch(submit = true)
                return true
            }
            KeyCode.Emoji -> {
                endSearch(submit = false)
                return true
            }
            else -> return false
        }
        updateSearchBar()
        return true
    }

    private fun searchType(text: String) {
        if (query.length < MAX_QUERY) query.append(text)
        if (shift == ShiftState.ON) {
            shift = ShiftState.OFF
            refreshKeyboardState()
        }
    }

    fun openSettings(screen: String? = null) {
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { if (screen != null) putExtra(SettingsActivity.EXTRA_SCREEN, screen) },
        )
    }

    @Suppress("DEPRECATION")
    private fun currentLanguageTag(subtype: InputMethodSubtype?): String? {
        subtype ?: return null
        return subtype.languageTag.takeIf { it.isNotEmpty() } ?: subtype.locale
    }

    @Suppress("DEPRECATION")
    private fun showGlobe(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            shouldOfferSwitchingToNextInputMethod()
        } else {
            val token = window?.window?.attributes?.token
            token != null && imm.shouldOfferSwitchingToNextInputMethod(token)
        }

    private fun loadLayout() {
        val r = root ?: return
        r.keyboard.setKeyboard(
            Layouts.build(layoutId, language, policy.variant, showGlobe(), prefs.numberRow),
            language.locale,
        )
        updateGlide()
        refreshKeyboardState()
    }

    /** Glide typing works on the letters layout once the dictionary is ready. */
    private fun updateGlide() {
        val on = prefs.glideTyping && layoutId == LayoutId.ALPHA && session.canGlide() && searchFor == null
        DiagLog.event(
            "glide on=$on (setting=${prefs.glideTyping} letters=${layoutId == LayoutId.ALPHA} " +
                "session=${session.glideStatus()})",
        )
        root?.keyboard?.glideEnabled = on
    }

    private fun refreshKeyboardState() {
        val incognito = prefs.incognito && !policy.isPassword
        val space = when {
            layoutId != LayoutId.ALPHA -> ""
            incognito -> "🕶 " + language.displayName
            else -> language.displayName
        }
        val enter = if (searchFor != null) "⌕" else EditorPolicy.enterLabel(policy.enterAction)
        root?.keyboard?.setState(shift, enter, if (searchFor != null) "" else space)
    }

    /** Whether the editor wants a capital at the cursor right now. */
    private fun sentenceCapsNow(): Boolean {
        if (searchFor != null) return false
        val ic = currentInputConnection ?: return false
        val info = currentInputEditorInfo ?: return false
        return prefs.autoCapitalize && policy.autoCapAllowed && ic.getCursorCapsMode(info.inputType) != 0
    }

    /** Sentence-start capitalisation, unless caps lock is on. */
    private fun updateAutoShift() {
        if (shift == ShiftState.LOCKED) return
        val caps = layoutId == LayoutId.ALPHA && sentenceCapsNow()
        val next = if (caps) ShiftState.ON else ShiftState.OFF
        if (next != shift) {
            shift = next
            refreshKeyboardState()
        }
    }

    // ---------------------------------------------------------------- key handling

    override fun onKeyPressed(key: Key) = feedback.onPress(key)

    override fun onKey(key: Key, x: Float, y: Float) {
        if (searchFor != null && searchKey(key)) return
        when (val code = key.code) {
            is KeyCode.Text -> typeText(code.text, key.isLetter, x, y)
            is KeyCode.Switch -> {
                layoutId = code.target
                shift = ShiftState.OFF
                loadLayout()
                updateAutoShift()
            }
            KeyCode.Shift -> onShift()
            KeyCode.Delete -> session.onDelete()
            KeyCode.Enter -> session.onEnter(policy.enterAction)
            KeyCode.Space -> {
                session.onSpace()
                if (layoutId == LayoutId.SYMBOLS) {
                    // Like most keyboards: "?123 → symbol → space" returns to letters.
                    layoutId = LayoutId.ALPHA
                    loadLayout()
                    updateAutoShift()
                }
            }
            KeyCode.Globe -> {
                DiagLog.event("globe key: switching keyboard")
                switchToNext()
            }
            KeyCode.Emoji -> {
                DiagLog.event("emoji key")
                DiagLog.save(this)
                toggleEmoji()
                DiagLog.save(this)
            }
        }
    }

    /** When this APK was installed, so diagnostics show which build is running. */
    private fun buildStamp(): String = installStamp(first = false)

    /** First install (survives updates; a new value means Bori was removed) or last update. */
    private fun installStamp(first: Boolean): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        val t = if (first) info.firstInstallTime else info.lastUpdateTime
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(t))
    }.getOrDefault("?")

    override fun onPopupText(text: String) {
        if (searchFor != null) {
            searchType(text)
            updateSearchBar()
            return
        }
        typeText(text, isLetter = false, x = Float.NaN, y = Float.NaN)
    }

    override fun onKeyLongPress(key: Key): Boolean = when (key.code) {
        KeyCode.Globe, KeyCode.Space -> {
            imm.showInputMethodPicker()
            true
        }
        else -> false
    }

    override fun onGlide(xs: FloatArray, ys: FloatArray, ts: IntArray, count: Int) {
        if (searchFor != null) return
        val case = when (shift) {
            ShiftState.LOCKED -> TypingSession.GlideCase.ALL
            ShiftState.ON -> TypingSession.GlideCase.FIRST
            ShiftState.OFF -> TypingSession.GlideCase.NONE
        }
        DiagLog.event("glide swipe points=$count")
        session.onGlide(xs, ys, ts, count, case)
        if (shift == ShiftState.ON) {
            shift = ShiftState.OFF
            refreshKeyboardState()
        }
    }

    override fun onCursorMove(steps: Int) {
        if (searchFor == null) session.moveCursor(steps)
    }

    override fun onDeleteSlide(words: Int) {
        if (searchFor == null) session.deleteSlide(words)
    }

    override fun onDeleteSlideEnd() {
        if (searchFor == null) {
            session.deleteSlideEnd()
        } else {
            onSearchClear()
        }
    }

    private fun typeText(text: String, isLetter: Boolean, x: Float, y: Float) {
        val out = if (shift != ShiftState.OFF && isLetter) text.uppercase(language.locale) else text
        session.onText(out, x, y)
        if (shift == ShiftState.ON) {
            shift = ShiftState.OFF
            refreshKeyboardState()
        }
    }

    private fun onShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == ShiftState.LOCKED -> ShiftState.OFF
            now - lastShiftDownAt < DOUBLE_TAP_MS -> ShiftState.LOCKED
            shift == ShiftState.ON -> ShiftState.OFF
            else -> ShiftState.ON
        }
        lastShiftDownAt = now
        refreshKeyboardState()
    }

    @Suppress("DEPRECATION")
    private fun switchToNext() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!switchToNextInputMethod(false)) imm.showInputMethodPicker()
        } else {
            val token = window?.window?.attributes?.token
            if (token == null || !imm.switchToNextInputMethod(token, false)) imm.showInputMethodPicker()
        }
    }

    private companion object {
        const val DOUBLE_TAP_MS = 300L
        const val MAX_RECENT_EMOJI = 32
        const val MAX_RECENT_GIFS = 40
        const val MAX_QUERY = 100
        /** Stand-ins so emoji panel taps get the same click and buzz as keys. */
        val EMOJI_KEY = Key("", KeyCode.Text(""))
        val DELETE_KEY = Key("⌫", KeyCode.Delete)
    }
}
