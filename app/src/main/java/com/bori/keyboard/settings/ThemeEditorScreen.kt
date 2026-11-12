package com.bori.keyboard.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bori.keyboard.theme.Fonts
import com.bori.keyboard.theme.KeyLook
import com.bori.keyboard.theme.KeyShape
import com.bori.keyboard.theme.KeyStyle
import com.bori.keyboard.theme.KeyboardTheme
import com.bori.keyboard.theme.ThemeColors
import com.bori.keyboard.theme.ThemeOverrides
import com.bori.keyboard.theme.ThemeResolver
import com.bori.keyboard.theme.ThemeSpec
import com.bori.keyboard.theme.ThemeStore
import com.bori.keyboard.theme.ThemeVariant

/** Settings route for the editor: EDIT_PREFIX + theme id. */
internal const val EDIT_PREFIX = "edit:"

/** One paintable part of a theme: its name, how to read it from a resolved theme, how to set it. */
private enum class Part(val title: String) {
    BACKGROUND("Background"),
    BACKGROUND_BOTTOM("Background (bottom)"),
    KEY_LETTER("Letter keys"),
    TEXT("Letters"),
    KEY_FUNCTION("Function keys"),
    TEXT_FUNCTION("Function labels"),
    ACCENT("Enter key"),
    ON_ACCENT("Enter label"),
    KEY_PRESSED("Pressed key"),
    STRIP_TEXT("Suggestions"),
    HINT("Hints"),
    PREVIEW_BG("Key pop-up"),
    PREVIEW_TEXT("Pop-up letter"),
    KEY_BORDER("Outline");

    fun read(t: KeyboardTheme): Int = when (this) {
        BACKGROUND -> t.backgroundGradient?.first() ?: t.background
        BACKGROUND_BOTTOM -> t.backgroundGradient?.last() ?: t.background
        KEY_LETTER -> t.keyLetter
        TEXT -> t.text
        KEY_FUNCTION -> t.keyFunction
        TEXT_FUNCTION -> t.textFunction
        ACCENT -> t.accent
        ON_ACCENT -> t.onAccent
        KEY_PRESSED -> t.keyPressed
        STRIP_TEXT -> t.stripText
        HINT -> t.hint
        PREVIEW_BG -> t.previewBackground
        PREVIEW_TEXT -> t.previewText
        KEY_BORDER -> t.keyBorder
    }

    fun write(c: ThemeColors, hex: String, gradientOn: Boolean): ThemeColors = when (this) {
        BACKGROUND -> if (gradientOn) {
            c.copy(background = hex, backgroundGradient = listOf(hex, c.backgroundGradient?.lastOrNull() ?: hex))
        } else {
            c.copy(background = hex, backgroundGradient = null)
        }
        BACKGROUND_BOTTOM -> c.copy(backgroundGradient = listOf(c.backgroundGradient?.firstOrNull() ?: c.background ?: hex, hex))
        KEY_LETTER -> c.copy(keyLetter = hex)
        TEXT -> c.copy(text = hex)
        KEY_FUNCTION -> c.copy(keyFunction = hex)
        TEXT_FUNCTION -> c.copy(textFunction = hex)
        ACCENT -> c.copy(accent = hex)
        ON_ACCENT -> c.copy(onAccent = hex)
        KEY_PRESSED -> c.copy(keyPressed = hex)
        STRIP_TEXT -> c.copy(stripText = hex)
        HINT -> c.copy(hint = hex)
        PREVIEW_BG -> c.copy(previewBackground = hex)
        PREVIEW_TEXT -> c.copy(previewText = hex)
        KEY_BORDER -> c.copy(keyBorder = hex, keyBorderGradient = null)
    }
}

/**
 * Settings → Theme → Design your own (Stage 3.7). Edits a user theme (filesDir/themes/<id>).
 * Tap a part of the preview (again for its label) and the colour picker opens in a dialog; the
 * page itself stays short: colours, keys, and the theme's name / copy / delete. Every finished
 * change is saved and becomes the keyboard's theme, so the try bar shows it at once.
 */
