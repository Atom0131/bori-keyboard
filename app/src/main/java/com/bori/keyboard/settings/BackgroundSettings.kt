package com.bori.keyboard.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bori.keyboard.ime.DiagLog
import com.bori.keyboard.theme.Backdrop
import com.bori.keyboard.theme.BackgroundSpec
import com.bori.keyboard.theme.KeyboardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Settings → Theme → Background (Stage 3.4): the theme's own, a plain colour, a gradient, a drawn
 * scene or the user's photo, on top of any theme. Saved as one pref (a BackgroundSpec); sliders
 * save when let go (each save redraws the picture). Only the controls for the chosen type show.
 */
@Composable
internal fun BackgroundScreen(onBack: () -> Unit) = SettingsPage("Background", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()
    var spec by remember { mutableStateOf(prefs.backgroundOverride) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val preview = currentPreviewTheme(prefs, spec)
    fun save(s: BackgroundSpec?) {
        spec = s
        prefs.backgroundOverride = s
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = ""
        scope.launch {
            val name = withContext(Dispatchers.IO) { BackgroundPhotos.import(context, uri) }
            busy = false
            if (name == null) {
                error = "Couldn't open that picture. Try another one."
            } else {
                val old = spec?.takeIf { it.type == "photo" }
                save((old ?: BackgroundSpec()).copy(type = "photo", image = name, zoom = 1f, focusX = 0.5f, focusY = 0.5f))
            }
        }
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    KeyboardPreview(preview, Modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(16.dp)))
    if (busy) Footnote("Opening the picture…")
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

    val type = spec?.type ?: ""
    Group(null) {
        ChoiceRow(
            "Background",
            listOf("" to "The theme's own", "none" to "Plain colour", "gradient" to "Gradient", "scene" to "Scene", "photo" to "Your photo"),
            type,
            status = backgroundStatus(spec),
        ) {
            when (it) {
                "" -> save(null)
                "none" -> save(BackgroundSpec(type = "none"))
                "gradient" -> save(BackgroundSpec(type = "gradient", colors = Backdrop.GRADIENTS[0]))
                "scene" -> save(BackgroundSpec(type = "scene", scene = Backdrop.SCENES[0].first))
                "photo" -> pickPhoto()
            }
        }
    }

    val s = spec ?: return@SettingsPage
    when (s.type) {
        "gradient" -> Group("Gradient") {
            RowContent {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (g in Backdrop.GRADIENTS) {
                        val colors = g.map { Color(android.graphics.Color.parseColor(it)) }
                        val chosen = s.colors == g
                        Canvas(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                                .clickable { save(s.copy(colors = g)) },
                        ) { drawRect(Brush.linearGradient(colors)) }
                    }
                }
            }
            RowDivider()
            ChoiceRow("Direction", listOf(90 to "Top to bottom", 45 to "Diagonal ↘", 0 to "Left to right", 135 to "Diagonal ↙"), s.angle) {
                save(s.copy(angle = it))
            }
        }
        "scene" -> Group("Scene") {
            ChoiceRow("Scene", Backdrop.SCENES, s.scene ?: Backdrop.SCENES[0].first, status = null) { save(s.copy(scene = it)) }
        }
        "photo" -> Group("Photo") {
            NavRow("Pick another photo", "Stays on this phone") { pickPhoto() }
            RowDivider()
            SavedSlider("Zoom", s.zoom, 1f..3f, { "${(it * 100).toInt()}%" }) { save(s.copy(zoom = it)) }
            SavedSlider("Left – right", s.focusX, 0f..1f, { "${(it * 100).toInt()}%" }) { save(s.copy(focusX = it)) }
            SavedSlider("Up – down", s.focusY, 0f..1f, { "${(it * 100).toInt()}%" }) { save(s.copy(focusY = it)) }
            SavedSlider("Blur", s.blur, 0f..1f, { "${(it * 100).toInt()}%" }) { save(s.copy(blur = it)) }
        }
    }
    if (s.type != "none") {
        Group("Adjust") {
            SavedSlider("Brightness", s.brightness, -0.8f..0.6f, { (if (it > 0) "+" else "") + "${(it * 100).toInt()}%" }) {
                save(s.copy(brightness = it))
            }
            SavedSlider("Picture strength", s.opacity, 0.2f..1f, { "${(it * 100).toInt()}%" }) { save(s.copy(opacity = it)) }
        }
        Footnote("Hard-to-read letters switch to black or white by themselves.")
    }
}

/** A slider row that saves only when let go. */
@Composable
private fun SavedSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onSave: (Float) -> Unit,
) {
    var v by remember(value) { mutableStateOf(value) }
    SliderRow(label, v, format(v), range, onChange = { v = it }, onDone = { onSave(v) })
}

/** Copies a picked photo into filesDir/backgrounds, upright and at most [MAX_SIDE] px. */
internal object BackgroundPhotos {
    private const val MAX_SIDE = 2048

    fun dir(context: Context) = File(context.filesDir, "backgrounds")

    /** Returns the new file name, or null if the picture couldn't be read. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val bmp = decode(context, uri) ?: return null
        val d = dir(context).apply { mkdirs() }
        val name = "photo_${System.currentTimeMillis()}.jpg"
        val tmp = File(d, "$name.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        if (!tmp.renameTo(File(d, name))) return null
        // One photo at a time: delete the old ones.
        d.listFiles()?.filter { it.name != name && it.name.startsWith("photo_") }?.forEach { it.delete() }
        name
    }.onFailure { DiagLog.event("background photo failed: $it") }.getOrNull()

    private fun decode(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder turns the photo upright (EXIF) and scales while decoding.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val k = max(w, h).toFloat() / MAX_SIDE
                if (k > 1f) decoder.setTargetSize((w / k).toInt().coerceAtLeast(1), (h / k).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
