package com.bori.keyboard.settings

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bori.keyboard.theme.BackdropRenderer
import com.bori.keyboard.theme.Fonts
import com.bori.keyboard.theme.KeyLook
import com.bori.keyboard.theme.KeyPainter
import com.bori.keyboard.theme.KeyShape
import com.bori.keyboard.theme.KeyboardTheme
import com.bori.keyboard.theme.LabelCase
import com.bori.keyboard.theme.ThemeMode
import com.bori.keyboard.theme.ThemeOverrides
import com.bori.keyboard.theme.ThemeResolver
import com.bori.keyboard.theme.ThemeSpec
import com.bori.keyboard.theme.ThemeStore

/**
 * Settings → Theme: a short hub (Android settings guide: status on the second line, details one
 * level down). Big live preview, the theme (opens the gallery), dark mode, key borders, then one
 * row per customisation page. Every choice is a pref the IME listens to, so the keyboard in the
 * try bar re-themes at once.
 */
@Composable
internal fun ThemesScreen(onBack: () -> Unit, navigate: (String) -> Unit) = SettingsPage("Theme", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val store = remember { ThemeStore(context) }
    val selected = prefs.themeId
    val spec = remember(selected) { store.get(selected) }
    var mode by remember { mutableStateOf(prefs.themeMode) }
    var borders by remember { mutableStateOf(prefs.keyBorders) }
    val night = KeyboardTheme.wantsDark(context, mode)
    val overrides = remember(borders) { prefs.themeOverrides() }
    val preview = remember(spec, night, overrides) { resolveSafe(context, spec, night, overrides) }

    KeyboardPreview(preview, Modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(16.dp)))

    Group(null) {
        NavRow("Gallery", spec.name, SettingsIcons.Palette) { navigate(SCREEN_THEME_GALLERY) }
        RowDivider()
        ChoiceRow("Dark mode", THEME_MODES, mode) { mode = it; prefs.themeMode = it }
        RowDivider()
        SwitchRow("Key borders", borders, status = if (borders) "Shapes behind the letters" else "Letters only") {
            borders = it; prefs.keyBorders = it
        }
    }
    Group("Customize") {
        NavRow("Keys & letters", keysStatus(prefs)) { navigate(SCREEN_THEME_KEYS) }
        RowDivider()
        NavRow("Background", backgroundStatus(prefs.backgroundOverride)) { navigate(SCREEN_THEME_BACKGROUND) }
        RowDivider()
        NavRow("Effects & lighting", effectsStatus(prefs)) { navigate(SCREEN_THEME_EFFECTS) }
        RowDivider()
        if (store.isBuiltIn(selected)) {
            NavRow("Design your own", "Start from ${spec.name}") {
                val id = runCatching { makeOwnTheme(store, spec) }.getOrNull() ?: return@NavRow
                prefs.themeId = id
                navigate(EDIT_PREFIX + id)
            }
        } else {
            NavRow("Edit ${spec.name}", "Your theme") { navigate(EDIT_PREFIX + selected) }
        }
    }
    Footnote("Your Keys & letters, Background and Effects choices apply on top of any theme.")
}

internal const val SCREEN_THEME_GALLERY = "theme_gallery"
internal const val SCREEN_THEME_KEYS = "theme_keys"
internal const val SCREEN_THEME_BACKGROUND = "theme_background"
internal const val SCREEN_THEME_EFFECTS = "theme_effects"

private val THEME_MODES = listOf(
    ThemeMode.SYSTEM to "Follow system",
    ThemeMode.LIGHT to "Always light",
    ThemeMode.DARK to "Always dark",
    ThemeMode.NIGHT to "Dark at night (7 pm – 7 am)",
)

internal fun themeModeLabel(m: ThemeMode) = THEME_MODES.firstOrNull { it.first == m }?.second ?: ""

private fun keysStatus(prefs: Prefs): String {
    val parts = listOfNotNull(
        KeyLook.entries.firstOrNull { it.id == prefs.keyLook }?.title,
        KeyShape.entries.firstOrNull { it.id == prefs.keyShape }?.title,
        Fonts.ALL.firstOrNull { it.id == prefs.keyFont }?.name,
        LabelCase.entries.firstOrNull { it.id == prefs.labelCase }?.title,
        if (prefs.labelScale != 1f) "Letters ${(prefs.labelScale * 100).toInt()}%" else null,
        if (prefs.keyOpacity < 1f) "See-through ${((1f - prefs.keyOpacity) * 100).toInt()}%" else null,
    )
    return if (parts.isEmpty()) "The theme's own style" else parts.joinToString(" · ")
}

internal fun backgroundStatus(b: com.bori.keyboard.theme.BackgroundSpec?): String = when (b?.type) {
    null -> "The theme's own"
    "none" -> "Plain colour"
    "gradient" -> "Gradient"
    "scene" -> "Scene: " + (com.bori.keyboard.theme.Backdrop.SCENES.firstOrNull { it.first == b.scene }?.second ?: "")
    "photo" -> "Your photo"
    else -> ""
}

