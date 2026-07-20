package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import ie.adrianszydlo.navitunes.ui.theme.Accent

/**
 * Generic single-field text prompt (rename, description, etc.). Pre-fills with
 * [initialValue] and places the cursor at the end. Trims and returns the text via
 * [onConfirm]; the confirm button disables on empty unless [allowEmpty] is set.
 */
@Composable
fun TextPromptDialog(
    title: String,
    initialValue: String = "",
    label: String = "",
    confirmLabel: String = "Save",
    allowEmpty: Boolean = false,
    singleLine: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var field by remember {
        mutableStateOf(TextFieldValue(initialValue, selection = androidx.compose.ui.text.TextRange(initialValue.length)))
    }
    val canConfirm = allowEmpty || field.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = singleLine,
                placeholder = if (label.isNotBlank()) { { Text(label) } } else null
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(field.text.trim()) },
                enabled = canConfirm
            ) { Text(confirmLabel, color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}
