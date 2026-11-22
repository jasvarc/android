package sk.sipky.darts.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class Phase {
    /** Ruka so šípkou je pripravená, zameriavač je viditeľný. */
    READY,

    /** Šípka letí k terču. */
    FLIGHT,

    /** Ruka s novou šípkou sa vracia do východiskovej polohy. */
    RETURNING,

    /** Po tretej šípke (alebo prestrelení) sa zobrazuje súhrn kola. */
    ROUND_SUMMARY,

    /** Hra je dohraná, čaká sa na „Hrať znova" alebo návrat do menu. */
    GAME_OVER,
}

/** Šípka zapichnutá v terči. [tiltDeg] je náhodný náklon pri kreslení. */
class StuckDart(val xMm: Float, val yMm: Float, val tiltDeg: Float, val result: ThrowResult)

/**
 * Stav hry a jej pravidlá, bez závislosti na kreslení. Vstupy prichádzajú v mm v mierke terča.
 *
 * Hodnoty, ktoré zobrazuje HUD, sú Compose snapshot stav, aby sa HUD prekreslil len pri zmene.
 * [timeSeconds] sa mení každý snímok a jeho čítaním sa prekresľuje scéna.
 */
class GameState(
    seed: Long,
    val type: GameType = GameType.Training,
    val difficulty: Difficulty = GameConfig.defaultDifficulty,
    private val records: RecordBook = RecordBook(InMemoryRecordStore()),
) {
    private val random = Random(seed xor 0x5DEECE66DL)
    val sway = Sway(seed).apply { setLevel(difficulty.sway) }

    var rules: GameRules by mutableStateOf(type.createRules().apply { startTurn() })
        private set

    /** Doterajší rekord pre tento druh hry a náročnosť. */
    var record by mutableStateOf(records.best(type, difficulty))
        private set

    /** Posledné kolo alebo dohraná hra priniesli nový rekord. */
    var isNewRecord by mutableStateOf(false)
        private set

    var timeSeconds by mutableDoubleStateOf(0.0)
        private set
    var phase by mutableStateOf(Phase.READY)
        private set
    var round by mutableIntStateOf(1)
        private set

    /** Všetky hodené šípky v tejto hre. */
    var totalDarts by mutableIntStateOf(0)
        private set

    /** Počet šípok, ktoré v tomto kole už dopadli. */
    var dartsLanded by mutableIntStateOf(0)
        private set
    var lastResult by mutableStateOf<ThrowResult?>(null)
        private set

    /** Čas v aktuálnej fáze (s). */
    var phaseTime = 0f
        private set

    var dragXMm = 0f
        private set
    var dragYMm = 0f
        private set

    /** Hráč práve drží prst na displeji a mieri. */
    var aiming = false
        private set

    val stuckDarts: List<StuckDart> get() = darts
    private val darts = ArrayList<StuckDart>(GameConfig.dartsPerRound)

    // Aktuálny let
    var targetXMm = 0f
        private set
    var targetYMm = 0f
        private set
    var releaseHandXMm = 0f
        private set
    var releaseHandYMm = 0f
        private set
    private var flightResult: ThrowResult? = null
    var flightTiltDeg = 0f
        private set

    /** Poradové číslo šípky v kole (1 až [GameConfig.dartsPerRound]). */
    val dartNumber: Int get() = minOf(dartsLanded + 1, GameConfig.dartsPerRound)

    /** Bod, kam by šípka práve dopadla (mm). */
    val aimXMm: Float get() = dragXMm + sway.x(timeSeconds)
    val aimYMm: Float get() = dragYMm + sway.y(timeSeconds)

    /** Posun ruky na obrazovke (mm v mierke terča) – ruka sa trasie a mierne sleduje ťahanie. */
    val handXMm: Float get() = sway.x(timeSeconds) * GameConfig.handSwayFactor + dragXMm * GameConfig.handDragFactor
    val handYMm: Float get() = sway.y(timeSeconds) * GameConfig.handSwayFactor + dragYMm * GameConfig.handDragFactor

    /** Priebeh letu 0..1. */
    val flightProgress: Float get() = (phaseTime / GameConfig.flightSeconds).coerceIn(0f, 1f)

    /** Priebeh návratu ruky 0..1. */
    val returnProgress: Float get() = (phaseTime / GameConfig.handReturnSeconds).coerceIn(0f, 1f)

    /**
     * Posunie hru o [dtSeconds]. Vráti true v snímku, v ktorom šípka dopadla do terča
     * (na spustenie haptickej odozvy).
     */
    fun update(dtSeconds: Float): Boolean {
        timeSeconds += dtSeconds
        phaseTime += dtSeconds
        when (phase) {
            Phase.READY -> Unit
            Phase.FLIGHT -> if (phaseTime >= GameConfig.flightSeconds) {
                land()
                return true
            }
            Phase.RETURNING -> if (phaseTime >= GameConfig.handReturnSeconds) enter(Phase.READY)
            Phase.ROUND_SUMMARY -> if (phaseTime >= GameConfig.roundSummarySeconds) {
                darts.clear()
                round += 1
                dartsLanded = 0
                isNewRecord = false
                rules.startTurn()
                startReturn()
            }
            Phase.GAME_OVER -> Unit
        }
        return false
    }

    /** Prst sa dotkol displeja. Vráti false, ak sa vstup teraz ignoruje. */
    fun onPointerDown(): Boolean {
        if (phase != Phase.READY) return false
        aiming = true
        return true
    }

    /** Prst sa posunul o ([dxMm], [dyMm]) v mierke terča. */
    fun onPointerMove(dxMm: Float, dyMm: Float) {
        if (!aiming || phase != Phase.READY) return
        val max = GameConfig.maxDragOffsetMm
        dragXMm = (dragXMm + dxMm).coerceIn(-max, max)
        dragYMm = (dragYMm + dyMm).coerceIn(-max, max)
    }

    /** Prst sa zdvihol – ak hráč mieril, šípka sa vypustí presne do zameriavača. */
    fun onPointerUp() {
        if (!aiming) return
        aiming = false
        if (phase != Phase.READY) return
        targetXMm = aimXMm
        targetYMm = aimYMm
        releaseHandXMm = handXMm
        releaseHandYMm = handYMm
        flightResult = BoardGeometry.score(targetXMm, targetYMm)
        val maxTilt = GameConfig.stuckDartMaxTiltDeg
        flightTiltDeg = random.nextFloat() * 2 * maxTilt - maxTilt
        dragXMm = 0f
        dragYMm = 0f
        enter(Phase.FLIGHT)
    }

    /** Začne tú istú hru odznova. */
    fun restart() {
        rules = type.createRules().apply { startTurn() }
        darts.clear()
        round = 1
        totalDarts = 0
        dartsLanded = 0
        lastResult = null
        isNewRecord = false
        record = records.best(type, difficulty)
        aiming = false
        dragXMm = 0f
        dragYMm = 0f
        flightResult = null
        startReturn()
    }

    private fun land() {
        val result = flightResult ?: return
        darts += StuckDart(targetXMm, targetYMm, flightTiltDeg, result)
        lastResult = result
        dartsLanded += 1
        totalDarts += 1
        flightResult = null
        when (rules.onDart(result)) {
            DartOutcome.FINISHED -> {
                submitRecord(totalDarts)
                enter(Phase.GAME_OVER)
            }
            DartOutcome.BUST -> endTurn()
            DartOutcome.CONTINUE -> if (dartsLanded >= GameConfig.dartsPerRound) endTurn() else startReturn()
        }
    }

    private fun endTurn() {
        rules.turnRecordValue?.let { submitRecord(it) }
        enter(Phase.ROUND_SUMMARY)
    }

    private fun submitRecord(value: Int) {
        isNewRecord = records.submit(type, difficulty, value)
        record = records.best(type, difficulty)
    }

    private fun startReturn() {
        sway.newDart()
        enter(Phase.RETURNING)
    }

    private fun enter(newPhase: Phase) {
        phase = newPhase
        phaseTime = 0f
    }
}
