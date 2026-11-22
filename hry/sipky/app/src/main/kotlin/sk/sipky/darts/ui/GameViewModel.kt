package sk.sipky.darts.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import sk.sipky.darts.game.Difficulty
import sk.sipky.darts.game.GameConfig
import sk.sipky.darts.game.GameState
import sk.sipky.darts.game.GameType
import sk.sipky.darts.game.RecordBook
import sk.sipky.darts.game.RecordStore

/** Drží menu, rozohranú hru a rekordy cez recreate aktivity. */
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val records = RecordBook(PrefsRecordStore(prefs))

    var difficulty by mutableStateOf(loadDifficulty())
        private set

    /** Rozohraná hra, alebo null, ak je zobrazené menu. */
    var activeGame by mutableStateOf<GameState?>(null)
        private set

    fun selectDifficulty(newDifficulty: Difficulty) {
        difficulty = newDifficulty
        prefs.edit { putString(KEY_DIFFICULTY, newDifficulty.name) }
    }

    fun recordFor(type: GameType): Int? = records.best(type, difficulty)

    fun start(type: GameType) {
        activeGame = GameState(seed = System.nanoTime(), type = type, difficulty = difficulty, records = records)
    }

    fun exitToMenu() {
        activeGame = null
    }

    private fun loadDifficulty(): Difficulty {
        val name = prefs.getString(KEY_DIFFICULTY, null)
        return Difficulty.entries.firstOrNull { it.name == name } ?: GameConfig.defaultDifficulty
    }

    private companion object {
        const val PREFS_NAME = "sipky"
        const val KEY_DIFFICULTY = "difficulty"
    }
}

/** Rekordy v SharedPreferences, kľúč „record_…". */
private class PrefsRecordStore(private val prefs: SharedPreferences) : RecordStore {
    override fun get(key: String): Int? {
        val k = "record_$key"
        return if (prefs.contains(k)) prefs.getInt(k, 0) else null
    }

    override fun set(key: String, value: Int) {
        prefs.edit { putInt("record_$key", value) }
    }
}
