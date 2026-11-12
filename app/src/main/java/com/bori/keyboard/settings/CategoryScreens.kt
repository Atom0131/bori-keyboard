package com.bori.keyboard.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bori.keyboard.ime.SoundPacks
import com.bori.keyboard.layout.Key
import com.bori.keyboard.layout.KeyCode
import com.bori.keyboard.predict.Aggressiveness
import com.bori.keyboard.view.BigScreen
import com.bori.keyboard.view.EmojiPack
import com.bori.keyboard.view.Posture

/*
 * The settings category pages, in the order of the start page. Labels follow the Android
 * settings guide: short, the important word first, the second line shows the current state;
 * options that depend on a switch sit right under it and only show (or work) while it's on.
 */

internal val AUTOCORRECT_CHOICES = listOf(
    Aggressiveness.OFF to "Off",
    Aggressiveness.MILD to "Mild",
    Aggressiveness.NORMAL to "Normal",
    Aggressiveness.AGGRESSIVE to "Strong",
)

internal val CLIP_KEEP_CHOICES = listOf(
    Prefs.CLIP_KEEP_HOUR to "1 hour",
    Prefs.CLIP_KEEP_DAY to "1 day",
    Prefs.CLIP_KEEP_WEEK to "1 week",
    Prefs.CLIP_KEEP_FOREVER to "Until deleted",
)

private val GIF_FILTERS = listOf("high" to "Strict", "medium" to "Medium", "low" to "Low", "off" to "Off")

// ------------------------------------------------------------------ Typing

@Composable
internal fun TypingScreen(onBack: () -> Unit, navigate: (String) -> Unit) = SettingsPage("Typing", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var caps by remember { mutableStateOf(prefs.autoCapitalize) }
    var period by remember { mutableStateOf(prefs.doubleSpacePeriod) }
    var preview by remember { mutableStateOf(prefs.keyPreview) }
    Group(null) {
        SwitchRow("Auto-capitalize", caps, status = "Capital letter at the start of sentences") {
            caps = it; prefs.autoCapitalize = it
        }
        RowDivider()
        SwitchRow("Double-space period", period, status = "Two spaces type “. ”") { period = it; prefs.doubleSpacePeriod = it }
        RowDivider()
        SwitchRow("Key preview", preview, status = "Shows the letter bigger as you press") { preview = it; prefs.keyPreview = it }
    }
    Group(null) {
        NavRow("Sound & vibration", soundStatus(prefs)) { navigate("sound") }
    }
}

internal fun soundStatus(prefs: Prefs): String {
    val sound = if (!prefs.sound) "Sound off" else
        SoundPacks.PACKS.firstOrNull { it.first == prefs.soundPack }?.second ?: "Sound on"
    val vibration = if (prefs.haptics) "Vibration ${prefs.vibrationStrength}%" else "Vibration off"
    return "$sound · $vibration"
}

/** Typing → Sound & vibration. */
@Composable
internal fun SoundVibrationScreen(onBack: () -> Unit) = SettingsPage("Sound & vibration", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val feedback = remember { com.bori.keyboard.ime.Feedback(context, prefs) }
    androidx.compose.runtime.DisposableEffect(feedback) { onDispose { feedback.release() } }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var strength by remember { mutableFloatStateOf(prefs.vibrationStrength.toFloat()) }
    var sound by remember { mutableStateOf(prefs.sound) }
    var pack by remember { mutableStateOf(prefs.soundPack) }
    var volume by remember { mutableFloatStateOf(prefs.soundVolume) }
    val sample = remember { Key("a", KeyCode.Text("a")) }
    Group("Vibration") {
        SwitchRow("Vibration", haptics) { haptics = it; prefs.haptics = it }
        if (haptics) {
            SliderRow("Strength", strength, "${strength.toInt()}%", 1f..100f, onChange = {
                // Buzz at each 5% step while dragging so the strength can be felt.
                if (it.toInt() / 5 != strength.toInt() / 5) feedback.vibrate(it.toInt())
                strength = it
            }, onDone = {
                prefs.vibrationStrength = strength.toInt()
                feedback.vibrate(strength.toInt())
            })
        }
    }
    Group("Sound") {
        SwitchRow("Sound", sound) { sound = it; prefs.sound = it }
        if (sound) {
            RowDivider()
            ChoiceRow("Sound type", SoundPacks.PACKS, pack) {
                pack = it
                prefs.soundPack = it
                feedback.playSound(sample, it, volume)
            }
            if (pack != SoundPacks.SYSTEM) {
                SliderRow("Volume", volume, "${(volume * 100).toInt()}%", 0.05f..1f, onChange = { volume = it }, onDone = {
                    prefs.soundVolume = volume
                    feedback.playSound(sample, pack, volume)
                })
            }
        }
    }
    Footnote("Sounds follow silent mode and stay off in password fields. Piano plays a note for each letter.")
}

