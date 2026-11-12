package com.bori.keyboard.settings

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bori.keyboard.layout.FieldVariant
import com.bori.keyboard.layout.KeyGeometry
import com.bori.keyboard.layout.Language
import com.bori.keyboard.layout.LayoutId
import com.bori.keyboard.layout.Layouts
import com.bori.keyboard.learn.TouchMap
import com.bori.keyboard.predict.KeyProximity
import com.bori.keyboard.theme.KeyboardTheme
import com.bori.keyboard.view.BigScreen
import com.bori.keyboard.view.KeySize
import com.bori.keyboard.view.Posture
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.util.Locale
import kotlin.math.hypot

/**
 * "Fit to my thumbs": full-screen calibration for the way the phone is held now.
 * 1. Rest your thumbs where they feel natural → keyboard height for this posture (the middle
 *    letter row goes under the thumbs) and, split on a big screen, the edges and the gap.
 * 2. Type a sentence on a practice keyboard drawn with the real key geometry → each tap goes
 *    into the touch heat map for the key it was meant for.
 * 3. Fold, unfold or turn the phone and do it again (the activity is recreated on a fold and
 *    comes back to this page, see SettingsActivity.STATE_SCREEN).
 */
@Composable
internal fun CalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val config = LocalConfiguration.current
    val posture = Posture.of(config)
    val density = LocalDensity.current.density
    val navBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    val map = remember { TouchMap.get(context) }

    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }
    var step by remember(posture) { mutableIntStateOf(STEP_THUMBS) }
    var done by remember { mutableStateOf(prefs.calibrated) }

    // Step 1: thumbs held still for HOLD_MS are taken as the resting position.
    var touching by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var anchor by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var anchorTick by remember { mutableIntStateOf(0) }
    var result by remember(posture) { mutableStateOf("") }
    var heightScale by remember(posture) { mutableFloatStateOf(prefs.keyHeightScale(posture)) }

    val big = config.smallestScreenWidthDp >= BigScreen.MIN_DP
    val splitOn = prefs.splitOnBigScreens && big && config.screenWidthDp >= BigScreen.MIN_DP

    fun capture(pts: List<Offset>) {
        if (boxH <= 0 || pts.isEmpty()) return
        val bottom = boxH - navBottom - 4f * density
        val thumbY = pts.map { it.y }.average().toFloat()
        // Resting thumbs sit higher than where they type: take them as the line between the
        // top and middle letter rows, so 3 rows lie below (middle, bottom letters, space row).
        // (2.5, "thumbs on the middle row", came out far too tall on the phone.)
        val rowDp = ((bottom - thumbY) / THUMB_ROWS_BELOW) / density
        val screenH = config.screenHeightDp
        val base = KeySize.baseDp(screenH)
        val scale = (rowDp.coerceAtMost(KeySize.maxDp(screenH)) / base).coerceIn(Prefs.HEIGHT_MIN, CAL_MAX_SCALE)
        prefs.setKeyHeightScale(posture, scale)
        heightScale = scale
        var msg = "Key height set to ${(scale * 100).toInt()}%."
        if (splitOn && pts.size >= 2) {
            val w = boxW.toFloat()
            val left = pts.minByOrNull { it.x }!!.x
            val right = pts.maxByOrNull { it.x }!!.x
            if (left < w / 2 && right > w / 2) {
                // Each half is centred under its thumb: edge/2 + (W - gap)/4 = thumb distance from its side.
                val reach = (left + (w - right)) / 2f
                val edge = ((2f * reach - (w - prefs.splitGap * w) / 2f) / w).coerceIn(0f, 0.2f)
                val gap = ((w - 4f * reach + 2f * edge * w) / w).coerceIn(0.1f, 0.5f)
                prefs.splitEdge = edge
                prefs.splitGap = gap
                msg += " Split halves moved under your thumbs."
            }
        }
        result = msg
        step = STEP_TYPE
    }

    LaunchedEffect(anchorTick) {
        if (step != STEP_THUMBS || anchor.isEmpty()) return@LaunchedEffect
        delay(HOLD_MS)
        if (touching.isNotEmpty()) capture(anchor)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxW = it.width; boxH = it.height },
    ) {
        // Touch areas first, so the buttons and text drawn after them stay tappable.
        when (step) {
            STEP_THUMBS -> Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(posture) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val pts = ev.changes.filter { it.pressed }.map { it.position }
                                ev.changes.forEach { it.consume() }
                                touching = pts
                                val moved = pts.size != anchor.size ||
                                    pts.zip(anchor).any { (a, b) -> hypot(a.x - b.x, a.y - b.y) > STILL_DP * density }
                                if (pts.isEmpty()) {
                                    anchor = emptyList()
                                    anchorTick++
                                } else if (moved) {
                                    anchor = pts
                                    anchorTick++
                                }
                            }
                        }
                    },
            ) {
                for (pt in touching) {
                    drawCircle(Color(0x553D8BFF), 36.dp.toPx(), pt)
                    drawCircle(Color(0xFF3D8BFF), 36.dp.toPx(), pt, style = Stroke(3.dp.toPx()))
                }
            }
            STEP_TYPE -> PracticeKeyboard(
                prefs = prefs,
                posture = posture,
                width = boxW,
                heightScale = heightScale,
                splitOn = splitOn,
                navBottomPx = navBottom,
                map = map,
                onDone = {
                    map.save()
                    done = done + posture.id
                    prefs.calibrated = done
                    prefs.calibrationAsked = true
                    step = STEP_DONE
                },
            )
        }
        Column(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Fit to my thumbs", style = MaterialTheme.typography.headlineSmall)
            Text("Now: ${posture.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            when (step) {
                STEP_THUMBS -> {
                    Text("Hold the phone the way you type. Rest both thumbs on the lower part of the screen where they feel natural, and keep them still for a second.")
                    TextButton(onClick = { step = STEP_TYPE }) { Text("Skip, keep my height") }
                }
                STEP_TYPE -> {
                    if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.bodySmall)
                    Text("Too tall or too short? Drag to adjust; the keys below follow.", style = MaterialTheme.typography.bodySmall)
                    Text("Key height: ${(heightScale * 100).toInt()}%")
                    Slider(
                        value = heightScale,
                        onValueChange = {
                            heightScale = it
                            prefs.setKeyHeightScale(posture, it)
                        },
                        valueRange = Prefs.HEIGHT_MIN..Prefs.HEIGHT_MAX,
                    )
                    Text("Now type this on the keys below, the way you normally would. Don't fix mistakes; they're what Bori learns from.")
                }
                else -> {
                    Text("Done for this way of holding the phone ✓", style = MaterialTheme.typography.titleMedium)
                    Text("Fold, unfold or turn the phone to fit that way too. This page stays open.")
                    for (p in Posture.entries) {
                        Text((if (p.id in done) "✓ " else "○ ") + p.label, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { step = STEP_THUMBS; result = "" }) { Text("Redo") }
                        Button(onClick = onBack) { Text("Finish") }
                    }
                }
            }
        }

    }
}


