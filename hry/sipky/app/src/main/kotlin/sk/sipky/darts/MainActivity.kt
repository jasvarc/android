package sk.sipky.darts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import sk.sipky.darts.ui.GameScreen
import sk.sipky.darts.ui.GameViewModel
import sk.sipky.darts.ui.MenuScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            val viewModel: GameViewModel = viewModel()
            val game = viewModel.activeGame
            if (game == null) {
                MenuScreen(
                    difficulty = viewModel.difficulty,
                    onDifficulty = viewModel::selectDifficulty,
                    recordFor = viewModel::recordFor,
                    onStart = viewModel::start,
                )
            } else {
                BackHandler(onBack = viewModel::exitToMenu)
                GameScreen(game, onExit = viewModel::exitToMenu)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