// ------------------------------------------------------------------ Corrections

@Composable
internal fun CorrectionsScreen(onBack: () -> Unit, navigate: (String) -> Unit) = SettingsPage("Corrections", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var level by remember { mutableStateOf(prefs.autocorrect) }
    var show by remember { mutableStateOf(prefs.showSuggestions) }
    var next by remember { mutableStateOf(prefs.nextWordPrediction) }
    var tapFix by remember { mutableStateOf(prefs.tapToFix) }
    var follow by remember { mutableStateOf(prefs.followNoSuggestions) }

    Group(null) {
        ChoiceRow(
            "Autocorrect",
            AUTOCORRECT_CHOICES,
            level,
            status = (AUTOCORRECT_CHOICES.firstOrNull { it.first == level }?.second ?: "") + " · Backspace undoes a fix",
        ) { level = it; prefs.autocorrect = it }
    }
    Group("Suggestions") {
        SwitchRow("Suggestion bar", show) { show = it; prefs.showSuggestions = it }
        RowDivider()
        SwitchRow("Next-word predictions", next, enabled = show) { next = it; prefs.nextWordPrediction = it }
        RowDivider()
        SwitchRow("Fix tapped words", tapFix, status = "Tap a typed word to see fixes") { tapFix = it; prefs.tapToFix = it }
        RowDivider()
        SwitchRow(
            "Respect apps' no-suggestion request",
            follow,
            info = "Some apps, like the Google search box, ask keyboards for no suggestions. With this off, " +
                "swipe and corrections keep working there. Passwords, emails and web addresses never get suggestions.",
        ) { follow = it; prefs.followNoSuggestions = it }
    }
    Group("Learning") {
        NavRow("Your dictionary", "Added, learned and blocked words") { navigate("dictionary") }
        RowDivider()
        NavRow("Touch heat map", if (prefs.touchMap) "Learning where you tap" else "Off") { navigate("touchmap") }
    }
}

// ------------------------------------------------------------------ Swipe & gestures

@Composable
internal fun GesturesScreen(onBack: () -> Unit, navigate: (String) -> Unit) = SettingsPage("Swipe & gestures", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var glide by remember { mutableStateOf(prefs.glideTyping) }
    var adapt by remember { mutableStateOf(prefs.adaptSwipe) }
    var space by remember { mutableStateOf(prefs.spaceCursor) }
    var del by remember { mutableStateOf(prefs.deleteSlide) }
    var flicks by remember { mutableStateOf(prefs.keyFlicks) }

    Group("Glide typing") {
        SwitchRow("Glide typing", glide, status = "Slide across letters to type a word") { glide = it; prefs.glideTyping = it }
        if (glide) {
            RowDivider()
            NavRow("Swipe trail", trailStatus(prefs)) { navigate("trail") }
            RowDivider()
            SwitchRow("Learn my swipe style", adapt) { adapt = it; prefs.adaptSwipe = it }
        }
    }
    if (glide) {
        Group("Practice") {
            NavRow("Swipe Trainer", "Teach Bori how you swipe") { navigate("trainer") }
            RowDivider()
            NavRow("Swipe Test Lab", "See how swipes are read") { navigate("lab") }
        }
    }
    Group("Gestures") {
        SwitchRow("Cursor slide", space, status = "Slide on the space bar to move the cursor") { space = it; prefs.spaceCursor = it }
        RowDivider()
        SwitchRow("Delete slide", del, status = "Slide left from ⌫ to erase words") { del = it; prefs.deleteSlide = it }
        RowDivider()
        SwitchRow("Flick for symbols", flicks, status = "Flick up on a key for its number or symbol") { flicks = it; prefs.keyFlicks = it }
    }
}

