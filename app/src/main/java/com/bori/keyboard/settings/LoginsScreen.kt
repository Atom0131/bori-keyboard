package com.bori.keyboard.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bori.keyboard.learn.LoginStore

internal const val SCREEN_LOGINS = "logins"

/** Status line for the "Emails & usernames" row on Clipboard & privacy. */
internal fun loginsStatus(prefs: Prefs, store: LoginStore): String {
    if (!prefs.rememberLogins) return "Off"
    val n = store.list().size
    return if (n == 0) "On · none saved yet" else "On · $n saved"
}

/**
 * Settings → Clipboard & privacy → Emails & usernames: the switch, the saved list (forget one),
 * add one by hand, forget all. See learn/LoginStore.kt.
 */
@Composable
internal fun LoginsScreen(onBack: () -> Unit) = SettingsPage("Emails & usernames", onBack) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val store = remember { LoginStore.get(context) }
    var on by remember { mutableStateOf(prefs.rememberLogins) }
    var version by remember { mutableIntStateOf(0) }
    val saved = remember(version) { store.list() }
    var newEntry by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Group(null) {
        SwitchRow(
            "Remember emails & usernames",
            on,
            status = if (on) "Suggested in email and sign-in fields" else "Off",
            info = "In email and sign-in fields, the suggestion bar shows the emails and usernames you use, and " +
                "@gmail.com-style endings after you type @. An email shows after you use it twice; one you " +
                "type right before a password shows at once. Long-press a suggestion to forget it. Stored on " +
                "this phone only, never in incognito. Passwords are never saved.",
        ) { on = it; prefs.rememberLogins = it }
    }
    if (!on) return@SettingsPage

    Group("Saved (${saved.size})") {
        if (saved.isEmpty()) {
            RowContent {
                Text(
                    "None yet. Sign in somewhere, or add one below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for ((i, e) in saved.withIndex()) {
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.text,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!e.shown) {
                    Text(
                        "learning",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { store.remove(e.text); version++ }) { Text("Forget") }
            }
        }
    }

    Group("Add") {
        RowContent {
            val ok = LoginStore.acceptable(newEntry.trim())
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newEntry,
                    onValueChange = { newEntry = it.filterNot { c -> c.isWhitespace() }.take(80) },
                    label = { Text("Email or username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    store.record(newEntry.trim(), confirmed = true)
                    newEntry = ""
                    version++
                }, enabled = ok) { Text("Add") }
            }
        }
    }

    if (saved.isNotEmpty()) {
        Group(null) {
            NavRow("Forget all", "${saved.size} saved") { confirmClear = true }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Forget all emails and usernames?") },
            text = { Text("The suggestion bar stops offering them until you use them again.") },
            confirmButton = { TextButton(onClick = { store.clear(); confirmClear = false; version++ }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
