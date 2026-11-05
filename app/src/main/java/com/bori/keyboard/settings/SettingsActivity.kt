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
import com.bori.keyboard.theme.ThemeMode
import com.bori.keyboard.view.BigScreen
import kotlinx.coroutines.delay

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoriTheme {
                Surface(Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf("main") }
                    val back = { screen = parentOf(screen) }
                    BackHandler(enabled = screen != "main") { back() }
                    val go = { to: String -> screen = to }
                    when (screen) {
                        "typing" -> TypingScreen(back)
                        "corrections" -> CorrectionsScreen(back, go)
                        "gestures" -> GesturesScreen(back, go)
                        "layout" -> LayoutScreen(back)
                        "privacy" -> PrivacyScreen(back, go)
                        "advanced" -> AdvancedScreen(back)
                        "dictionary" -> DictionaryScreen(back)
                        "lab" -> SwipeTestLabScreen(back)
                        "trainer" -> SwipeTrainerScreen(back)
                        else -> SettingsScreen(go)
                    }
                }
            }
        }
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
    "dictionary" -> "corrections"
    "trainer", "lab" -> "gestures"
    else -> "main"
}

/** The start screen: setup until it's done, a test box, then one row per settings category. */
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

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Bori Keyboard", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Private, offline keyboard. Nothing you type leaves this phone.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (!enabled || !selected) {
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
        // Until a choice is made; afterwards it lives under Appearance & layout.
        if (!splitAsked && LocalConfiguration.current.smallestScreenWidthDp >= BigScreen.MIN_DP) {
            BigScreenStep(split, splitAsked) { on ->
                split = on
                splitAsked = true
                prefs.splitOnBigScreens = on
            }
        }

        TryItField()

        CategoryRow("Typing", "Capitals, double-space period, vibration and sound") { navigate("typing") }
        CategoryRow("Corrections & suggestions", "Autocorrect strength, suggestions, your dictionary") {
            navigate("corrections")
        }
        CategoryRow("Swipe & gestures", "Glide typing, cursor slide, flicks, Swipe Trainer") { navigate("gestures") }
        CategoryRow("Appearance & layout", "Dark mode, number row, key height, split keyboard") { navigate("layout") }
        CategoryRow("Privacy & clipboard", "Incognito, what Bori learns, clipboard history") { navigate("privacy") }
        CategoryRow("Help improve Bori", "Typo log and diagnostics to share when something's wrong") {
            navigate("advanced")
        }
    }
}

