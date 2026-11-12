package com.bori.keyboard.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bori.keyboard.view.EffectsView
import com.bori.keyboard.view.LedLights

/**
 * Settings → Theme → Effects & lighting (Stage 3.5), on top of any theme. Every change is a pref
 * the keyboard listens to; try it in the bar at the bottom. Off in password fields and with the
 * phone's animations off. Colour and speed rows only show for the effect / mode that uses them.
 */
@Composable
internal fun EffectsScreen(onBack: () -> Unit) = SettingsPage("Effects & lighting", onBack) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { Prefs(context) }
    var effect by remember { mutableStateOf(prefs.pressEffect) }
    var effectColor by remember { mutableStateOf(prefs.effectColor) }
    var mode by remember { mutableStateOf(prefs.ledMode) }
    var target by remember { mutableStateOf(prefs.ledTarget) }
    var ledColor by remember { mutableStateOf(prefs.ledColor) }
    var speed by remember { mutableFloatStateOf(prefs.ledSpeed) }
    var brightness by remember { mutableFloatStateOf(prefs.ledBrightness) }

    Group("When you press a key") {
        ChoiceRow("Effect", EffectsView.Effect.entries.map { it.id to it.title }, effect) { effect = it; prefs.pressEffect = it }
        if (effect != "none") {
            RowDivider()
            RowContent {
                Text("Colour")
                Swatches(listOf("theme", "rainbow") + PALETTE, effectColor) { effectColor = it; prefs.effectColor = it }
            }
        }
    }

    Group("LED lighting") {
        ChoiceRow("Lighting", LedLights.Mode.entries.map { it.id to it.title }, mode) { mode = it; prefs.ledMode = it }
        if (mode != "off") {
            RowDivider()
            ChoiceRow("Lights up", LedLights.Target.entries.map { it.id to it.title }, target) { target = it; prefs.ledTarget = it }
            if (mode == "breathing" || mode == "reactive") {
                RowDivider()
                RowContent {
                    Text("Colour")
                    Swatches(listOf("rainbow") + PALETTE, ledColor) { ledColor = it; prefs.ledColor = it }
                }
            }
            RowDivider()
            if (mode != "rows") {
                SliderRow("Speed", speed, "${(speed * 100).toInt()}%", 0.25f..3f, onChange = { speed = it }, onDone = { prefs.ledSpeed = speed })
            }
            SliderRow("Brightness", brightness, "${(brightness * 100).toInt()}%", 0.1f..1f, onChange = { brightness = it }, onDone = { prefs.ledBrightness = brightness })
        }
    }
    Footnote("Type in the bar below to see them. Moving lights use a little more battery; all effects stop in password fields.")
}

private val PALETTE = listOf("#FF3B6B", "#FF9F1C", "#FFE14D", "#3DDC84", "#22D3EE", "#3D8BFF", "#9B5DE5", "#FFFFFF")

@Composable
private fun Chips(choices: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((id, label) in choices) {
            FilterChip(selected = current == id, onClick = { onPick(id) }, label = { Text(label) })
        }
    }
}

/** Colour dots; "theme" shows the app's primary colour, "rainbow" a rainbow. */
@Composable
private fun Swatches(choices: List<String>, current: String, onPick: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val selectedRing = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in choices) {
            val chosen = c == current
            Canvas(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(if (chosen) 3.dp else 1.dp, if (chosen) selectedRing else outline, CircleShape)
                    .clickable { onPick(c) },
            ) {
                when (c) {
                    "rainbow" -> drawRect(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    "theme" -> drawRect(primary)
                    else -> drawRect(Color(android.graphics.Color.parseColor(c)))
                }
            }
        }
    }

}
