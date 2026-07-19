package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ie.adrianszydlo.navitunes.ui.theme.NavTheme

/**
 * Standard grouped-content card: rounded surface with a hairline border in dark and
 * a soft shadow in light, so sections read as distinct cards in both themes.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = NavTheme.colors
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (colors.isDark) Modifier
                else Modifier.shadow(8.dp, shape, clip = false, ambientColor = colors.borderStrong, spotColor = colors.borderStrong)
            )
            .clip(shape)
            .background(colors.surface)
            .then(if (colors.isDark) Modifier.border(1.dp, colors.border, shape) else Modifier),
        content = content
    )
}