@Composable
internal fun ThemeEditorScreen(id: String, onBack: () -> Unit, navigate: (String) -> Unit) =
    SettingsPage("Design your theme", onBack) {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        val store = remember { ThemeStore(context) }
        var spec by remember(id) { mutableStateOf(store.get(id)) }
        val history = remember(id) { mutableStateListOf<ThemeSpec>() }
        var night by remember { mutableStateOf(false) }
        var part by remember { mutableStateOf(Part.KEY_LETTER) }
        var dragStart by remember { mutableStateOf<ThemeSpec?>(null) }
        var confirmDelete by remember { mutableStateOf(false) }
        var picking by remember { mutableStateOf(false) }
        var renaming by remember { mutableStateOf(false) }
        var moreKeys by remember { mutableStateOf(false) }

        if (store.isBuiltIn(id) || spec.id != id) {
            Footnote("This theme can't be edited. Go back and use “Design your own”.")
            return@SettingsPage
        }

        fun save(s: ThemeSpec) {
            runCatching { store.save(s) }
            prefs.themeId = s.id
            prefs.themeRevision = prefs.themeRevision + 1
        }
        /** A finished change: remember the old version for Undo, save. */
        fun commit(s: ThemeSpec) {
            history.add(dragStart ?: spec)
            if (history.size > 60) history.removeAt(0)
            dragStart = null
            spec = s
            save(s)
        }
        /** While dragging a slider: show it, but save and record only when let go. */
        fun draft(s: ThemeSpec) {
            if (dragStart == null) dragStart = spec
            spec = s
        }

        val editingNight = night && spec.dark != null
        val variant = if (editingNight) spec.dark!! else spec.light
        fun withVariant(v: ThemeVariant): ThemeSpec = if (editingNight) spec.copy(dark = v) else spec.copy(light = v)
        /** Key style applies to day and night alike. */
        fun withKeys(k: KeyStyle): ThemeSpec = spec.copy(light = spec.light.copy(keys = k), dark = spec.dark?.copy(keys = k))

        val theme = remember(spec, editingNight) {
            runCatching { ThemeResolver.resolve(context, spec, editingNight, ThemeOverrides()) }
                .getOrElse { KeyboardTheme.light() }
        }
        val gradientOn = variant.colors.backgroundGradient != null

        // Preview: tap a part to colour it (tap a key again for its label).
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.9f)
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        part = hitPart(o.x / size.width, o.y / size.height, part)
                        picking = true
                    }
                },
        ) {
            KeyboardPreview(theme, Modifier.fillMaxWidth().aspectRatio(1.9f))
        }
        Footnote("Tap any part of the keyboard to colour it. Changes save as you go.")
        ContrastWarnings(theme)

        Group("Colours") {
            ChoiceRow("Part", Part.entries.filter { it != Part.BACKGROUND_BOTTOM || gradientOn }.map { it to it.title }, part) {
                part = it
            }
            RowDivider()
            ColourRow(part.title, part.read(theme)) { picking = true }
            RowDivider()
            SwitchRow("Gradient background", gradientOn) { on ->
                val c = variant.colors
                val top = c.background ?: hex(theme.background)
                val newColors = if (on) c.copy(backgroundGradient = listOf(top, hex(darker(theme.background)))) else c.copy(backgroundGradient = null)
                commit(withVariant(variant.copy(colors = newColors)))
            }
            RowDivider()
            SwitchRow("Night colours", spec.dark != null, status = "Its own colours in dark mode") { on ->
                commit(if (on) spec.copy(dark = spec.light) else spec.copy(dark = null))
                night = on
            }
            if (spec.dark != null) {
                RowDivider()
                ChoiceRow("Editing", listOf(false to "Day colours", true to "Night colours"), night) { night = it }
            }
        }

        val k = variant.keys
        Group("Keys") {
            ChoiceRow("Style", KeyLook.entries.map { it.id to it.title }, k.look) { commit(withKeys(k.copy(look = it))) }
            RowDivider()
            ChoiceRow("Shape", KeyShape.entries.map { it.id to it.title }, k.shape) { commit(withKeys(k.copy(shape = it))) }
            RowDivider()
            ChoiceRow("Font", Fonts.ALL.map { it.id to it.name }, k.font) { commit(withKeys(k.copy(font = it))) }
            RowDivider()
            SwitchRow("More key options", moreKeys, status = "Roundness, outline, glow, see-through, spacing") { moreKeys = it }
            if (moreKeys) {
                EditorSlider("Roundness", k.radius, 0f..30f, { "${it.toInt()}" }, { draft(withKeys(k.copy(radius = it))) }) {
                    commit(withKeys(k.copy(radius = it)))
                }
                EditorSlider("Outline width", k.borderWidth, 0f..4f, { "%.1f".format(it) }, { draft(withKeys(k.copy(borderWidth = it))) }) {
                    commit(withKeys(k.copy(borderWidth = it)))
                }
                EditorSlider("Glow", k.glow, 0f..12f, { "${it.toInt()}" }, { draft(withKeys(k.copy(glow = it))) }) {
                    commit(withKeys(k.copy(glow = it)))
                }
                EditorSlider("See-through", 1f - k.opacity, 0f..0.9f, { "${(it * 100).toInt()}%" }, { draft(withKeys(k.copy(opacity = 1f - it))) }) {
                    commit(withKeys(k.copy(opacity = 1f - it)))
                }
                EditorSlider("Spacing", k.gapH, 0f..12f, { "${it.toInt()}" }, { draft(withKeys(k.copy(gapH = it, gapV = it + 3f))) }) {
                    commit(withKeys(k.copy(gapH = it, gapV = it + 3f)))
                }
            }
        }

        Group("Theme") {
            NavRow("Undo", if (history.isEmpty()) "Nothing to undo" else "${history.size} change${if (history.size == 1) "" else "s"}", enabled = history.isNotEmpty()) {
                val prev = history.removeLastOrNull() ?: return@NavRow
                spec = prev
                save(prev)
            }
            RowDivider()
            NavRow("Name", spec.name) { renaming = true }
            RowDivider()
            NavRow("Duplicate", "Make a copy to try ideas") {
                val copy = spec.copy(id = newThemeId(), name = spec.name + " copy")
                save(copy)
                navigate(EDIT_PREFIX + copy.id)
            }
            RowDivider()
            NavRow("Delete", "Can't be undone") { confirmDelete = true }
        }
        Footnote("Your Keys & letters and Background picks still apply on top of every theme.")

        if (picking) {
            AlertDialog(
                onDismissRequest = { picking = false },
                title = { Text(part.title + if (editingNight) " (night)" else "") },
                text = {
                    androidx.compose.foundation.layout.Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ColorPicker(part.read(theme), key = "${part.name}/$editingNight/${history.size}") { color, final ->
                            val s = withVariant(variant.copy(colors = part.write(variant.colors, hex(color), gradientOn)))
                            if (final) commit(s) else draft(s)
                        }
                        if (part == Part.KEY_BORDER && variant.keys.borderWidth <= 0f) {
                            Text("Outlines show once Outline width (More key options) is above 0.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { picking = false }) { Text("Done") } },
            )
        }
        if (renaming) {
            var name by remember { mutableStateOf(spec.name) }
            AlertDialog(
                onDismissRequest = { renaming = false },
                title = { Text("Name") },
                text = {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) commit(spec.copy(name = name.trim()))
                        renaming = false
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete “${spec.name}”?") },
                text = { Text("This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        store.delete(spec.id)
                        if (prefs.themeId == spec.id) prefs.themeId = ThemeStore.CLASSIC
                        prefs.themeRevision = prefs.themeRevision + 1
                        confirmDelete = false
                        onBack()
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
            )
        }
    }

/** A row showing a colour swatch; tapping opens the picker. */
@Composable
private fun ColourRow(title: String, color: Int, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text("Colour", style = MaterialTheme.typography.bodyLarge)
            Text(title + " · " + hex(color), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFCCCCCC))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ) { Canvas(Modifier.size(36.dp)) { drawRect(Color(color)) } }
    }
}

/** A new user theme id (never a built-in's). */
internal fun newThemeId() = "my_" + System.currentTimeMillis()

/** Copies [base] into a new user theme, saves it and returns its id. */
internal fun makeOwnTheme(store: ThemeStore, base: ThemeSpec): String {
    val copy = base.copy(id = newThemeId(), name = base.name + " (mine)", author = "Me", category = "Mine")
    store.save(copy)
    return copy.id
}

/**
 * Which part a tap at ([fx], [fy]) (fractions of the preview) means, laid out like
 * KeyboardPreview: strip at the top 16%, then 4 rows of 10 units. Tapping the same key again
 * switches between the key and its label.
 */
private fun hitPart(fx: Float, fy: Float, current: Part): Part {
    if (fy < 0.16f) return Part.STRIP_TEXT
    val row = ((fy - 0.16f) / 0.84f * 4f).toInt().coerceIn(0, 3)
    val col = fx * 10f
    val key = when {
        row == 1 && (col < 0.5f || col > 9.5f) -> Part.BACKGROUND
        row >= 2 && col < 1.5f -> Part.KEY_FUNCTION
        row == 2 && col > 8.5f -> Part.KEY_FUNCTION
        row == 3 && col > 8.5f -> Part.ACCENT
        else -> Part.KEY_LETTER
    }
    val label = when (key) {
        Part.KEY_LETTER -> Part.TEXT
        Part.KEY_FUNCTION -> Part.TEXT_FUNCTION
        Part.ACCENT -> Part.ON_ACCENT
        else -> key
    }
    return if (current == key) label else key
}

private fun hex(c: Int): String = "#%08X".format(c)

private fun darker(c: Int): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c, hsv)
    hsv[2] = (hsv[2] * 0.6f).coerceIn(0f, 1f)
    return android.graphics.Color.HSVToColor(android.graphics.Color.alpha(c), hsv)
}

