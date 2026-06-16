package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Text3
import ie.adrianszydlo.navitunes.ui.theme.Text4

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    glyph: String = "∅",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            glyph,
            fontStyle = FontStyle.Italic,
            color = Text4,
            fontSize = 72.sp,
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = Text3
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAction) { Text(actionLabel, color = Accent) }
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    body: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EmptyState(
        title = title,
        body = body,
        glyph = "!",
        actionLabel = if (onRetry != null) "Retry" else null,
        onAction = onRetry,
        modifier = modifier
    )
}

@Composable
fun SectionHead(title: String, subtitle: String? = null) {
    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        if (subtitle != null) {
            Text(
                subtitle.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Text3,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