private fun effectsStatus(prefs: Prefs): String {
    val press = com.bori.keyboard.view.EffectsView.Effect.of(prefs.pressEffect)
    val led = com.bori.keyboard.view.LedLights.Mode.of(prefs.ledMode)
    val parts = listOfNotNull(
        press.title.takeIf { press != com.bori.keyboard.view.EffectsView.Effect.NONE },
        led.title.takeIf { led != com.bori.keyboard.view.LedLights.Mode.OFF },
    )
    return if (parts.isEmpty()) "Off" else parts.joinToString(" · ")
}

/** The theme as the keyboard shows it now (theme + the user's overrides), for page previews. */
@Composable
internal fun currentPreviewTheme(prefs: Prefs, version: Any?): KeyboardTheme {
    val context = LocalContext.current
    return remember(version) {
        val spec = ThemeStore(context).get(prefs.themeId)
        resolveSafe(context, spec, KeyboardTheme.wantsDark(context, prefs.themeMode), prefs.themeOverrides())
    }
}

/** Theme → Gallery: built-in themes by section, and the user's own. */
@Composable
internal fun ThemeGalleryScreen(onBack: () -> Unit) = SettingsPage("Gallery", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val store = remember { ThemeStore(context) }
    var selected by remember { mutableStateOf(prefs.themeId) }
    var tab by remember { mutableIntStateOf(if (store.isBuiltIn(prefs.themeId)) 0 else 1) }
    val night = KeyboardTheme.wantsDark(context, prefs.themeMode)
    val overrides = remember { prefs.themeOverrides() }
    val pick = { id: String -> selected = id; prefs.themeId = id }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Gallery") })
        FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Mine") })
    }
    if (tab == 0) {
        val themes = remember { store.builtIns() }
        // Sections in the order their first theme appears (the files are numbered).
        for ((category, list) in themes.groupBy { it.category }) {
            SectionTitle(category)
            ThemeGrid(list, selected, night, overrides, pick)
        }
    } else {
        val mine = remember { store.userThemes() }
        if (mine.isEmpty()) {
            Footnote("Themes you design show up here. Go back and tap “Design your own”.")
        } else {
            ThemeGrid(mine, selected, night, overrides, pick)
        }
    }
}

/** Theme → Keys & letters: look, shape, font, case, size, see-through, hints, on any theme. */
@Composable
internal fun KeysLettersScreen(onBack: () -> Unit) = SettingsPage("Keys & letters", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var version by remember { mutableIntStateOf(0) }
    val bump = { version++ }
    val theme = currentPreviewTheme(prefs, version)
    var size by remember { mutableFloatStateOf(prefs.labelScale) }
    var opacity by remember { mutableFloatStateOf(prefs.keyOpacity) }
    var hints by remember { mutableStateOf(prefs.keyHints) }

    KeyboardPreview(theme, Modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(16.dp)))

    Group("Keys") {
        ChoiceRow("Style", listOf("" to "Theme's") + KeyLook.entries.map { it.id to it.title }, prefs.keyLook) {
            prefs.keyLook = it; bump()
        }
        RowDivider()
        ChoiceRow("Shape", listOf("" to "Theme's") + KeyShape.entries.map { it.id to it.title }, prefs.keyShape) {
            prefs.keyShape = it; bump()
        }
        RowDivider()
        SliderRow("See-through", 1f - opacity, "${((1f - opacity) * 100).toInt()}%", 0f..0.8f, steps = 15,
            onChange = { opacity = 1f - it }, onDone = { prefs.keyOpacity = opacity; bump() })
    }
    Group("Letters") {
        ChoiceRow("Font", listOf("" to "Theme's") + Fonts.ALL.map { it.id to it.name }, prefs.keyFont) {
            prefs.keyFont = it; bump()
        }
        RowDivider()
        ChoiceRow("Capitals", listOf("" to "Theme's") + LabelCase.entries.map { it.id to it.title }, prefs.labelCase) {
            prefs.labelCase = it; bump()
        }
        RowDivider()
        SliderRow("Size", size, "${(size * 100).toInt()}%", 0.7f..1.4f, steps = 13,
            onChange = { size = it }, onDone = { prefs.labelScale = size; bump() })
        RowDivider()
        SwitchRow("Hints", hints, status = "Small numbers and symbols in the corners") { hints = it; prefs.keyHints = it; bump() }
    }
    Group(null) {
        NavRow("Use the theme's style", "Undo all of the above") {
            prefs.keyLook = ""
            prefs.keyShape = ""
            prefs.keyFont = ""
            prefs.labelCase = ""
            prefs.labelScale = 1f
            prefs.keyOpacity = 1f
            prefs.keyHints = true
            size = 1f
            opacity = 1f
            hints = true
            bump()
        }
    }
}

private fun resolveSafe(
    context: android.content.Context,
    spec: ThemeSpec,
    night: Boolean,
    o: ThemeOverrides,
): KeyboardTheme = runCatching { ThemeResolver.resolve(context, spec, night, o) }
    .getOrElse { if (night) KeyboardTheme.dark() else KeyboardTheme.light() }

