package com.bori.keyboard.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bori.keyboard.ime.SwipeRecorder
import com.bori.keyboard.learn.UserStore

/** Words chosen to exercise contractions, s-words, short words and common words. */
private val TRAINER_WORDS = mapOf(
    "en" to listOf(
        "it's", "don't", "I'm", "can't", "won't", "didn't", "that's", "you're", "we're", "let's",
        "isn't", "doesn't", "I'll", "I've", "they're", "there's", "what's", "he's", "she's", "wasn't",
        "couldn't", "wouldn't", "shouldn't", "haven't",
        "is", "as", "was", "has", "its", "yes", "so", "see", "said", "says", "this", "also", "still",
        "just", "thanks", "things", "guys", "days", "words", "keys", "cats", "goes", "does", "sorry",
        "sure", "same", "some", "something", "should", "school", "us", "less", "miss", "pass",
        "the", "and", "to", "you", "of", "in", "for", "on", "with", "that", "have", "not", "but",
        "what", "all", "are", "they", "we", "can", "my", "me", "do", "at", "your", "will", "from",
        "good", "know", "like", "time", "people", "going", "think", "want", "really", "about", "would",
        "there", "here", "where", "when", "because", "right", "need", "love", "okay", "food",
        "keyboard", "text", "mode", "stage", "trouble", "installed", "accurate", "correction",
    ),
    "es" to listOf(
        "está", "también", "aquí", "mañana", "qué", "cómo", "sí", "más", "así", "después",
        "es", "eso", "esto", "estos", "esas", "casa", "cosas", "días", "gracias", "pues", "bueno",
        "que", "de", "la", "el", "en", "y", "a", "los", "se", "no", "un", "por", "con", "una",
        "para", "pero", "muy", "todo", "hacer", "tengo", "quiero", "puedo", "tiempo", "ahora",
        "necesito", "canción", "porque", "nada", "algo",
    ),
)

@Composable
fun SwipeTrainerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val store = remember { UserStore.get(context) }
    var lang by remember { mutableStateOf("en") }
    var index by remember { mutableIntStateOf(0) }
    var recorded by remember { mutableIntStateOf(SwipeRecorder.ensureLoaded(context).let { SwipeRecorder.count }) }
    var last by remember { mutableStateOf("") }
    var hits by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf<EditText?>(null) }
    val words = TRAINER_WORDS.getValue(lang)
    val target = words[index % words.size]
    SideEffect { SwipeRecorder.target = target }

    DisposableEffect(Unit) {
        SwipeRecorder.listener = { rec ->
            val ok = rec.decoded.firstOrNull()?.equals(rec.target, ignoreCase = true) == true
            tries++
            if (ok) hits++
            last = (if (ok) "✓ " else "✗ ") + "${rec.target} → ${rec.decoded.joinToString(", ").ifEmpty { "(nothing)" }}"
            recorded = SwipeRecorder.count
            index++
            field?.setText("")
        }
        onDispose {
            SwipeRecorder.listener = null
            SwipeRecorder.target = null
        }
    }

    SettingsPage("Swipe Trainer", onBack, tryBar = false) {
        Text(
            "Swipe each word shown below with Bori Keyboard, the way you normally would. " +
                "Switch the keyboard to the same language. Your swipes teach Bori where your finger " +
                "really lands. Tap “Copy data” to share them for tuning; nothing leaves the phone otherwise.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lang == "en", onClick = { lang = "en"; index = 0 }, label = { Text("English") })
            FilterChip(selected = lang == "es", onClick = { lang = "es"; index = 0 }, label = { Text("Español") })
        }
        Text("Word ${index % words.size + 1} of ${words.size}", style = MaterialTheme.typography.labelMedium)
        Text(target, style = MaterialTheme.typography.displaySmall)
        AndroidView(
            factory = { ctx ->
                EditText(ctx).apply {
                    privateImeOptions = SwipeRecorder.TRAINER_TAG
                    inputType = InputType.TYPE_CLASS_TEXT
                    imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    hint = "Swipe the word here"
                    setSingleLine()
                }.also { field = it }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (last.isNotEmpty()) Text(last)
        if (tries > 0) Text("This session: $hits of $tries right first time")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { index++; field?.setText("") }) { Text("Skip") }
            Button(onClick = {
                val json = SwipeRecorder.exportJson(context)
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Bori swipes", json))
                copied = true
            }, enabled = recorded > 0) { Text("Copy data ($recorded)") }
        }
        if (copied) Text("Copied. Paste it into your chat with Claude.", style = MaterialTheme.typography.bodySmall)

        PrefSwitch("Learn my swipe style", prefs.adaptSwipe) { prefs.adaptSwipe = it }
        Text(
            "Learned from ${store.keyOffsetSampleCount()} key touches so far.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!confirmClear) {
            OutlinedButton(onClick = { confirmClear = true }) { Text("Reset swipe learning & recordings") }
        } else {
            Text("Forget your learned finger offsets and delete recorded swipes?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    store.clearKeyOffsets()
                    SwipeRecorder.clear(context)
                    recorded = 0
                    confirmClear = false
                }) { Text("Reset") }
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        }
    }
}
