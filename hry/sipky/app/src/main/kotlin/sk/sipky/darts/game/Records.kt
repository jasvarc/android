package sk.sipky.darts.game

/** Úložisko celých čísel podľa kľúča (v aplikácii SharedPreferences, v testoch pamäť). */
interface RecordStore {
    fun get(key: String): Int?
    fun set(key: String, value: Int)
}

class InMemoryRecordStore : RecordStore {
    private val values = HashMap<String, Int>()
    override fun get(key: String): Int? = values[key]
    override fun set(key: String, value: Int) {
        values[key] = value
    }
}

/** Rekordy pre každý druh hry a náročnosť zvlášť. */
class RecordBook(private val store: RecordStore) {

    fun best(type: GameType, difficulty: Difficulty): Int? = store.get(key(type, difficulty))

    /** Zapíše [value], ak je lepšia ako doterajší rekord. Vráti true pri novom rekorde. */
    fun submit(type: GameType, difficulty: Difficulty, value: Int): Boolean {
        val old = best(type, difficulty)
        val better = if (type.lowerIsBetter) {
            value > 0 && (old == null || value < old)
        } else {
            value > (old ?: 0)
        }
        if (better) store.set(key(type, difficulty), value)
        return better
    }

    private fun key(type: GameType, difficulty: Difficulty) = "${type.recordKey}_${difficulty.name.lowercase()}"
}