private fun trailStatus(prefs: Prefs): String =
    if (!prefs.glideTrail) "Off" else "On · " + trailColorLabel(prefs.glideTrailColor) + " · ${prefs.glideTrailWidth.toInt()} dp"

/** Swipe & gestures → Swipe trail. */
@Composable
internal fun TrailScreen(onBack: () -> Unit) = SettingsPage("Swipe trail", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var trail by remember { mutableStateOf(prefs.glideTrail) }
    Group(null) {
        SwitchRow("Swipe trail", trail, status = "A line follows your finger while you swipe") { trail = it; prefs.glideTrail = it }
        if (trail) {
            RowDivider()
            RowContent { TrailSettings(prefs) }
        }
    }
}

// ------------------------------------------------------------------ Layout & size

@Composable
internal fun LayoutScreen(onBack: () -> Unit, navigate: (String) -> Unit) = SettingsPage("Layout & size", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var numberRow by remember { mutableStateOf(prefs.numberRow) }
    val now = Posture.of(LocalConfiguration.current)

    Group("Size") {
        NavRow("Fit to my thumbs", fitStatus(prefs)) { navigate(SettingsActivity.SCREEN_CALIBRATE) }
        RowDivider()
        HeightSlider(prefs, now, "Key height")
        RowDivider()
        NavRow("Other ways of holding", "Each has its own height") { navigate("heights") }
    }
    Group("Layout") {
        SwitchRow("Number row", numberRow) { numberRow = it; prefs.numberRow = it }
        RowDivider()
        NavRow("Split keyboard", splitStatus(prefs)) { navigate("split") }
    }
}

private fun splitStatus(prefs: Prefs): String =
    if (prefs.splitOnBigScreens) "On when unfolded · gap ${(prefs.splitGap * 100).toInt()}%" else "Off"

/** Layout & size → Other ways of holding: one height per posture. */
@Composable
internal fun HeightsScreen(onBack: () -> Unit) = SettingsPage("Key height", onBack) {
    val now = Posture.of(LocalConfiguration.current)
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    Group(null) {
        for ((i, p) in Posture.entries.withIndex()) {
            if (i > 0) RowDivider()
            HeightSlider(prefs, p, p.label + if (p == now) " (now)" else "")
        }
    }
    Footnote("Fold, unfold or turn the phone to try a height live.")
}

/** Layout & size → Split keyboard. */
@Composable
internal fun SplitScreen(onBack: () -> Unit) = SettingsPage("Split keyboard", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var split by remember { mutableStateOf(prefs.splitOnBigScreens) }
    Group(null) {
        SwitchRow("Split keyboard", split, status = "Half the keys under each thumb when unfolded") {
            split = it; prefs.splitOnBigScreens = it
        }
        if (split) {
            RowDivider()
            SplitSpacing(prefs)
        }
    }
    if (LocalConfiguration.current.smallestScreenWidthDp < BigScreen.MIN_DP) {
        Footnote("Unfold the phone to see the split keys move as you change these.")
    }
}

private fun fitStatus(prefs: Prefs): String {
    val n = prefs.calibrated.size
    return if (n == 0) "Set height from where your thumbs rest" else "Done for $n of ${Posture.entries.size} ways of holding"
}

// ------------------------------------------------------------------ Emoji & GIFs

@Composable
internal fun EmojiGifScreen(onBack: () -> Unit, navigate: (String) -> Unit) = SettingsPage("Emoji & GIFs", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var style by remember { mutableStateOf(prefs.emojiStyle) }

    Group(null) {
        ChoiceRow("Emoji style", EmojiPack.STYLES.map { it.first to it.second }, style) { style = it; prefs.emojiStyle = it }
        RowContent { EmojiPreviewRow(style) }
    }
    Footnote("Only changes emoji in Bori's panel; people see their own phone's emoji.")
    Group(null) {
        NavRow("GIF search", if (prefs.gifSearch) "On · powered by KLIPY" else "Off") { navigate(SettingsActivity.SCREEN_GIFS) }
    }
}

