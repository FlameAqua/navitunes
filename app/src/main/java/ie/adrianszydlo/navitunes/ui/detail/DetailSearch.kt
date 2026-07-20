package ie.adrianszydlo.navitunes.ui.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.data.api.Album
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Text3

/**
 * Compact in-list search field for filtering the tracks/albums shown on a detail
 * screen (album, artist, playlist). Purely client-side — filters the already-loaded
 * list, so it's instant and works offline.
 */
@Composable
fun DetailSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search…",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(placeholder, color = Text3) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Text3) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = Text3, modifier = Modifier.size(18.dp))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}

/** Case-insensitive filter of songs by title / artist / album. Blank query → unchanged. */
fun List<Song>.matching(query: String): List<Song> {
    val q = query.trim()
    if (q.isBlank()) return this
    return filter { s ->
        s.title.contains(q, ignoreCase = true) ||
            s.artist?.contains(q, ignoreCase = true) == true ||
            s.album?.contains(q, ignoreCase = true) == true
    }
}

/** Case-insensitive filter of albums by name / artist. Blank query → unchanged. */
fun List<Album>.matchingAlbums(query: String): List<Album> {
    val q = query.trim()
    if (q.isBlank()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
}