private fun over(fg: Int, bg: Int): Int {
    val a = android.graphics.Color.alpha(fg) / 255f
    fun ch(f: Int, b: Int) = (f * a + b * (1 - a)).toInt()
    return android.graphics.Color.rgb(
        ch(android.graphics.Color.red(fg), android.graphics.Color.red(bg)),
        ch(android.graphics.Color.green(fg), android.graphics.Color.green(bg)),
        ch(android.graphics.Color.blue(fg), android.graphics.Color.blue(bg)),
    )
}

/** Lists text that's hard to read (below 4.5:1, the WCAG level the built-in themes meet). */
@Composable
private fun ContrastWarnings(t: KeyboardTheme) {
    val bg = t.backgroundGradient?.first() ?: t.background
    val checks = listOf(
        Triple("Letters on letter keys", t.text, over(t.keyLetter, bg)),
        Triple("Function labels", t.textFunction, over(t.keyFunction, bg)),
        Triple("Enter label", t.onAccent, over(t.accent, bg)),
        Triple("Suggestions", t.stripText, bg),
        Triple("Pop-up letter", t.previewText, over(t.previewBackground, bg)),
    )
    for ((what, fg, back) in checks) {
        val r = ThemeResolver.contrast(over(fg, back), back)
        if (r < 4.5f) {
            Text(
                "⚠ $what: ${"%.1f".format(r)}:1, hard to read (aim for 4.5 or more)",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EditorChips(choices: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((id, label) in choices) {
            FilterChip(selected = current == id, onClick = { onPick(id) }, label = { Text(label) })
        }
    }
}

@Composable
private fun EditorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onDrag: (Float) -> Unit,
    onDone: (Float) -> Unit,
) {
    var v by remember(value) { mutableStateOf(value) }
    SliderRow(label, v, format(v), range, onChange = { v = it; onDrag(it) }, onDone = { onDone(v) })
}

private val QUICK = listOf(
    "#FFFFFFFF", "#FF000000", "#FF202124", "#FFE8EAED", "#FFE4002B", "#FF41B3E5", "#FF0D2B55",
    "#FFFFD166", "#FF3DDC84", "#FF9B5DE5", "#FFFF6A88", "#FFFF9F1C", "#80FFFFFF", "#40000000",
)

/**
 * HSV + transparency sliders, a hex field and quick colours. [onChange] gets every drag step
 * with final = false, and the finished colour with final = true. [key] resets the sliders when
 * the part (or an undo) changes the colour from outside.
 */
@Composable
private fun ColorPicker(color: Int, key: String, onChange: (Int, Boolean) -> Unit) {
    val start = remember(key) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        floatArrayOf(hsv[0], hsv[1], hsv[2], android.graphics.Color.alpha(color) / 255f)
    }
    var h by remember(key) { mutableStateOf(start[0]) }
    var s by remember(key) { mutableStateOf(start[1]) }
    var v by remember(key) { mutableStateOf(start[2]) }
    var a by remember(key) { mutableStateOf(start[3]) }
    var hexText by remember(key) { mutableStateOf(hex(color).removePrefix("#")) }
    fun current(): Int = android.graphics.Color.HSVToColor((a * 255).toInt(), floatArrayOf(h, s, v))
    fun push(final: Boolean) {
        val c = current()
        hexText = hex(c).removePrefix("#")
        onChange(c, final)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFCCCCCC))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        ) {
            Canvas(Modifier.size(56.dp)) { drawRect(Color(current())) }
        }
        OutlinedTextField(
            value = hexText,
            onValueChange = { t ->
                val clean = t.filter { it.isLetterOrDigit() }.take(8).uppercase()
                hexText = clean
                if (clean.length == 6 || clean.length == 8) {
                    runCatching { android.graphics.Color.parseColor("#$clean") }.getOrNull()?.let { c ->
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(c, hsv)
                        h = hsv[0]; s = hsv[1]; v = hsv[2]; a = android.graphics.Color.alpha(c) / 255f
                        onChange(c, true)
                    }
                }
            },
            label = { Text("Hex (RRGGBB or AARRGGBB)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // Rainbow strip above the hue slider so it's clear what it does.
    Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        drawRect(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
    }
    Text("Colour")
    Slider(value = h, onValueChange = { h = it; push(false) }, onValueChangeFinished = { push(true) }, valueRange = 0f..360f)
    Text("Richness: ${(s * 100).toInt()}%")
    Slider(value = s, onValueChange = { s = it; push(false) }, onValueChangeFinished = { push(true) })
    Text("Brightness: ${(v * 100).toInt()}%")
    Slider(value = v, onValueChange = { v = it; push(false) }, onValueChangeFinished = { push(true) })
    Text("See-through: ${((1f - a) * 100).toInt()}%")
    Slider(value = 1f - a, onValueChange = { a = 1f - it; push(false) }, onValueChangeFinished = { push(true) })
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (q in QUICK) {
            val c = android.graphics.Color.parseColor(q)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCCCCCC))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable {
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(c, hsv)
                        h = hsv[0]; s = hsv[1]; v = hsv[2]; a = android.graphics.Color.alpha(c) / 255f
                        push(true)
                    },
            ) { Canvas(Modifier.size(32.dp)) { drawRect(Color(c)) } }
        }
    }
}
