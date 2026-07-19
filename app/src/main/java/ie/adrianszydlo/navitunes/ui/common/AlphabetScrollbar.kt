package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.ui.theme.Accent
import ie.adrianszydlo.navitunes.ui.theme.Text4

/** Maps a name to its A–Z bucket ('#' for anything not starting with a letter). */
fun firstLetterBucket(name: String): Char {
    val c = name.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

/** First index for each bucket, in list order — for jumping a list/grid to a letter. */
fun buildLetterIndex(names: List<String>): Map<Char, Int> {
    val map = LinkedHashMap<Char, Int>()
    names.forEachIndexed { i, n ->
        val b = firstLetterBucket(n)
        if (b !in map) map[b] = i
    }
    return map
}

/**
 * A–Z scrubber pinned to the right edge of a list/grid. Tapping or dragging a letter
 * calls [onSelect]; letters with no matching items are dimmed. The caller maps the
 * letter to an index (via [buildLetterIndex]) and scrolls.
 */
@Composable
fun AlphabetScrollbar(
    activeLetters: Set<Char>,
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val alphabet = remember { ('A'..'Z').toList() + '#' }

    fun letterAt(y: Float, height: Int): Char {
        val idx = (y / height * alphabet.size).toInt().coerceIn(0, alphabet.lastIndex)
        return alphabet[idx]
    }

    Column(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSelect(letterAt(offset.y, size.height)) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    onSelect(letterAt(change.position.y, size.height))
                }
            }
            // Wide start padding = a bigger, easier hit target; end padding keeps
            // the letters off the very edge of the screen.
            .padding(start = 16.dp, end = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alphabet.forEach { c ->
            Text(
                c.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (c in activeLetters) Accent else Text4
            )
        }
    }
}
