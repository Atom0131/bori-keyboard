package com.bori.keyboard.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import com.bori.keyboard.view.EmojiPack
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import com.bori.keyboard.learn.UserStore
import com.bori.keyboard.predict.Aggressiveness
import com.bori.keyboard.view.BigScreen
import com.bori.keyboard.view.Posture
import kotlinx.coroutines.delay

class SettingsActivity : ComponentActivity() {
    /** The screen to show; the keyboard can ask for one (e.g. GIF settings from the GIF panel). */
    private val startScreen = mutableStateOf("main")

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SCREEN)?.let { startScreen.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.getStringExtra(EXTRA_SCREEN)?.let { startScreen.value = it }
        // Folding/unfolding recreates the activity; stay on the same page (calibration needs it).
        savedInstanceState?.getString(STATE_SCREEN)?.let { startScreen.value = it }
        setContent {
            BoriTheme {
                Surface(Modifier.fillMaxSize()) {
                    var screen by startScreen
                    val back = { screen = parentOf(screen) }
                    BackHandler(enabled = screen != "main") { back() }
                    val go = { to: String -> screen = to }
                    when (screen) {
                        "typing" -> TypingScreen(back, go)
                        "sound" -> SoundVibrationScreen(back)
                        "corrections" -> CorrectionsScreen(back, go)
                        "gestures" -> GesturesScreen(back, go)
                        "trail" -> TrailScreen(back)
                        "layout" -> LayoutScreen(back, go)
                        "heights" -> HeightsScreen(back)
                        "split" -> SplitScreen(back)
                        "themes" -> ThemesScreen(back, go)
                        SCREEN_THEME_GALLERY -> ThemeGalleryScreen(back)
                        SCREEN_THEME_KEYS -> KeysLettersScreen(back)
                        SCREEN_THEME_BACKGROUND -> BackgroundScreen(back)
                        SCREEN_THEME_EFFECTS -> EffectsScreen(back)
                        "privacy" -> PrivacyScreen(back)
                        "emoji" -> EmojiGifScreen(back, go)
                        SCREEN_GIFS -> GifSearchScreen(back)
                        "advanced" -> HelpScreen(back)
                        "dictionary" -> DictionaryScreen(back)
                        "touchmap" -> TouchMapScreen(back, go)
                        SCREEN_CALIBRATE -> CalibrationScreen(back)
                        "lab" -> SwipeTestLabScreen(back)
                        "trainer" -> SwipeTrainerScreen(back)
                        else -> if (screen.startsWith(EDIT_PREFIX)) {
                            ThemeEditorScreen(screen.removePrefix(EDIT_PREFIX), back, go)
                        } else {
                            SettingsScreen(go)
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SCREEN, startScreen.value)
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_GIFS = "gifs"
        const val SCREEN_CALIBRATE = "calibrate"
        private const val STATE_SCREEN = "screen"
    }
}

@Composable
private fun BoriTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun isEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    // Throws DeadSystemException while the phone's system restarts (seen in diagnostics).
    return runCatching { imm.enabledInputMethodList.any { it.packageName == context.packageName } }
        .getOrDefault(false)
}

private fun isSelected(context: Context): Boolean {
    val current = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    }.getOrNull()
    return current?.startsWith(context.packageName + "/") == true
}

/** Where Back goes from each screen: tools return to their category, categories to the start. */
private fun parentOf(screen: String): String = when (screen) {
    "dictionary", "touchmap" -> "corrections"
    SettingsActivity.SCREEN_CALIBRATE, "heights", "split" -> "layout"
    "trainer", "lab", "trail" -> "gestures"
    "sound" -> "typing"
    SettingsActivity.SCREEN_GIFS -> "emoji"
    SCREEN_THEME_GALLERY, SCREEN_THEME_KEYS, SCREEN_THEME_BACKGROUND, SCREEN_THEME_EFFECTS -> "themes"
    else -> if (screen.startsWith(EDIT_PREFIX)) "themes" else "main"
}

/**
 * The start page: setup steps until they're done, then the seven settings groups, each with
 * its current state on the second line (Android settings guide: show status, not a feature list).
 */
@Composable
private fun SettingsScreen(navigate: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var enabled by remember { mutableStateOf(isEnabled(context)) }
    var selected by remember { mutableStateOf(isSelected(context)) }

    // The system pickers don't report back, so poll while the screen is visible.
    LaunchedEffect(Unit) {
        while (true) {
            enabled = isEnabled(context)
            selected = isSelected(context)
            delay(700)
        }
    }

    SettingsPage("Bori Keyboard", onBack = null) {
        if (!enabled || !selected) {
            Footnote("Private, offline keyboard. Nothing you type leaves this phone.")
            SetupStep(
                number = 1,
                title = "Turn on Bori Keyboard",
                done = enabled,
                action = "Open settings",
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            SetupStep(
                number = 2,
                title = "Switch to Bori Keyboard",
                done = selected,
                action = "Choose keyboard",
                actionEnabled = enabled,
            ) {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        }

        var split by remember { mutableStateOf(prefs.splitOnBigScreens) }
        var splitAsked by remember { mutableStateOf(prefs.splitAsked) }
        // Until a choice is made; afterwards it lives under Layout & size.
        if (!splitAsked && LocalConfiguration.current.smallestScreenWidthDp >= BigScreen.MIN_DP) {
            BigScreenStep(split, splitAsked) { on ->
                split = on
                splitAsked = true
                prefs.splitOnBigScreens = on
            }
        }

        // Optional: once the keyboard is on and picked, until calibrated or dismissed.
        var calAsked by remember { mutableStateOf(prefs.calibrationAsked || prefs.calibrated.isNotEmpty()) }
        if (enabled && selected && !calAsked) {
            CalibrationStep(
                onStart = { navigate(SettingsActivity.SCREEN_CALIBRATE) },
                onSkip = { prefs.calibrationAsked = true; calAsked = true },
            )
        }

        val s = remember { overviewStatus(context, prefs) }
        Group(null) {
            NavRow("Theme", s.theme, SettingsIcons.Palette) { navigate("themes") }
            RowDivider()
            NavRow("Layout & size", s.layout, SettingsIcons.Height) { navigate("layout") }
            RowDivider()
            NavRow("Typing", s.typing, SettingsIcons.Keyboard) { navigate("typing") }
            RowDivider()
            NavRow("Corrections", s.corrections, SettingsIcons.Spellcheck) { navigate("corrections") }
            RowDivider()
            NavRow("Swipe & gestures", s.gestures, SettingsIcons.Swipe) { navigate("gestures") }
            RowDivider()
            NavRow("Emoji & GIFs", s.emoji, SettingsIcons.Emoji) { navigate("emoji") }
            RowDivider()
            NavRow("Clipboard & privacy", s.privacy, SettingsIcons.Lock) { navigate("privacy") }
        }
        Group(null) {
            NavRow("Help improve Bori", "Typo log and diagnostics", SettingsIcons.Build) { navigate("advanced") }
        }
    }
}

/** Second lines of the start page: each group's current state. */
private class OverviewStatus(
    val theme: String,
    val layout: String,
    val typing: String,
    val corrections: String,
    val gestures: String,
    val emoji: String,
    val privacy: String,
)

private fun overviewStatus(context: Context, prefs: Prefs): OverviewStatus {
    val themeName = runCatching { com.bori.keyboard.theme.ThemeStore(context).get(prefs.themeId).name }.getOrDefault("Classic")
    val mode = when (prefs.themeMode) {
        com.bori.keyboard.theme.ThemeMode.SYSTEM -> "Follows system"
        com.bori.keyboard.theme.ThemeMode.LIGHT -> "Light"
        com.bori.keyboard.theme.ThemeMode.DARK -> "Dark"
        com.bori.keyboard.theme.ThemeMode.NIGHT -> "Dark at night"
    }
    val posture = Posture.of(context)
    val height = "Height ${(prefs.keyHeightScale(posture) * 100).toInt()}%"
    val layout = listOfNotNull(
        height,
        if (prefs.numberRow) "Number row" else null,
        if (prefs.splitOnBigScreens) "Split when unfolded" else null,
    ).joinToString(" · ")
    val auto = AUTOCORRECT_CHOICES.firstOrNull { it.first == prefs.autocorrect }?.second ?: ""
    val corrections = "Autocorrect: $auto" + if (!prefs.showSuggestions) " · Suggestions off" else ""
    val gestures = if (prefs.glideTyping) "Glide typing on" else "Glide typing off"
    val emojiName = com.bori.keyboard.view.EmojiPack.STYLES.firstOrNull { it.first == prefs.emojiStyle }?.second ?: "Phone default"
    val emoji = "$emojiName · " + if (prefs.gifSearch) "GIFs on" else "GIFs off"
    val keep = CLIP_KEEP_CHOICES.firstOrNull { it.first == prefs.clipboardKeepMs }?.second ?: ""
    val clip = if (prefs.clipboardHistory) "Clipboard: $keep" else "Clipboard off"
    val privacy = clip + if (prefs.incognito) " · Incognito on" else " · Incognito off"
    return OverviewStatus("$themeName · $mode", layout, soundStatus(prefs), corrections, gestures, emoji, privacy)
}

/** Older pages use this name; it is now the standard settings page. */
@Composable
internal fun CategoryScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) =
    SettingsPage(title, onBack) { content() }

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
internal fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    done: Boolean,
    action: String,
    actionEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Step $number", style = MaterialTheme.typography.labelMedium)
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (done) Text("Done ✓", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            if (!done) Button(onClick = onClick, enabled = actionEnabled) { Text(action) }
        }
    }
}

