package space.maxsim.ulicy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UlicyTheme {
                val ctx = LocalContext.current
                val content = remember { Content.get(ctx) }
                val store = remember { Store(ctx) }
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") { HomeScreen(content, store, nav) }
                    composable("hero/{id}") { HeroScreen(content, it.arguments?.getString("id") ?: "", nav) }
                    composable("quest") { QuestScreen(content, store, nav) }
                    composable(
                        "ar?plaque={plaque}&mode={mode}",
                        arguments = listOf(
                            navArgument("plaque") { type = NavType.StringType; defaultValue = "" },
                            navArgument("mode") { type = NavType.StringType; defaultValue = "plaques" }
                        )
                    ) {
                        ArScreen(content, store, nav, it.arguments?.getString("plaque"), it.arguments?.getString("mode") ?: "plaques")
                    }
                    composable("quiz") { QuizScreen(content, store, nav) }
                    composable("final") { FinalScreen(content, store, nav) }
                    composable("about") { AboutScreen(content, nav) }
                }
            }
        }
    }
}
