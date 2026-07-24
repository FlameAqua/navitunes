package ie.adrianszydlo.navitunes.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.adrianszydlo.navitunes.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Brand palette, held constant so the splash reads the same in every theme (it's the icon's own navy).
private val SplashTop = Color(0xFF26406F)
private val SplashMid = Color(0xFF15243F)
private val SplashBottom = Color(0xFF0B1526)
private val SplashGlow = Color(0xFF8FE3FF)

/**
 * The animated brand splash shown once on cold start, handing off from the system splash.
 * The fairy logo fades and springs in over a soft pulsing glow (a nod to Navi from Zelda —
 * and to Navidrome behind it), the "Hey, Listen!" slogan follows, then the whole thing
 * dissolves into the app.
 */
@Composable
fun BrandSplash(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.6f) }
    val sloganAlpha = remember { Animatable(0f) }
    val containerAlpha = remember { Animatable(1f) }

    // Gentle, continuous glow behind the logo while it's on screen.
    val glow = rememberInfiniteTransition(label = "splashGlow")
    val glowScale by glow.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glowScale"
    )
    val glowAlpha by glow.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(500)) }
        logoScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
        sloganAlpha.animateTo(1f, tween(450))
        delay(950)
        containerAlpha.animateTo(0f, tween(450))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = containerAlpha.value }
            .background(Brush.verticalGradient(listOf(SplashTop, SplashMid, SplashBottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // Soft radial glow halo — pulses independently of the logo's entrance.
                Box(
                    Modifier
                        .size(260.dp)
                        .graphicsLayer {
                            alpha = glowAlpha * logoAlpha.value
                            scaleX = glowScale
                            scaleY = glowScale
                        }
                        .background(
                            Brush.radialGradient(listOf(SplashGlow.copy(alpha = 0.55f), Color.Transparent))
                        )
                )
                Image(
                    painter = painterResource(R.drawable.nav_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(156.dp)
                        .graphicsLayer {
                            alpha = logoAlpha.value
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                        }
                        .clip(RoundedCornerShape(34.dp))
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Navitunes",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = sloganAlpha.value }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Hey, Listen!",
                style = MaterialTheme.typography.titleMedium,
                color = SplashGlow,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                modifier = Modifier.graphicsLayer { alpha = sloganAlpha.value }
            )
        }
    }
}