/** Emoji & GIFs → GIF search (the keyboard's GIF panel links straight here). */
@Composable
internal fun GifSearchScreen(onBack: () -> Unit) = SettingsPage("GIF search", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var gifs by remember { mutableStateOf(prefs.gifSearch) }
    var key by remember { mutableStateOf(prefs.klipyKey) }
    var filter by remember { mutableStateOf(prefs.gifFilter) }
    var forgot by remember { mutableStateOf(false) }
    Group(null) {
        SwitchRow(
            "GIF search",
            gifs,
            status = if (gifs) "Online, powered by KLIPY" else "Off · the only online feature",
            info = "Find GIFs from the GIF button in the emoji panel. What you search for, your country, the " +
                "safe-search level and a random ID (not linked to you) go to KLIPY. What you type in apps " +
                "never goes online. GIFs you send are remembered on this phone under Recent.",
        ) { gifs = it; prefs.gifSearch = it }
    }
    if (gifs) {
        Group("KLIPY key") {
            RowContent {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim(); prefs.klipyKey = it.trim() },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RowDivider()
            NavRow("Get a free key", "partner.klipy.com") {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://partner.klipy.com"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        Group(null) {
            ChoiceRow("Safe search", GIF_FILTERS, filter) { filter = it; prefs.gifFilter = it }
            RowDivider()
            NavRow("Forget recent GIFs", if (forgot) "Done" else "Clears the Recent list", enabled = !forgot) {
                prefs.recentGifs = "[]"; forgot = true
            }
        }
    }
}

// ------------------------------------------------------------------ Clipboard & privacy

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) = SettingsPage("Clipboard & privacy", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var incognito by remember { mutableStateOf(prefs.incognito) }
    var history by remember { mutableStateOf(prefs.clipboardHistory) }
    var chip by remember { mutableStateOf(prefs.clipboardChip) }
    var keep by remember { mutableStateOf(prefs.clipboardKeepMs) }

    Group("Privacy") {
        SwitchRow(
            "Incognito",
            incognito,
            status = if (incognito) "On · new words aren't learned" else "Off",
            info = "Bori never learns from password fields or apps that ask for private input. " +
                "Everything it learns stays on this phone.",
        ) { incognito = it; prefs.incognito = it }
    }
    Group("Clipboard") {
        SwitchRow(
            "Clipboard history",
            history,
            info = "The 📋 button above the keys shows text and pictures you copied (up to 200). Pin clips to keep " +
                "them, and use 🔍 to find one. Stored on this phone only; never while incognito or for copies " +
                "marked as passwords.",
        ) { history = it; prefs.clipboardHistory = it }
        if (history) {
            RowDivider()
            ChoiceRow("Keep copies for", CLIP_KEEP_CHOICES, keep) { keep = it; prefs.clipboardKeepMs = it }
        }
        RowDivider()
        SwitchRow("Suggest copied text", chip, status = "Shows it in the suggestion bar") { chip = it; prefs.clipboardChip = it }
    }
}

// ------------------------------------------------------------------ Help improve Bori

@Composable
internal fun HelpScreen(onBack: () -> Unit) = SettingsPage("Help improve Bori", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    Footnote("Nothing here is sent anywhere. You copy it and paste it into a chat yourself.")
    Group("Typo log") { RowContent { TypoLogSection(prefs) } }
    Group("Diagnostics") { RowContent { DiagnosticsSection() } }
}

/** A row of emoji drawn in [style], so the choice can be seen. */
@Composable
internal fun EmojiPreviewRow(style: String) {
    val context = LocalContext.current
    val pack = remember(style) { EmojiPack.get(context, style) }
    val custom = remember { EmojiPack.custom(context) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (e in EMOJI_PREVIEW) {
            val bmp = remember(pack, e) { (custom?.bitmap(e) ?: pack?.bitmap(e))?.asImageBitmap() }
            if (bmp != null) {
                Image(bmp, contentDescription = e, modifier = Modifier.size(32.dp))
            } else {
                Text(e, fontSize = 24.sp)
            }
        }
    }
}

private val EMOJI_PREVIEW = listOf("😀", "😂", "🥰", "😎", "👍", "🔥", "🐶", "🍕", "🎉", "❤️", "🇵🇷")
