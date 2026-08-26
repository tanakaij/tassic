package tassic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tassic.ui.theme.Amber
import tassic.ui.theme.Blue
import tassic.ui.theme.CardWhite
import tassic.ui.theme.Coral
import tassic.ui.theme.Ink
import tassic.ui.theme.MoodColors
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy
import tassic.ui.theme.SkySoft

/** App-wide snackbar channel so any screen can surface feedback. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** `var x by rememberState(init)` — terse local state in sheets & screens. */
@Composable
fun <T> rememberState(initial: T): MutableState<T> = remember { mutableStateOf(initial) }

/** Snackbar launcher usable from sheets and dialogs. */
class SnackbarScope internal constructor(
    private val host: SnackbarHostState,
    private val scope: CoroutineScope
) {
    fun launchSnackbar(message: String) {
        scope.launch { host.showSnackbar(message) }
    }
}

@Composable
fun rememberSheetScope(): SnackbarScope {
    val host = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    return remember { SnackbarScope(host, scope) }
}

fun Modifier.spacerW(dp: Int): Modifier = this.then(Modifier.width(dp.dp))

// ---------------------------------------------------------------- cards

@Composable
fun TassicCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Navy)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        trailing()
    }
}

@Composable
fun Pill(text: String, bg: Color = SkySoft, fg: Color = Navy, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun StatTile(value: String, label: String, tint: Color = Blue, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

// ---------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Amber,
            contentColor = Navy,
            disabledContainerColor = SkySoft,
            disabledContentColor = Muted
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Blue,
            contentColor = Color.White,
            disabledContainerColor = SkySoft,
            disabledContentColor = Muted
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = Blue, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun IconActionBtn(
    icon: ImageVector,
    contentDesc: String,
    modifier: Modifier = Modifier,
    tint: Color = Muted,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------- rows

@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onChecked: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = Blue,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onChecked)
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(if (checked) tint else Color.Transparent, RoundedCornerShape(7.dp))
                .border(2.dp, if (checked) tint else SkySoft, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) Muted else Ink,
                textDecoration = if (checked) TextDecoration.LineThrough else null
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun TassicProgress(percent: Float, color: Color = Blue, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { percent.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp)),
        color = color,
        trackColor = SkySoft
    )
}

// ---------------------------------------------------------------- pickers

@Composable
fun <T> SelectChips(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
    onSelect: (T) -> Unit
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Navy,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun Stepper(
    label: String,
    value: Int,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..999,
    suffix: String = ""
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
        IconButton(onClick = { onValue((value - 1).coerceIn(range.first, range.last)) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Navy)
        }
        Text(
            "$value$suffix",
            style = MaterialTheme.typography.labelLarge,
            color = Navy,
            modifier = Modifier.width(52.dp)
        )
        IconButton(onClick = { onValue((value + 1).coerceIn(range.first, range.last)) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Navy)
        }
    }
}

@Composable
fun LabeledField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Blue,
            focusedLabelColor = Blue,
            cursorColor = Navy
        )
    )
}

@Composable
fun MoodPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (1..5).forEach { mood ->
            val isSel = mood == selected
            Box(
                Modifier
                    .size(if (isSel) 44.dp else 38.dp)
                    .clip(CircleShape)
                    .background(MoodColors[mood - 1].copy(alpha = if (isSel) 1f else 0.20f))
                    .clickable { onSelect(mood) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$mood",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSel) Color.White else MoodColors[mood - 1]
                )
            }
        }
    }
}

// ---------------------------------------------------------------- menus

@Composable
fun ItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        onUp?.let {
            DropdownMenuItem(
                text = { Text("Move up") },
                leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        onDown?.let {
            DropdownMenuItem(
                text = { Text("Move down") },
                leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        onEdit?.let {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { onDismiss(); it() }
            )
        }
        onDelete?.let {
            DropdownMenuItem(
                text = { Text("Delete", color = Coral) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Coral) },
                onClick = { onDismiss(); it() }
            )
        }
    }
}

@Composable
fun ConfirmDelete(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted) },
        confirmButton = { DestructiveButton("Delete", { onConfirm(); onDismiss() }) },
        dismissButton = { GhostButton("Cancel", onDismiss) }
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SkySoft.copy(alpha = 0.55f))
            .padding(vertical = 18.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Navy)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            GhostButton(actionText, onAction)
        }
    }
}
