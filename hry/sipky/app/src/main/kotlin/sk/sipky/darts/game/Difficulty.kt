package sk.sipky.darts.game

/** Sila trasenia: amplitúda (mm) a násobok frekvencií z [GameConfig.swayFreqX] a [GameConfig.swayFreqY]. */
data class SwayLevel(val amplitudeMm: Float, val freqScale: Float)

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    ;

    val sway: SwayLevel
        get() = when (this) {
            EASY -> GameConfig.swayEasy
            MEDIUM -> GameConfig.swayMedium
            HARD -> GameConfig.swayHard
        }
}
