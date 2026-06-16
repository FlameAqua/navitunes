package ie.adrianszydlo.navitunes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ie.adrianszydlo.navitunes.ui.nav.RootNav
import ie.adrianszydlo.navitunes.ui.theme.NavitunesTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NavitunesTheme {
                RootNav()
            }
        }
    }
}
