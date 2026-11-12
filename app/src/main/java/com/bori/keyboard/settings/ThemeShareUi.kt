package com.bori.keyboard.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import com.bori.keyboard.ime.DiagLog
import com.bori.keyboard.theme.ThemeShare
import com.bori.keyboard.theme.ThemeSpec
import com.bori.keyboard.theme.ThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Stage 3.8: share and import for Settings → Theme and the theme editor.

private const val AUTHORITY = "com.bori.keyboard.files"

/**
 * Exports [spec] with the user's Keys & letters and Background picks baked in and opens the
 * share sheet. Returns an error message, or null when the share sheet opened.
 */
internal suspend fun shareTheme(context: Context, spec: ThemeSpec): String? {
    val prefs = Prefs(context)
    val file = withContext(Dispatchers.IO) {
        runCatching { ThemeShare.export(context, spec, prefs.themeOverrides()) }
            .onFailure { DiagLog.event("theme share failed: $it") }.getOrNull()
    } ?: return "Couldn't make the theme file."
    return runCatching {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = ThemeShare.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bori Keyboard theme: ${spec.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share “${spec.name}”"))
        null
    }.getOrElse { "Couldn't open the share menu." }
}

/** Imports the theme at [uri] into Mine and makes it the keyboard's theme. */
internal fun importThemeNow(context: Context, uri: Uri): Result<ThemeSpec> =
    ThemeShare.import(context, uri).onSuccess { spec ->
        val prefs = Prefs(context)
        prefs.themeId = spec.id
        prefs.themeRevision = prefs.themeRevision + 1
    }

/** A file picker for .boritheme files; [onDone] gets a message to show. */
@Composable
internal fun rememberThemeImporter(onDone: (String) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { importThemeNow(context, uri) }
            onDone(importMessage(result))
        }
    }
    // Any type: shared files often arrive as "application/octet-stream".
    return { launcher.launch(arrayOf("*/*")) }
}

internal fun importMessage(result: Result<ThemeSpec>): String = result.fold(
    onSuccess = { "Added “${it.name}” to Mine. It's your keyboard's theme now." },
    onFailure = { "Couldn't add that theme: ${it.message ?: "not a Bori theme file"}." },
)

/** Deletes a Surprise me theme that was replaced or undone (only user themes). */
internal fun dropTheme(context: Context, id: String) {
    val store = ThemeStore(context)
    if (!store.isBuiltIn(id)) store.delete(id)
}
