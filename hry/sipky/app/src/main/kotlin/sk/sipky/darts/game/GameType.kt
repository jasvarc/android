package sk.sipky.darts.game

/** Druh hry vybraný v menu. Určuje pravidlá aj kľúč rekordu. */
sealed interface GameType {
    /** Kľúč rekordu (bez náročnosti). */
    val recordKey: String

    /** Rekord je počet šípok (menej je lepšie) alebo body kola (viac je lepšie). */
    val lowerIsBetter: Boolean

    fun createRules(): GameRules

    data object Training : GameType {
        override val recordKey = "training"
        override val lowerIsBetter = false
        override fun createRules() = TrainingRules()
    }

    data class X01(val start: Int) : GameType {
        override val recordKey get() = "x01_$start"
        override val lowerIsBetter get() = true
        override fun createRules() = X01Rules(start)
    }

    data class Roundabout(val variant: RoundaboutVariant) : GameType {
        override val recordKey get() = "roundabout_${variant.name.lowercase()}"
        override val lowerIsBetter get() = true
        override fun createRules() = RoundaboutRules(variant)
    }

    companion object {
        val X01_STARTS = listOf(301, 501, 701, 1001)
    }
}
