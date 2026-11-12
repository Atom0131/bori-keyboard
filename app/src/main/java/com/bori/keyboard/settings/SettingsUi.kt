package com.bori.keyboard.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Settings building blocks (Material 3 list style, per the Android settings design guide):
 * a page with a top bar and a "Try it here" bar pinned at the bottom; groups with a heading and
 * one rounded surface; rows whose second line shows the current state. Long explanations go
 * behind an info button instead of paragraphs between settings.
 */

/** Widest a settings column gets, so rows don't stretch across an unfolded screen. */
internal val MAX_CONTENT_WIDTH = 640.dp

/**
 * A settings page: back arrow + title, scrolling content, and a "Try it here" bar pinned at
 * the bottom (it sits right above the keyboard when tapped). [onBack] null = the start page.
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    tryBar: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(SettingsIcons.Back, contentDescription = "Back") }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = if (onBack == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
        if (tryBar) TryBar()
    }
}

/** The pinned test field. Typing here uses the real keyboard, so every change shows at once. */
@Composable
internal fun TryBar() {
    var text by remember { mutableStateOf("") }
    Surface(tonalElevation = 3.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Try the keyboard here") },
                leadingIcon = { Icon(SettingsIcons.Keyboard, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** A heading and one rounded surface holding its rows, separated by thin lines. */
@Composable
internal fun Group(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** Thin line between rows inside a [Group]. */
@Composable
internal fun RowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** The shared row layout: optional icon, title + status, optional trailing part. */
@Composable
private fun BaseRow(
    title: String,
    status: String?,
    icon: ImageVector?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    trailing: @Composable (() -> Unit)?,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (!status.isNullOrEmpty()) {
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Opens another page; the title matches that page's title. */
@Composable
internal fun NavRow(title: String, status: String? = null, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    BaseRow(title, status, icon, enabled, onClick) {
        Icon(SettingsIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An on/off setting; the whole row toggles it. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    status: String? = null,
    enabled: Boolean = true,
    info: String? = null,
    onChange: (Boolean) -> Unit,
) {
    BaseRow(title, status, null, enabled, { onChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (info != null) InfoButton(title, info)
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

/** One of several choices: shows the current one, and a radio list in a dialog. */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    status: String? = null,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = choices.firstOrNull { it.first == selected }?.second ?: ""
    BaseRow(title, status ?: current, null, enabled, { open = true }, null)
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for ((value, label) in choices) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = value == selected, onClick = { onSelect(value); open = false }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

/** A range setting: title + current value, slider underneath. [onDone] runs when let go. */
@Composable
internal fun SliderRow(
    title: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    steps: Int = 0,
    onChange: (Float) -> Unit,
    onDone: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range, steps = steps, enabled = enabled)
    }
}

/** Free content inside a group (chips, previews, buttons), padded like a row. */
@Composable
internal fun RowContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** ⓘ that opens a short explanation, for the few settings that need one. */
@Composable
internal fun InfoButton(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(SettingsIcons.Info, contentDescription = "About $title", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("OK") } },
        )
    }
}

/** Small grey text under a group (used sparingly). */
@Composable
internal fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
