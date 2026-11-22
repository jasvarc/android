package sk.sipky.darts.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Čo nasleduje po dopade šípky. */
enum class DartOutcome {
    /** Hra pokračuje ďalšou šípkou (alebo koncom kola po tretej). */
    CONTINUE,

    /** Prestrelenie v X01 – kolo končí hneď, skóre sa vráti na začiatok kola. */
    BUST,

    /** Hra je dohraná. */
    FINISHED,
}

/** Pravidlá konkrétnej hry. Stav, ktorý zobrazuje HUD, je Compose snapshot stav. */
sealed class GameRules {

    /** Body (v hre Okolo terča zásahy) v aktuálnom kole. */
    var turnPoints by mutableIntStateOf(0)
        protected set

    open fun startTurn() {
        turnPoints = 0
    }

    abstract fun onDart(result: ThrowResult): DartOutcome

    /** Hodnota dohraného kola pre rekord, ak sa rekord počíta z kôl (tréning). */
    open val turnRecordValue: Int? get() = null
}

/** Voľné hádzanie, body sa len sčítavajú. */
class TrainingRules : GameRules() {
    var total by mutableIntStateOf(0)
        private set

    override fun onDart(result: ThrowResult): DartOutcome {
        total += result.points
        turnPoints += result.points
        return DartOutcome.CONTINUE
    }

    override val turnRecordValue: Int get() = turnPoints
}

/** Odpočítavanie od [start] presne na nulu (bez povinného double na konci). */
class X01Rules(val start: Int) : GameRules() {
    var remaining by mutableIntStateOf(start)
        private set

    /** Posledné kolo skončilo prestrelením. */
    var busted by mutableStateOf(false)
        private set

    private var turnStart = start

    override fun startTurn() {
        super.startTurn()
        turnStart = remaining
        busted = false
    }

    override fun onDart(result: ThrowResult): DartOutcome {
        val next = remaining - result.points
        return when {
            next < 0 -> {
                remaining = turnStart
                turnPoints = 0
                busted = true
                DartOutcome.BUST
            }
            next == 0 -> {
                remaining = 0
                turnPoints += result.points
                DartOutcome.FINISHED
            }
            else -> {
                remaining = next
                turnPoints += result.points
                DartOutcome.CONTINUE
            }
        }
    }
}

enum class RoundaboutVariant {
    /** 1–20 v ľubovoľnom poli, potom 25 (platí aj bull). */
    ANY,

    /** Single 1–20, potom double alebo triple 1–20, potom 25 a 50. */
    MULTIPLE,

    /** Single 1–20, double 1–20, triple 1–20, potom 25 a 50. */
    DOUBLE_TRIPLE,
}

enum class TargetKind { ANY, SINGLE, DOUBLE_OR_TRIPLE, DOUBLE, TRIPLE, ANY_BULL, OUTER_BULL, BULL }

/** Jeden cieľ v hre Okolo terča. [number] je 1–20, 25 alebo 50. */
data class RoundaboutTarget(val number: Int, val kind: TargetKind) {

    fun isHitBy(result: ThrowResult): Boolean = when (kind) {
        TargetKind.ANY -> result.sector == number
        TargetKind.SINGLE -> result.sector == number && result.type == HitType.SINGLE
        TargetKind.DOUBLE_OR_TRIPLE -> result.sector == number &&
            (result.type == HitType.DOUBLE || result.type == HitType.TRIPLE)
        TargetKind.DOUBLE -> result.sector == number && result.type == HitType.DOUBLE
        TargetKind.TRIPLE -> result.sector == number && result.type == HitType.TRIPLE
        TargetKind.ANY_BULL -> result.type == HitType.OUTER_BULL || result.type == HitType.BULL
        TargetKind.OUTER_BULL -> result.type == HitType.OUTER_BULL
        TargetKind.BULL -> result.type == HitType.BULL
    }

    /** Krátky popis, napr. „7", „S7", „D/T 7", „D7", „T7", „25", „50". */
    val label: String
        get() = when (kind) {
            TargetKind.ANY -> "$number"
            TargetKind.SINGLE -> "S$number"
            TargetKind.DOUBLE_OR_TRIPLE -> "D/T $number"
            TargetKind.DOUBLE -> "D$number"
            TargetKind.TRIPLE -> "T$number"
            TargetKind.ANY_BULL, TargetKind.OUTER_BULL -> "25"
            TargetKind.BULL -> "50"
        }
}

/** Okolo terča: postupne trafiť všetky ciele v poradí. [turnPoints] sú zásahy v kole. */
class RoundaboutRules(val variant: RoundaboutVariant) : GameRules() {
    val targets: List<RoundaboutTarget> = targetsFor(variant)

    var targetIndex by mutableIntStateOf(0)
        private set

    val currentTarget: RoundaboutTarget? get() = targets.getOrNull(targetIndex)

    override fun onDart(result: ThrowResult): DartOutcome {
        val target = currentTarget ?: return DartOutcome.FINISHED
        if (!target.isHitBy(result)) return DartOutcome.CONTINUE
        targetIndex += 1
        turnPoints += 1
        return if (targetIndex >= targets.size) DartOutcome.FINISHED else DartOutcome.CONTINUE
    }

    companion object {
        fun targetsFor(variant: RoundaboutVariant): List<RoundaboutTarget> = buildList {
            fun numbers(kind: TargetKind) = (1..20).forEach { add(RoundaboutTarget(it, kind)) }
            when (variant) {
                RoundaboutVariant.ANY -> {
                    numbers(TargetKind.ANY)
                    add(RoundaboutTarget(25, TargetKind.ANY_BULL))
                }
                RoundaboutVariant.MULTIPLE -> {
                    numbers(TargetKind.SINGLE)
                    numbers(TargetKind.DOUBLE_OR_TRIPLE)
                    add(RoundaboutTarget(25, TargetKind.OUTER_BULL))
                    add(RoundaboutTarget(50, TargetKind.BULL))
                }
                RoundaboutVariant.DOUBLE_TRIPLE -> {
                    numbers(TargetKind.SINGLE)
                    numbers(TargetKind.DOUBLE)
                    numbers(TargetKind.TRIPLE)
                    add(RoundaboutTarget(25, TargetKind.OUTER_BULL))
                    add(RoundaboutTarget(50, TargetKind.BULL))
                }
            }
        }
    }
}
