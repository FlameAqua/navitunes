package ie.adrianszydlo.navitunes.ui.update

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.update.ApkInstaller
import ie.adrianszydlo.navitunes.data.update.UpdateStatus
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Text3
import kotlinx.coroutines.launch

/**
 * Prompts the user that [available] is out, and drives the download → install
 * flow. [onSkip] is only supplied for the on-launch prompt (so the user can mute
 * this specific version); the manual "Check for updates" path omits it.
 */
@Composable
fun UpdateAvailableDialog(
    available: UpdateStatus.Available,
    onDismiss: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val container = NavitunesApp.container()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val startUpdate: () -> Unit = start@{
        val apkUrl = available.apkUrl
        if (apkUrl == null) {
            // No APK asset on the release — fall back to opening the release page.
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.releaseUrl)))
            }
            onDismiss()
            return@start
        }
        downloading = true
        progress = 0f
        scope.launch {
            val result = container.apkInstaller.downloadAndInstall(
                context = ctx,
                apkUrl = apkUrl,
                versionName = available.versionName,
                onProgress = { progress = it }
            )
            downloading = false
            when (result) {
                ApkInstaller.Result.Launched -> onDismiss()
                ApkInstaller.Result.NeedsUnknownSourcesPermission ->
                    Toast.makeText(
                        ctx,
                        "Allow Navitunes to install apps, then tap Update again.",
                        Toast.LENGTH_LONG
                    ).show()
                is ApkInstaller.Result.Failed ->
                    Toast.makeText(ctx, "Update failed: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Update available") },
        text = {
            Column {
                Text(
                    "Version ${available.versionName} is available. You're on " +
                        "${ie.adrianszydlo.navitunes.BuildConfig.VERSION_NAME}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (available.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        available.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = Text3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                if (downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Downloading… ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Text3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = startUpdate, enabled = !downloading) {
                Text(if (available.apkUrl != null) "Update & restart" else "View release", color = Accent)
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = {
                    if (onSkip != null) onSkip() else onDismiss()
                }) { Text(if (onSkip != null) "Skip" else "Later") }
            }
        }
    )
}
