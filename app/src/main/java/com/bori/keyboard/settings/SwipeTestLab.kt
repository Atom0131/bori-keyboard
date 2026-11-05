package com.bori.keyboard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bori.keyboard.dict.BinaryDictionary
import com.bori.keyboard.layout.FieldVariant
import com.bori.keyboard.layout.KeyGeometry
import com.bori.keyboard.layout.Language
import com.bori.keyboard.layout.LayoutId
import com.bori.keyboard.layout.Layouts
import com.bori.keyboard.learn.UserStore
import com.bori.keyboard.predict.KeyProximity
import com.bori.keyboard.predict.SpellCorrector
import com.bori.keyboard.predict.Suggester
import com.bori.keyboard.swipe.GlideDecoder
import com.bori.keyboard.swipe.SyntheticSwipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Swipe Test Lab: runs synthetic swipes for the commonest words through the real glide
 * decoder on this phone and reports accuracy and speed. Mirrors tools/glide_sim.py.
 */
@Composable
fun SwipeTestLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf(Language.EN) }
    var realistic by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var report by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Swipe Test Lab", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Swipes the 500 most common words with simulated fingers and checks what the " +
                "decoder guesses. Your learned words slightly affect the ranking.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (l in Language.entries) {
                FilterChip(selected = lang == l, onClick = { lang = l }, label = { Text(l.displayName) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = realistic, onClick = { realistic = true }, label = { Text("Realistic swipes") })
            FilterChip(selected = !realistic, onClick = { realistic = false }, label = { Text("Clean swipes") })
        }
        Button(enabled = !running, onClick = {
            running = true
            report = ""
            scope.launch {
                report = withContext(Dispatchers.Default) {
                    runLab(context, lang, realistic) { progress = it }
                }
                running = false
            }
        }) { Text(if (running) "Running…" else "Run test") }
        if (running) LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(vertical = 4.dp))
        if (report.isNotEmpty()) Text(report, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun runLab(
    context: android.content.Context,
    lang: Language,
    realistic: Boolean,
    onProgress: (Float) -> Unit,
): String {
    val dict = BinaryDictionary.fromAsset(context, "dict/${lang.tag}.bdic")
    val layout = Layouts.build(LayoutId.ALPHA, lang, FieldVariant.TEXT, showGlobe = true)
    val geometry = KeyGeometry(layout, 1080, 640, 6f, 12f, 12f, 20f)
    val prox = KeyProximity(geometry)
    val user = UserStore.get(context)
    val suggester = Suggester(dict, user, lang.locale)
    val decoder = GlideDecoder(dict)
    val synth = SyntheticSwipe(prox, seed = 11)
    val ctx = Suggester.Context(null, sentenceStart = true)

    val words = ArrayList<String>()
    SpellCorrector(dict).topDescendants(BinaryDictionary.ROOT, 0, 900, 1) { node, _ ->
        val w = dict.wordOf(node)
        val keys = w.lowercase().filter { it.isLetter() }.toSet()
        if (words.size < 500 && w.length >= 2 && keys.size >= 2) words.add(w)
    }

    var top1 = 0
    var top3 = 0
    var totalNs = 0L
    var worstMs = 0.0
    val misses = ArrayList<String>()
    words.forEachIndexed { i, w ->
        val (xs, ys) = synth.pathFor(w, realistic) ?: return@forEachIndexed
        val t = System.nanoTime()
        val res = decoder.decode(xs, ys, xs.size, prox, { suggester.languageScore(it, ctx) }, { false })
        val dt = System.nanoTime() - t
        totalNs += dt
        worstMs = maxOf(worstMs, dt / 1e6)
        val got = res.map { dict.wordOf(it.node).lowercase() }
        val target = w.lowercase()
        if (got.firstOrNull() == target) top1++
        if (target in got.take(3)) top3++ else if (misses.size < 20) misses.add("$w → ${got.take(3).joinToString(", ")}")
        if (i % 10 == 0) onProgress(i / words.size.toFloat())
    }
    val n = words.size.coerceAtLeast(1)
    return buildString {
        appendLine("${lang.displayName}: ${words.size} words")
        appendLine("Top-1 (first guess right): ${"%.1f".format(100.0 * top1 / n)}%")
        appendLine("Top-3 (right word in the bar): ${"%.1f".format(100.0 * top3 / n)}%")
        appendLine("Speed: ${"%.1f".format(totalNs / 1e6 / n)} ms per word (slowest ${"%.0f".format(worstMs)} ms)")
        if (misses.isNotEmpty()) {
            appendLine()
            appendLine("Missed (not in top 3):")
            misses.forEach { appendLine("  $it") }
        }
    }
}