/**
 * How far apart the split halves sit and how far they stay from the screen edges; hands and
 * grips differ. Saved when a slider is let go; an open keyboard updates straight away.
 */
@Composable
internal fun SplitSpacing(prefs: Prefs) {
    var gap by remember { mutableFloatStateOf(prefs.splitGap) }
    var edge by remember { mutableFloatStateOf(prefs.splitEdge) }
    SliderRow("Space between halves", gap, "${(gap * 100).toInt()}%", 0.1f..0.5f, onChange = { gap = it }, onDone = { prefs.splitGap = gap })
    SliderRow("Space at the edges", edge, "${(edge * 100).toInt()}%", 0f..0.2f, onChange = { edge = it }, onDone = { prefs.splitEdge = edge })
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = {
            gap = Prefs.DEFAULT_SPLIT_GAP
            edge = Prefs.DEFAULT_SPLIT_EDGE
            prefs.splitGap = gap
            prefs.splitEdge = edge
        }) { Text("Reset spacing") }
    }
}

/**
 * Onboarding card on an unfolded foldable or tablet: split (half the keys under each thumb)
 * or one full-width keyboard. The keyboard also asks once in its suggestion bar.
 */
@Composable
private fun BigScreenStep(split: Boolean, asked: Boolean, onChoose: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Step 3", style = MaterialTheme.typography.labelMedium)
            Text("Pick your big-screen layout", style = MaterialTheme.typography.titleMedium)
            Text(
                "On this big screen a full-width keyboard puts the middle keys out of thumb " +
                    "reach. Split puts half the keys under each thumb. The cover screen always " +
                    "uses the normal keyboard.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = asked && split, onClick = { onChoose(true) }, label = { Text("Split (recommended)") })
                FilterChip(selected = asked && !split, onClick = { onChoose(false) }, label = { Text("Full width") })
            }
            if (asked) Text("Done ✓ (change it any time under Appearance & layout)", color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Onboarding card: fit the keyboard to where the user's thumbs rest. */
@Composable
private fun CalibrationStep(onStart: () -> Unit, onSkip: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Optional", style = MaterialTheme.typography.labelMedium)
            Text("Fit the keys to your thumbs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hold the phone the way you type and rest your thumbs. Bori sets the keyboard " +
                    "height (and the split on big screens) to match, then learns your aim from a " +
                    "short sentence. Do it folded and unfolded.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart) { Text("Start") }
                TextButton(onClick = onSkip) { Text("Not now") }
            }
        }
    }
}

