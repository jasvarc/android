package sk.sipky.darts.ui

import androidx.lifecycle.ViewModel
import sk.sipky.darts.game.GameState

/** Drží stav hry cez recreate aktivity. */
class GameViewModel : ViewModel() {
    val game = GameState(seed = System.nanoTime())
}
