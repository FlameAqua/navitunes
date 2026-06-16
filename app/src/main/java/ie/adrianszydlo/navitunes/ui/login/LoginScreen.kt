package ie.adrianszydlo.navitunes.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.NavitunesApp
import ie.adrianszydlo.navitunes.data.api.SubsonicException
import ie.adrianszydlo.navitunes.data.auth.Profile
import ie.adrianszydlo.navitunes.ui.theme.Accent
import kotlinx.coroutines.launch

/**
 * Login screen for adding (or re-authenticating) a profile.
 * Verifies credentials with a ping call before persisting.
 */
@Composable
fun LoginScreen(
    initialServer: String = "https://music.adrianszydlo.ie",
    profileToEdit: Profile? = null,
    onLoggedIn: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val container = NavitunesApp.container()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(profileToEdit?.name ?: "") }
    var server by remember { mutableStateOf(profileToEdit?.serverUrl ?: initialServer) }
    var username by remember { mutableStateOf(profileToEdit?.username ?: "") }
    var password by remember { mutableStateOf(profileToEdit?.password ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.widthIn(max = 420.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Navi",
                        style = MaterialTheme.typography.displayLarge,
                        fontStyle = FontStyle.Italic
                    )
                    Text(
                        "tunes",
                        style = MaterialTheme.typography.displayLarge,
                        color = Accent,
                        fontStyle = FontStyle.Italic
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "YOUR LIBRARY, ANYWHERE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(48.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile name") },
                    placeholder = { Text("e.g. Home server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it; error = null },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://music.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide" else "Show")
                    }
                }

                val normalized = normalizeServerUrl(server)
                if (normalized != null && !normalized.startsWith("https://")) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Warning: this connection is not encrypted. Use HTTPS over the public internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        error = null
                        val finalServer = normalizeServerUrl(server)
                        if (finalServer == null) { error = "Enter a valid URL"; return@Button }
                        if (username.isBlank() || password.isBlank()) {
                            error = "Username and password are required"
                            return@Button
                        }
                        loading = true
                        scope.launch {
                            try {
                                container.apiClient.callWith(
                                    finalServer, username, password, "ping.view"
                                )
                                if (profileToEdit != null) {
                                    container.profileStore.update(
                                        profileToEdit.copy(
                                            name = name.ifBlank { username },
                                            serverUrl = finalServer,
                                            username = username,
                                            password = password
                                        )
                                    )
                                    container.profileStore.setActive(profileToEdit.id)
                                } else {
                                    container.profileStore.add(
                                        name.ifBlank { username },
                                        finalServer,
                                        username,
                                        password
                                    )
                                }
                                container.onProfileSwitched()
                                onLoggedIn()
                            } catch (t: Throwable) {
                                error = humanError(t)
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(20.dp).width(20.dp)
                        )
                    } else {
                        Text(if (profileToEdit != null) "Save" else "Connect")
                    }
                }

                if (onCancel != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isBlank()) return null
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
    else "https://$trimmed"
    return runCatching {
        val uri = java.net.URI(withScheme)
        require(!uri.host.isNullOrBlank())
        withScheme
    }.getOrNull()
}

private fun humanError(t: Throwable): String = when (t) {
    is SubsonicException ->
        if (t.isAuthError) "Invalid username or password" else (t.message ?: "Login failed")
    is java.net.UnknownHostException -> "Server not reachable. Check the URL and network."
    is javax.net.ssl.SSLException -> "TLS error: ${t.message ?: "certificate invalid"}"
    else -> t.message ?: "Login failed"
}