/** Preset swipe trail colours; the first two are the theme and rainbow options. */
private val TRAIL_COLORS = listOf(
    Prefs.TRAIL_THEME, Prefs.TRAIL_RAINBOW,
    0xFFFFFFFF.toInt(), 0xFF212121.toInt(), 0xFFE53935.toInt(), 0xFFFF9800.toInt(),
    0xFFFFEB3B.toInt(), 0xFF43A047.toInt(), 0xFF00BCD4.toInt(), 0xFF1E88E5.toInt(),
    0xFF8E24AA.toInt(), 0xFFEC407A.toInt(),
)

@Composable
internal fun TrailSettings(prefs: Prefs) {
    var color by remember { mutableStateOf(prefs.glideTrailColor) }
    var width by remember { mutableFloatStateOf(prefs.glideTrailWidth) }
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val rainbow = remember {
        androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
        )
    }
    Text("Trail colour")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in TRAIL_COLORS) {
            val selected = c == color
            val shape = androidx.compose.foundation.shape.CircleShape
            val base = Modifier
                .size(36.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) accent else outline, shape)
                .padding(4.dp)
            val fill = when (c) {
                Prefs.TRAIL_RAINBOW -> base.background(rainbow, shape)
                Prefs.TRAIL_THEME -> base.background(accent, shape)
                else -> base.background(Color(c), shape)
            }
            androidx.compose.foundation.layout.Box(
                fill
                    .clickable { color = c; prefs.glideTrailColor = c }
                    .semantics { contentDescription = trailColorName(c) },
            )
        }
    }
    Note(if (color == Prefs.TRAIL_THEME) "Theme colour: follows the keyboard's accent colour." else trailColorName(color))
    Text("Trail thickness: ${"%.0f".format(width)} dp")
    Slider(
        value = width,
        onValueChange = { width = it },
        onValueChangeFinished = { prefs.glideTrailWidth = width },
        valueRange = 2f..16f,
        steps = 13,
    )
    // Preview: a swipe-like curve drawn the way the keyboard draws it (tapering, fading).
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        val n = 60
        val px = width.dp.toPx()
        var prev: androidx.compose.ui.geometry.Offset? = null
        for (i in 0..n) {
            val t = i / n.toFloat()
            val p = androidx.compose.ui.geometry.Offset(
                size.width * (0.05f + 0.9f * t),
                size.height * (0.5f + 0.32f * kotlin.math.sin(t * 2 * Math.PI).toFloat()),
            )
            prev?.let { q ->
                val segColor = when (color) {
                    Prefs.TRAIL_RAINBOW -> Color.hsv((i * 4f * 2) % 360f, 0.85f, 1f)
                    Prefs.TRAIL_THEME -> accent
                    else -> Color(color)
                }
                drawLine(
                    segColor.copy(alpha = 0.25f + 0.75f * t),
                    q, p,
                    strokeWidth = px * (0.4f + 0.6f * t),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            prev = p
        }
    }
}

