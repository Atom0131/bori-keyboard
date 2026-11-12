package com.bori.keyboard.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bori.keyboard.learn.TouchMap
import com.bori.keyboard.view.Posture
import kotlinx.coroutines.delay

/**
 * Settings → Corrections → Touch heat map: where the user's taps land on each key, per way of
 * holding the phone. Dots inside a key are on target; orange dots landed on a neighbour.
 */
@Composable
internal fun TouchMapScreen(onBack: () -> Unit, navigate: (String) -> Unit) = SettingsPage("Touch heat map", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val map = remember { TouchMap.get(context) }
    val now = Posture.of(LocalConfiguration.current)
    var posture by remember(now) { mutableStateOf(now) }
    var learn by remember { mutableStateOf(prefs.touchMap) }
    var aim by remember { mutableStateOf(prefs.aimKeys) }
    // The map changes while typing in the try bar; redraw when it does.
    var version by remember { mutableIntStateOf(map.version) }
    LaunchedEffect(map) {
        while (true) {
            delay(700)
            if (map.version != version) version = map.version
        }
    }
    var copied by remember { mutableStateOf(false) }

    Group(null) {
        SwitchRow(
            "Learn from where I tap",
            learn,
            info = "Bori notes where your thumbs land on each key, separately for folded and unfolded, upright " +
                "and sideways. After about ${TouchMap.MIN_TAPS} taps on a key, corrections aim where you really " +
                "press. Only words you keep or pick count; never in incognito or password fields. It stays on this phone.",
        ) { learn = it; prefs.touchMap = it }
        RowDivider()
        SwitchRow(
            "Move keys to fit my aim",
            aim,
            enabled = learn,
            status = "Border taps go to the key you usually mean",
        ) { aim = it; prefs.aimKeys = it }
        RowDivider()
        NavRow("Fit to my thumbs", "Set height and aim from a short test") { navigate(SettingsActivity.SCREEN_CALIBRATE) }
    }

    // Read version so the picture redraws when new taps come in.
    val keys = remember(posture, version) { map.keys(posture).toMap() }
    Group("Your taps") {
        ChoiceRow(
            "Showing",
            Posture.entries.map { it to it.label + (if (it == now) " (now)" else "") + " · " + map.count(it) + " taps" },
            posture,
        ) { posture = it }
        RowContent {
            HeatMap(keys)
            Text(
                "Each dot is a tap. Orange dots landed outside the key; the ring is your average aim.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Group(null) {
        NavRow("Copy data", if (copied) "Copied · paste it into your chat with Claude" else "To share for tuning", enabled = map.total() > 0) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Bori touch map", map.toJson().toString()))
            copied = true
        }
        RowDivider()
        NavRow("Clear this one", posture.label, enabled = keys.isNotEmpty()) { map.clear(posture); version = map.version }
        RowDivider()
        NavRow("Clear all", "Every way of holding", enabled = map.total() > 0) { map.clear(null); version = map.version }
    }
}

private val ROWS = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")
private val ROW_INDENT = floatArrayOf(0f, 0.5f, 1.5f)

@Composable
internal fun HeatMap(keys: Map<Char, TouchMap.KeyStats>) {
    val outline = MaterialTheme.colorScheme.outline
    val label = MaterialTheme.colorScheme.onSurface
    val keyFill = MaterialTheme.colorScheme.surfaceVariant
    val hit = Color(0xFFE53935)
    val miss = Color(0xFFFFA000)
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    // ñ only when it has its own key (Spanish layout) and has data.
    val rows = ROWS.map { r -> r.filter { it != 'ñ' || keys.containsKey('ñ') } }
    Canvas(Modifier.fillMaxWidth().aspectRatio(10f / (3 * 1.3f))) {
        val kw = size.width / 10f
        val kh = size.height / 3f
        val pad = kw * 0.06f
        textPaint.color = label.toArgb()
        textPaint.textSize = kh * 0.32f
        val dotR = kw * 0.07f
        for ((ri, row) in rows.withIndex()) {
            val indent = if (ri == 1 && row.length == 10) 0f else ROW_INDENT[ri]
            for ((ci, c) in row.withIndex()) {
                val left = (indent + ci) * kw
                val top = ri * kh
                val cx = left + kw / 2f
                val cy = top + kh / 2f
                drawRoundRect(keyFill, Offset(left + pad, top + pad), Size(kw - 2 * pad, kh - 2 * pad), CornerRadius(pad * 2))
                drawRoundRect(outline, Offset(left + pad, top + pad), Size(kw - 2 * pad, kh - 2 * pad), CornerRadius(pad * 2), style = Stroke(1.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(c.toString(), cx, cy + textPaint.textSize * 0.35f, textPaint)
                val s = keys[c] ?: continue
                val pts = s.recent
                var i = 0
                while (i + 1 < pts.size) {
                    val dx = pts[i]
                    val dy = pts[i + 1]
                    val out = kotlin.math.abs(dx) > 0.5f || kotlin.math.abs(dy) > 0.5f
                    drawCircle((if (out) miss else hit).copy(alpha = 0.35f), dotR, Offset(cx + dx * kw, cy + dy * kh))
                    i += 2
                }
                if (s.n >= 3f) {
                    drawCircle(label, dotR * 1.6f, Offset(cx + s.meanX * kw, cy + s.meanY * kh), style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