@Composable
private fun ThemeGrid(
    themes: List<ThemeSpec>,
    selected: String,
    night: Boolean,
    o: ThemeOverrides,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in themes.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (spec in row) {
                    ThemeCard(spec, spec.id == selected, night, o, Modifier.weight(1f)) { onPick(spec.id) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeCard(
    spec: ThemeSpec,
    isSelected: Boolean,
    night: Boolean,
    o: ThemeOverrides,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val theme = remember(spec, night, o) { resolveSafe(context, spec, night, o) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .border(
                if (isSelected) 3.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onClick),
    ) {
        KeyboardPreview(theme, Modifier.fillMaxWidth().aspectRatio(1.45f))
        Text(
            (if (isSelected) "✓ " else "") + spec.name,
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private val PREVIEW_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

/**
 * A small drawing of the keyboard (strip + 4 rows), drawn with the keyboard's own KeyPainter
 * on the native canvas, so looks, shapes, outlines and fonts match the real thing.
 */
@Composable
internal fun KeyboardPreview(theme: KeyboardTheme, modifier: Modifier) {
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val painter = remember { KeyPainter() }
    val rect = remember { RectF() }
    val context = LocalContext.current
    Canvas(modifier) {
        val grad = theme.backgroundGradient
        val backdrop = theme.backdrop
        val bmp = backdrop?.let { BackdropRenderer.get(context, it, size.width.toInt(), size.height.toInt()) }
        if (bmp != null) {
            drawContext.canvas.nativeCanvas.drawBitmap(bmp, 0f, 0f, null)
        } else if (grad != null) {
            drawRect(Brush.verticalGradient(grad.map { Color(it) }))
        } else {
            drawRect(Color(theme.background))
        }
        val canvas = drawContext.canvas.nativeCanvas
        // Real keyboards are ~360 dp wide; scale dp sizes down to the card.
        val scale = size.width / 360.dp.toPx()
        val pxPerDp = density * scale
        painter.prepare(theme, size.width, pxPerDp)
        val gapH = theme.keyGapHDp * pxPerDp
        val gapV = theme.keyGapVDp * pxPerDp
        val stripH = size.height * 0.16f
        val pad = 2f * pxPerDp
        val rowH = (size.height - stripH - pad * 2) / 4f
        val unit = (size.width - pad * 2) / 10f
        val face = theme.typeface ?: Typeface.DEFAULT
        val bold = if (theme.typeface == null) Typeface.DEFAULT_BOLD else Typeface.create(theme.typeface, Typeface.BOLD)

        // Suggestion strip (always the phone's font, like the keyboard).
        textPaint.color = theme.stripText
        textPaint.textSize = stripH * 0.5f
        textPaint.typeface = Typeface.DEFAULT
        for ((i, w) in listOf("the", "I", "you").withIndex()) {
            canvas.drawText(w, size.width * (0.2f + 0.3f * i), stripH * 0.68f, textPaint)
        }

        val upper = theme.labelCase == LabelCase.UPPER
        fun key(col: Float, row: Int, widthUnits: Float, label: String, kind: Int) {
            // kind: 0 letter, 1 function, 2 accent
            val left = pad + col * unit + gapH / 2
            val top = stripH + pad + row * rowH + gapV / 2
            rect.set(left, top, left + widthUnits * unit - gapH, top + rowH - gapV)
            if (rect.width() <= 0f || rect.height() <= 0f) return
            val fill = when (kind) {
                2 -> theme.accent
                1 -> theme.keyFunction
                else -> theme.keyLetter
            }
            val f = painter.draw(canvas, rect, fill, pressed = false, accent = kind == 2)
            if (label.isEmpty()) return
            textPaint.color = when (kind) {
                2 -> theme.onAccent
                1 -> theme.textFunction
                else -> theme.text
            }
            val h = f.height()
            textPaint.textSize = h * theme.labelScale * if (label.length > 1) 0.36f else 0.55f
            textPaint.typeface = if (label.length > 1) bold else face
            val text = if (upper && kind == 0) label.uppercase() else label
            val y = f.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(text, f.centerX(), y, textPaint)
        }

        PREVIEW_ROWS[0].forEachIndexed { i, c -> key(i.toFloat(), 0, 1f, c.toString(), 0) }
        PREVIEW_ROWS[1].forEachIndexed { i, c -> key(0.5f + i, 1, 1f, c.toString(), 0) }
        key(0f, 2, 1.5f, "⇧", 1)
        PREVIEW_ROWS[2].forEachIndexed { i, c -> key(1.5f + i, 2, 1f, c.toString(), 0) }
        key(8.5f, 2, 1.5f, "⌫", 1)
        key(0f, 3, 1.5f, "?123", 1)
        key(1.5f, 3, 7f, "", 0)
        key(8.5f, 3, 1.5f, "↵", 2)
    }
}
