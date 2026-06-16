package ie.adrianszydlo.navitunes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// We rely on system fonts to keep APK size small and avoid bundling Fraunces/Geist.
// The look is achieved with weights, italics, and letter-spacing close to the PWA.
val DisplayFont = FontFamily.Serif
val BodyFont = FontFamily.SansSerif
val MonoFont = FontFamily.Monospace

val NavitunesTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 56.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 60.sp
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Italic,
        fontSize = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Italic,
        fontSize = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp
    )
)
