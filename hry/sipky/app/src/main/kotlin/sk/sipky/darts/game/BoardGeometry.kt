package sk.sipky.darts.game

import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

enum class HitType { BULL, OUTER_BULL, SINGLE, DOUBLE, TRIPLE, MISS }

/** Výsledok jedného hodu. [sector] je 0 pre bull a minutie. */
data class ThrowResult(
    val points: Int,
    val multiplier: Int,
    val sector: Int,
    val type: HitType,
) {
    /** Krátky popis, napr. „T20", „D16", „Bull", „25", „Mimo". */
    val label: String
        get() = when (type) {
            HitType.BULL -> "Bull"
            HitType.OUTER_BULL -> "25"
            HitType.SINGLE -> sector.toString()
            HitType.DOUBLE -> "D$sector"
            HitType.TRIPLE -> "T$sector"
            HitType.MISS -> "Mimo"
        }
}

/**
 * Geometria štandardného terča. Jednotky sú mm, počiatok v strede terča, x doprava, y nadol.
 *
 * Bod presne na drôte medzi dvoma pásmami patrí k pásmu s vyššou hodnotou:
 * bull a outer bull vrátane svojho polomeru, triple a double vrátane oboch okrajov.
 */
object BoardGeometry {
    const val BULL_RADIUS = 6.35f
    const val OUTER_BULL_RADIUS = 15.9f
    const val TRIPLE_INNER_RADIUS = 99f
    const val TRIPLE_OUTER_RADIUS = 107f
    const val DOUBLE_INNER_RADIUS = 162f
    const val DOUBLE_OUTER_RADIUS = 170f

    /** Vizuálny okraj terča vrátane číselného prstenca. */
    const val BOARD_RADIUS = 225f

    const val SECTOR_DEGREES = 18.0

    /** Čísla sektorov v smere hodinových ručičiek od vrchu. */
    val SECTORS = intArrayOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

    /** Uhol v stupňoch v smere hodinových ručičiek od vrchu, v rozsahu [0, 360). */
    fun angleDegrees(xMm: Float, yMm: Float): Double {
        val deg = Math.toDegrees(atan2(xMm.toDouble(), -yMm.toDouble()))
        return ((deg % 360.0) + 360.0) % 360.0
    }

    /** Index sektora (0 = sektor 20) pre daný uhol. */
    fun sectorIndex(angleDegrees: Double): Int {
        val shifted = ((angleDegrees + SECTOR_DEGREES / 2) % 360.0 + 360.0) % 360.0
        return floor(shifted / SECTOR_DEGREES).toInt().coerceIn(0, SECTORS.size - 1)
    }

    fun score(xMm: Float, yMm: Float): ThrowResult {
        val r = sqrt(xMm.toDouble() * xMm + yMm.toDouble() * yMm)
        if (r <= BULL_RADIUS) return ThrowResult(50, 1, 0, HitType.BULL)
        if (r <= OUTER_BULL_RADIUS) return ThrowResult(25, 1, 0, HitType.OUTER_BULL)
        if (r > DOUBLE_OUTER_RADIUS) return ThrowResult(0, 0, 0, HitType.MISS)

        val sector = SECTORS[sectorIndex(angleDegrees(xMm, yMm))]
        return when {
            r < TRIPLE_INNER_RADIUS -> ThrowResult(sector, 1, sector, HitType.SINGLE)
            r <= TRIPLE_OUTER_RADIUS -> ThrowResult(sector * 3, 3, sector, HitType.TRIPLE)
            r < DOUBLE_INNER_RADIUS -> ThrowResult(sector, 1, sector, HitType.SINGLE)
            else -> ThrowResult(sector * 2, 2, sector, HitType.DOUBLE)
        }
    }
}
