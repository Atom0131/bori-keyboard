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
import com.bori.keyboard.view.InputRootView
import com.bori.keyboard.view.KeyboardView
import com.bori.keyboard.view.ShiftState

/**
 * Keyboard service: owns the views, layout switching and shift state. Text editing,
 * suggestions and autocorrect live in [TypingSession].
 */
class BoriImeService :
    InputMethodService(),
    KeyboardView.Listener,
    ClipboardPanelView.Listener,
    EmojiPanelView.Listener {

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
    private var themeNight: Boolean? = null

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
            if (key == Prefs.THEME_MODE) root?.let { applyThemeIfNeeded(it) }
            // Turning history off forgets what was kept, except pinned clips.
            if (key == Prefs.CLIPBOARD_HISTORY && !prefs.clipboardHistory) clipboard.clearUnpinned()
        }
    }

    override fun onDestroy() {
        DiagLog.event("service onDestroy")
        DiagLog.save(this)
        prefsListener?.let { prefs.unlisten(it) }
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
        // The system dark mode can flip while the keyboard is open.
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
        r.emoji.listener = this
        session.strip = r.strip
        root = r
        // Theme the new views at once: after a fold the view can be rebuilt while the keyboard
        // stays open, and it must not show the default palette until the next field.
        themeNight = null
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
        language = Language.fromTag(currentLanguageTag(imm.currentInputMethodSubtype))
        layoutId = policy.initialLayout
        shift = ShiftState.OFF
        val r = root ?: return
        r.showClipboard(false)
        r.showEmoji(false)
        applyThemeIfNeeded(r)
        r.keyboard.keyPreviewEnabled = prefs.keyPreview
        r.keyboard.keyHeightScale = prefs.keyHeightScale
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
    }

    /** Light or dark per Settings → Appearance (system, light, dark, or dark at night). */
    private fun applyThemeIfNeeded(r: InputRootView) {
        val night = KeyboardTheme.wantsDark(this, prefs.themeMode)
        if (themeNight != night) {
            r.applyTheme(KeyboardTheme.forContext(this, night))
            themeNight = night
        }
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
        root?.showClipboard(false)
        root?.showEmoji(false)
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
        r.clipboard.setClips(clipboard.items())
        r.showClipboard(true)
        r.strip.clear()
    }

    override fun onClipPaste(text: String) {
        root?.showClipboard(false)
        session.paste(text)
    }

    override fun onClipLongPress(clip: ClipboardHistory.Clip) {
        val r = root ?: return
        val preview = clip.text.trim().replace(Regex("\\s+"), " ").take(24)
        r.strip.showPrompt("“$preview”", if (clip.pinned) "Unpin" else "Pin", "Delete", sticky = false) { pin ->
            if (pin) clipboard.togglePin(clip.text) else clipboard.delete(clip.text)
        }
    }

    override fun onClipAction(action: Int) {
        val ic = currentInputConnection ?: return
        if (action == ClipboardPanelView.ACTION_CLEAR) {
            clipboard.clearUnpinned()
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

    fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        val on = prefs.glideTyping && layoutId == LayoutId.ALPHA && session.canGlide()
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
        root?.keyboard?.setState(shift, EditorPolicy.enterLabel(policy.enterAction), space)
    }

    /** Whether the editor wants a capital at the cursor right now. */
    private fun sentenceCapsNow(): Boolean {
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

    override fun onPopupText(text: String) = typeText(text, isLetter = false, x = Float.NaN, y = Float.NaN)

    override fun onKeyLongPress(key: Key): Boolean = when (key.code) {
        KeyCode.Globe, KeyCode.Space -> {
            imm.showInputMethodPicker()
            true
        }
        else -> false
    }

    override fun onGlide(xs: FloatArray, ys: FloatArray, ts: IntArray, count: Int) {
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

    override fun onCursorMove(steps: Int) = session.moveCursor(steps)

    override fun onDeleteSlide(words: Int) = session.deleteSlide(words)

    override fun onDeleteSlideEnd() = session.deleteSlideEnd()

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
        /** Stand-ins so emoji panel taps get the same click and buzz as keys. */
        val EMOJI_KEY = Key("", KeyCode.Text(""))
        val DELETE_KEY = Key("⌫", KeyCode.Delete)
    }
}