@Composable
private fun CategoryRow(title: String, summary: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun TryItField() {
    var test by remember { mutableStateOf("") }
    OutlinedTextField(
        value = test,
        onValueChange = { test = it },
        label = { Text("Try it here") },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A category page: back button, title, a test box at the top, then the settings. */
@Composable
private fun CategoryScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        TryItField()
        content()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TypingScreen(onBack: () -> Unit) = CategoryScreen("Typing", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    PrefSwitch("Capitalise sentences", prefs.autoCapitalize) { prefs.autoCapitalize = it }
    PrefSwitch("Double-space types a period", prefs.doubleSpacePeriod) { prefs.doubleSpacePeriod = it }

    SectionTitle("Key press feedback")
    PrefSwitch("Vibrate on key press", prefs.haptics) { prefs.haptics = it }
    val feedback = remember { com.bori.keyboard.ime.Feedback(context, prefs) }
    var strength by remember { mutableFloatStateOf(prefs.vibrationStrength.toFloat()) }
    Text("Vibration strength: ${strength.toInt()}%")
    Slider(
        value = strength,
        onValueChange = {
            // Buzz at each 5% step while dragging so the strength can be felt.
            if (it.toInt() / 5 != strength.toInt() / 5) feedback.vibrate(it.toInt())
            strength = it
        },
        onValueChangeFinished = {
            prefs.vibrationStrength = strength.toInt()
            feedback.vibrate(strength.toInt())
        },
        valueRange = 1f..100f,
    )
    PrefSwitch("Sound on key press", prefs.sound) { prefs.sound = it }
    PrefSwitch("Show key preview", prefs.keyPreview) { prefs.keyPreview = it }
}

@Composable
private fun CorrectionsScreen(onBack: () -> Unit, navigate: (String) -> Unit) =
    CategoryScreen("Corrections & suggestions", onBack) {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        Text("Autocorrect")
        var level by remember { mutableStateOf(prefs.autocorrect) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (l in Aggressiveness.entries) {
                FilterChip(
                    selected = level == l,
                    onClick = { level = l; prefs.autocorrect = l },
                    label = { Text(l.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Note("Backspace right after an autocorrection puts back what you typed.")

        SectionTitle("Suggestion bar")
        PrefSwitch("Show suggestions", prefs.showSuggestions) { prefs.showSuggestions = it }
        PrefSwitch("Next-word predictions", prefs.nextWordPrediction) { prefs.nextWordPrediction = it }
        PrefSwitch("Block offensive words", prefs.blockOffensive) { prefs.blockOffensive = it }
        PrefSwitch("Fix words you tap on", prefs.tapToFix) { prefs.tapToFix = it }
        Note("Tap a word you already typed and the bar shows fixes for it; tap one to swap it in.")
        PrefSwitch("Follow apps that turn suggestions off", prefs.followNoSuggestions) { prefs.followNoSuggestions = it }
        Note("Some apps, like the Google search box, ask keyboards for no suggestions. Leave this off to keep swipe and corrections there too. Passwords, emails and web addresses never get them.")

        SectionTitle("Words")
        Note("Add your own words, unblock words, or make Bori forget words it learned.")
        OutlinedButton(onClick = { navigate("dictionary") }) { Text("Your dictionary") }
    }

@Composable
private fun GesturesScreen(onBack: () -> Unit, navigate: (String) -> Unit) =
    CategoryScreen("Swipe & gestures", onBack) {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        SectionTitle("Glide typing")
        PrefSwitch("Glide typing (swipe across letters)", prefs.glideTyping) { prefs.glideTyping = it }
        var trailOn by remember { mutableStateOf(prefs.glideTrail) }
        PrefSwitch("Show swipe trail", trailOn) { trailOn = it; prefs.glideTrail = it }
        if (trailOn) TrailSettings(prefs)
        PrefSwitch("Learn my swipe style", prefs.adaptSwipe) { prefs.adaptSwipe = it }
        Note("The Swipe Trainer teaches Bori your swipe style; the Test Lab shows how it reads swipes.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { navigate("trainer") }) { Text("Swipe Trainer") }
            OutlinedButton(onClick = { navigate("lab") }) { Text("Swipe Test Lab") }
        }

        SectionTitle("Gestures")
        PrefSwitch("Slide on spacebar to move cursor", prefs.spaceCursor) { prefs.spaceCursor = it }
        PrefSwitch("Slide left from delete to erase words", prefs.deleteSlide) { prefs.deleteSlide = it }
        PrefSwitch("Flick up on a key for its number or symbol", prefs.keyFlicks) { prefs.keyFlicks = it }
    }

@Composable
private fun LayoutScreen(onBack: () -> Unit) = CategoryScreen("Appearance & layout", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    Text("Theme")
    var mode by remember { mutableStateOf(prefs.themeMode) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((m, label) in listOf(
            ThemeMode.SYSTEM to "Follow system",
            ThemeMode.LIGHT to "Light",
            ThemeMode.DARK to "Dark",
            ThemeMode.NIGHT to "Dark at night",
        )) {
            FilterChip(selected = mode == m, onClick = { mode = m; prefs.themeMode = m }, label = { Text(label) })
        }
    }
    Note("“Dark at night” goes dark from 7 pm to 7 am by the phone's clock.")

    SectionTitle("Emoji style")
    EmojiStyleSettings(prefs)

    SectionTitle("Layout & size")
    PrefSwitch("Number row", prefs.numberRow) { prefs.numberRow = it }
    var height by remember { mutableFloatStateOf(prefs.keyHeightScale) }
    Text("Key height: ${(height * 100).toInt()}%")
    Slider(
        value = height,
        onValueChange = { height = it },
        onValueChangeFinished = { prefs.keyHeightScale = height },
        valueRange = 0.8f..1.3f,
    )

    SectionTitle("Big screens (unfolded foldables, tablets)")
    var split by remember { mutableStateOf(prefs.splitOnBigScreens) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Split keyboard", Modifier.weight(1f))
        Switch(checked = split, onCheckedChange = {
            split = it
            prefs.splitOnBigScreens = it
        })
    }
    Note("Puts half the keys under each thumb. The cover screen always uses the normal keyboard.")
    if (split) SplitSpacing(prefs)
}

@Composable
private fun PrivacyScreen(onBack: () -> Unit, navigate: (String) -> Unit) = CategoryScreen("Privacy", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    PrefSwitch("Incognito (don't learn new words)", prefs.incognito) { prefs.incognito = it }
    Note(
        "Bori never learns from password fields or apps that ask for private input. " +
            "Learned words stay on this phone.",
    )

    SectionTitle("Clipboard")
    PrefSwitch("Clipboard history", prefs.clipboardHistory) { prefs.clipboardHistory = it }
    PrefSwitch("Offer copied text in the suggestion bar", prefs.clipboardChip) { prefs.clipboardChip = it }
    Note(
        "The 📋 button above the keys shows what you copied in the last hour; pin clips to keep " +
            "them. Copies are stored on this phone only, never while incognito, and never when " +
            "the app marks them as passwords.",
    )
    OutlinedButton(onClick = { navigate("dictionary") }) { Text("Your dictionary") }
}

@Composable
private fun AdvancedScreen(onBack: () -> Unit) = CategoryScreen("Help improve Bori", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    Note("Nothing here is sent anywhere. You copy it and paste it into a chat yourself.")
    SectionTitle("Typo log")
    TypoLogSection(prefs)
    SectionTitle("Diagnostics")
    DiagnosticsSection()
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
 * grips differ. The keys take whatever width is left. Saved when a slider is let go, and an
 * open keyboard updates straight away.
 */
@Composable
private fun SplitSpacing(prefs: Prefs) {
    var gap by remember { mutableFloatStateOf(prefs.splitGap) }
    var edge by remember { mutableFloatStateOf(prefs.splitEdge) }
    Text("Space between the halves: ${(gap * 100).toInt()}%")
    Slider(
        value = gap,
        onValueChange = { gap = it },
        onValueChangeFinished = { prefs.splitGap = gap },
        valueRange = 0.1f..0.5f,
    )
    Text("Space at the screen edges: ${(edge * 100).toInt()}%")
    Slider(
        value = edge,
        onValueChange = { edge = it },
        onValueChangeFinished = { prefs.splitEdge = edge },
        valueRange = 0f..0.2f,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Unfold and tap “Try it here” to see the keys move as you change these.",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = {
            gap = Prefs.DEFAULT_SPLIT_GAP
            edge = Prefs.DEFAULT_SPLIT_EDGE
            prefs.splitGap = gap
            prefs.splitEdge = edge
        }) { Text("Reset") }
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

private val EMOJI_PREVIEW = listOf("😀", "😂", "🥰", "😎", "👍", "🔥", "🐶", "🍕", "🎉", "❤️", "🇵🇷")

@Composable
private fun EmojiStyleSettings(prefs: Prefs) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(prefs.emojiStyle) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((id, name, _) in EmojiPack.STYLES) {
            FilterChip(selected = style == id, onClick = { style = id; prefs.emojiStyle = id }, label = { Text(name) })
        }
    }
    val pack = remember(style) { EmojiPack.get(context, style) }
    val custom = remember { EmojiPack.custom(context) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (e in EMOJI_PREVIEW) {
            val bmp = remember(pack, e) { (custom?.bitmap(e) ?: pack?.bitmap(e))?.asImageBitmap() }
            if (bmp != null) {
                androidx.compose.foundation.Image(bmp, contentDescription = e, modifier = Modifier.size(32.dp))
            } else {
                Text(e, fontSize = 24.sp)
            }
        }
    }
    Note(
        "Changes how emoji look in Bori's emoji panel. The person you send them to sees their own " +
            "phone's emoji. The Puerto Rico flag 🇵🇷 shows with its light-blue triangle in every style. " + EmojiPack.STYLES.first { it.first == style }.third + ".",
    )
}

/** Preset swipe trail colours; the first two are the theme and rainbow options. */
private val TRAIL_COLORS = listOf(
    Prefs.TRAIL_THEME, Prefs.TRAIL_RAINBOW,
    0xFFFFFFFF.toInt(), 0xFF212121.toInt(), 0xFFE53935.toInt(), 0xFFFF9800.toInt(),
    0xFFFFEB3B.toInt(), 0xFF43A047.toInt(), 0xFF00BCD4.toInt(), 0xFF1E88E5.toInt(),
    0xFF8E24AA.toInt(), 0xFFEC407A.toInt(),
)

@Composable
private fun TrailSettings(prefs: Prefs) {
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
private fun DictionaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { UserStore.get(context) }
    var version by remember { mutableStateOf(0) }
    val personal = remember(version) { store.listPersonal() }
    val learned = remember(version) { store.listLearned() }
    val blocked = remember(version) { store.listBlocked() }
    var newWord by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("en") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Your dictionary", style = MaterialTheme.typography.headlineSmall)

        Text("Add a word", style = MaterialTheme.typography.titleMedium)
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

        WordSection("Added by you", personal, "Remove") { store.remove("personal", it); version++ }
        WordSection("Learned from your typing", learned, "Forget") { store.remove("words", it); version++ }
        WordSection("Blocked from suggestions", blocked, "Unblock") { store.remove("blocked", it); version++ }

        if (!confirmClear) {
            OutlinedButton(onClick = { confirmClear = true }) { Text("Forget everything learned") }
        } else {
            Text("Forget all learned words and word pairs? Words you added stay.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { store.clearLearned(); confirmClear = false; version++ }) { Text("Forget") }
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun WordSection(title: String, entries: List<UserStore.Entry>, action: String, onAction: (UserStore.Entry) -> Unit) {
    Text("$title (${entries.size})", style = MaterialTheme.typography.titleMedium)
    if (entries.isEmpty()) {
        Text("None yet", style = MaterialTheme.typography.bodySmall)
        return
    }
    for (e in entries.take(200)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(e.word, Modifier.weight(1f))
            Text(e.lang, style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { onAction(e) }) { Text(action) }
        }
        HorizontalDivider()
    }
}

/** Opt-in log of the user's own corrections, copied out for tuning (see TypoRecorder). */
@Composable
private fun DiagnosticsSection() {
    val context = LocalContext.current
    var size by remember { mutableStateOf(com.bori.keyboard.ime.DiagLog.export(context).length) }
    var copied by remember { mutableStateOf(false) }
    Text(
        "If the keyboard crashes or won't open, Bori notes when it opened, closed " +
            "and resized (and the crash details). Never what you type.",
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
private fun TypoLogSection(prefs: Prefs) {
    val context = LocalContext.current
    var count by remember { mutableStateOf(com.bori.keyboard.ime.TypoRecorder.count(context)) }
    var copied by remember { mutableStateOf(false) }
    PrefSwitch("Keep a typo log for tuning", prefs.typoLog) { prefs.typoLog = it }
    Text(
        "When on, each time you pick a suggestion or undo an autocorrect, Bori saves what you " +
            "typed, what you chose and the word before it. Not in password or incognito fields. " +
            "It stays on this phone until you copy it.",
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