internal fun trailColorLabel(c: Int) = trailColorName(c)

private fun trailColorName(c: Int) = when (c) {
    Prefs.TRAIL_THEME -> "Theme colour"
    Prefs.TRAIL_RAINBOW -> "Rainbow"
    0xFFFFFFFF.toInt() -> "White"
    0xFF212121.toInt() -> "Black"
    0xFFE53935.toInt() -> "Red"
    0xFFFF9800.toInt() -> "Orange"
    0xFFFFEB3B.toInt() -> "Yellow"
    0xFF43A047.toInt() -> "Green"
    0xFF00BCD4.toInt() -> "Cyan"
    0xFF1E88E5.toInt() -> "Blue"
    0xFF8E24AA.toInt() -> "Purple"
    0xFFEC407A.toInt() -> "Pink"
    else -> "Custom colour"
}

@Composable
internal fun PrefSwitch(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = {
            checked = it
            onChange(it)
        })
    }
}

@Composable
internal fun DictionaryScreen(onBack: () -> Unit) = SettingsPage("Your dictionary", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val store = remember { UserStore.get(context) }
    var version by remember { mutableStateOf(0) }
    val personal = remember(version) { store.listPersonal() }
    val learned = remember(version) { store.listLearned() }
    val blocked = remember(version) { store.listBlocked() }
    var newWord by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("en") }
    var confirmClear by remember { mutableStateOf(false) }
    var offensive by remember { mutableStateOf(prefs.blockOffensive) }

    Group("Add a word") {
        RowContent {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = lang == "en", onClick = { lang = "en" }, label = { Text("English") })
                FilterChip(selected = lang == "es", onClick = { lang = "es" }, label = { Text("Español") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it.filter { c -> c.isLetter() || c == '\'' || c == '-' } },
                    label = { Text("Word") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    store.addPersonal(lang, newWord)
                    newWord = ""
                    version++
                }, enabled = newWord.isNotBlank()) { Text("Add") }
            }
        }
    }
    WordSection("Added by you", personal, "Remove") { store.remove("personal", it); version++ }
    WordSection("Learned from your typing", learned, "Forget") { store.remove("words", it); version++ }
    Group("Blocked") {
        SwitchRow("Block offensive words", offensive) { offensive = it; prefs.blockOffensive = it }
    }
    WordSection("Blocked by you", blocked, "Unblock") { store.remove("blocked", it); version++ }

    Group(null) {
        NavRow("Forget everything learned", "Words you added stay") { confirmClear = true }
    }
    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Forget everything learned?") },
            text = { Text("All learned words and word pairs go. Words you added stay.") },
            confirmButton = { TextButton(onClick = { store.clearLearned(); confirmClear = false; version++ }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** A word list in a group: the first few, "Show all" for the rest. */
@Composable
private fun WordSection(title: String, entries: List<UserStore.Entry>, action: String, onAction: (UserStore.Entry) -> Unit) {
    var all by remember { mutableStateOf(false) }
    Group("$title (${entries.size})") {
        if (entries.isEmpty()) {
            RowContent { Text("None yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Group
        }
        val shown = if (all) entries.take(300) else entries.take(WORDS_SHOWN)
        for ((i, e) in shown.withIndex()) {
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.word, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(e.lang, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onAction(e) }) { Text(action) }
            }
        }
        if (entries.size > WORDS_SHOWN) {
            RowDivider()
            TextButton(onClick = { all = !all }, modifier = Modifier.padding(start = 4.dp)) {
                Text(if (all) "Show fewer" else "Show all ${entries.size}")
            }
        }
    }
}

private const val WORDS_SHOWN = 5

/** Opt-in log of the user's own corrections, copied out for tuning (see TypoRecorder). */
@Composable
internal fun DiagnosticsSection() {
    val context = LocalContext.current
    var size by remember { mutableStateOf(com.bori.keyboard.ime.DiagLog.export(context).length) }
    var copied by remember { mutableStateOf(false) }
    Text(
        "If the keyboard crashes or won't open, this shows when it opened, closed and resized, " +
            "and the crash. Never what you type.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Bori diagnostics", com.bori.keyboard.ime.DiagLog.export(context)))
            copied = true
        }, enabled = size > 0) { Text("Copy diagnostics") }
        OutlinedButton(onClick = {
            com.bori.keyboard.ime.DiagLog.clear(context)
            size = 0
            copied = false
        }, enabled = size > 0) { Text("Clear") }
    }
    if (copied) Text("Copied. Paste it into your chat with Claude.", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun TypoLogSection(prefs: Prefs) {
    val context = LocalContext.current
    var count by remember { mutableStateOf(com.bori.keyboard.ime.TypoRecorder.count(context)) }
    var copied by remember { mutableStateOf(false) }
    PrefSwitch("Keep a typo log for tuning", prefs.typoLog) { prefs.typoLog = it }
    Text(
        "Saves what you typed and what you chose each time you pick a suggestion or undo a fix. " +
            "Never in password or incognito fields.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Bori typos", com.bori.keyboard.ime.TypoRecorder.export(context)))
            copied = true
        }, enabled = count > 0) { Text("Copy typo log ($count)") }
        OutlinedButton(onClick = {
            com.bori.keyboard.ime.TypoRecorder.clear(context)
            count = 0
            copied = false
        }, enabled = count > 0) { Text("Clear") }
    }
    if (copied) Text("Copied. Paste it into your chat with Claude.", style = MaterialTheme.typography.bodySmall)
}

/** One posture's key height; resizes the keyboard live while dragging. */
@Composable
internal fun HeightSlider(prefs: Prefs, p: Posture, title: String) {
    // Keyed by posture: after a fold or rotation each slider keeps its own value.
    var h by remember(p) { mutableFloatStateOf(prefs.keyHeightScale(p)) }
    SliderRow(title, h, "${(h * 100).toInt()}%", Prefs.HEIGHT_MIN..Prefs.HEIGHT_MAX, onChange = {
        h = it
        prefs.setKeyHeightScale(p, it)
    })
}