@Composable
private fun androidx.compose.foundation.layout.BoxScope.PracticeKeyboard(
    prefs: Prefs,
    posture: Posture,
    width: Int,
    heightScale: Float,
    splitOn: Boolean,
    navBottomPx: Int,
    map: TouchMap,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    if (width <= 0) return
    val lang = Language.fromTag(Locale.getDefault().language)
    val sentence = if (lang == Language.ES) SENTENCE_ES else SENTENCE_EN

    // The real keyboard's geometry at the height just chosen (KeyboardView.onMeasure/rebuildGeometry).
    val geometry = remember(posture, width, heightScale) {
        val numberRow = prefs.numberRow
        val layout = Layouts.build(LayoutId.ALPHA, lang, FieldVariant.TEXT, false, numberRow)
        val rows = maxOf(if (numberRow) 5 else 4, layout.rows.size)
        val screenH = config.screenHeightDp
        val rowDp = KeySize.rowDp(screenH, heightScale)
        val h = (rows * rowDp * density + 8f * density).toInt()
        val theme = KeyboardTheme.forContext(context, themeId = prefs.themeId, overrides = prefs.themeOverrides())
        KeyGeometry(
            layout, width, h,
            padH = 2f * density + if (splitOn) width * prefs.splitEdge else 0f,
            padV = 4f * density,
            gapH = theme.keyGapHDp * density, gapV = theme.keyGapVDp * density,
            splitGap = if (splitOn) width * prefs.splitGap else 0f,
        )
    }
    val prox = remember(geometry) { KeyProximity(geometry) }
    var index by remember(posture) { mutableIntStateOf(0) }
    val taps = remember(posture) { mutableStateListOf<Offset>() }

    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = (navBottomPx / density).dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(sentence.substring(0, index)) }
                if (index < sentence.length) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.primaryContainer)) {
                        append(sentence[index])
                    }
                    append(sentence.substring(index + 1))
                }
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        val keyColor = MaterialTheme.colorScheme.surfaceVariant
        val outline = MaterialTheme.colorScheme.outline
        val label = MaterialTheme.colorScheme.onSurface
        val dot = MaterialTheme.colorScheme.primary
        val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height((geometry.height / density).dp)
                .pointerInput(geometry) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            for (ch in ev.changes) {
                                if (!ch.pressed || ch.previousPressed) continue
                                ch.consume()
                                if (index >= sentence.length) continue
                                val want = sentence[index]
                                if (want != ' ') {
                                    val k = keyFor(want, prox)
                                    if (k != null) {
                                        val dx = (ch.position.x - prox.centerX(k)) / prox.keyWidth
                                        val dy = (ch.position.y - prox.centerY(k)) / prox.keyHeight
                                        map.recordPoint(posture, k, dx, dy)
                                    }
                                    taps.add(ch.position)
                                    if (taps.size > 60) taps.removeAt(0)
                                }
                                index++
                                if (index >= sentence.length) onDone()
                            }
                        }
                    }
                },
        ) {
            textPaint.color = label.toArgb()
            textPaint.textSize = geometry.rowHeight * 0.38f
            for (pk in geometry.keys) {
                val v = pk.visual
                val r = CornerRadius(6.dp.toPx())
                drawRoundRect(keyColor, Offset(v.left, v.top), Size(v.width(), v.height()), r)
                drawRoundRect(outline, Offset(v.left, v.top), Size(v.width(), v.height()), r, style = Stroke(1.dp.toPx()))
                val text = pk.key.label
                if (text.length <= 6) {
                    drawContext.canvas.nativeCanvas.drawText(text, v.centerX(), v.centerY() + textPaint.textSize * 0.35f, textPaint)
                }
            }
            for (t in taps) drawCircle(dot.copy(alpha = 0.5f), 5.dp.toPx(), t)
        }
    }
}

/** The key a letter is typed on: itself, or its base letter (á → a). */
private fun keyFor(ch: Char, prox: KeyProximity): Char? {
    val c = ch.lowercaseChar()
    if (prox.hasKey(c)) return c
    val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: return null
    return base.takeIf { prox.hasKey(it) }
}

private const val STEP_THUMBS = 0
private const val STEP_TYPE = 1
private const val STEP_DONE = 2
private const val HOLD_MS = 800L
/** Key rows below the resting thumbs (see capture). */
private const val THUMB_ROWS_BELOW = 3f
/** Calibration never picks more than this; the slider can still go to Prefs.HEIGHT_MAX. */
private const val CAL_MAX_SCALE = 1.3f
private const val STILL_DP = 12f
private const val SENTENCE_EN = "the quick brown fox jumps over the lazy dog"
private const val SENTENCE_ES = "el veloz murcielago hindu comia feliz cardillo y kiwi"
