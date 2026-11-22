package sk.sipky.darts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {

    /** Hodí šípku s posunom ([dx], [dy]) mm a nechá ju dopadnúť. Vráti bod, kam hráč mieril. */
    private fun GameState.throwDart(dx: Float = 0f, dy: Float = 0f): Pair<Float, Float> {
        while (phase != Phase.READY) update(0.016f)
        assertTrue(onPointerDown())
        onPointerMove(dx, dy)
        update(0.016f)
        val aim = aimXMm to aimYMm
        onPointerUp()
        assertEquals(Phase.FLIGHT, phase)
        var landed = false
        while (phase == Phase.FLIGHT) landed = update(0.016f) || landed
        assertTrue(landed)
        return aim
    }

    private val GameState.trainingTotal get() = (rules as TrainingRules).total

    @Test
    fun dartLandsExactlyAtCrosshair() {
        val game = GameState(seed = 11)
        val (x, y) = game.throwDart(dx = 0f, dy = -103f)
        val dart = game.stuckDarts.single()
        assertEquals(x, dart.xMm, 0f)
        assertEquals(y, dart.yMm, 0f)
        assertEquals(BoardGeometry.score(x, y), dart.result)
        assertEquals(dart.result.points, game.trainingTotal)
        assertEquals(dart.result, game.lastResult)
    }

    @Test
    fun dragResetsAfterThrowAndIsClamped() {
        val game = GameState(seed = 5)
        assertTrue(game.onPointerDown())
        game.onPointerMove(10_000f, -10_000f)
        assertEquals(GameConfig.maxDragOffsetMm, game.dragXMm, 0f)
        assertEquals(-GameConfig.maxDragOffsetMm, game.dragYMm, 0f)
        game.onPointerUp()
        assertEquals(0f, game.dragXMm, 0f)
        assertEquals(0f, game.dragYMm, 0f)
    }

    @Test
    fun inputIgnoredDuringFlight() {
        val game = GameState(seed = 9)
        game.onPointerDown()
        game.onPointerUp()
        assertEquals(Phase.FLIGHT, game.phase)
        assertFalse(game.onPointerDown())
        game.onPointerMove(50f, 50f)
        assertEquals(0f, game.dragXMm, 0f)
    }

    @Test
    fun easyDifficultyBarelyMoves() {
        val game = GameState(seed = 4, difficulty = Difficulty.EASY)
        repeat(300) {
            game.update(0.016f)
            assertTrue(kotlin.math.abs(game.aimXMm) <= Difficulty.EASY.sway.amplitudeMm + 1e-3f)
        }
    }

    @Test
    fun trainingRoundOfThreeDartsThenNextRoundAndRecord() {
        val records = RecordBook(InMemoryRecordStore())
        val game = GameState(seed = 1, difficulty = Difficulty.EASY, records = records)
        repeat(3) { game.throwDart() } // ľahká úroveň a stred = 3× bull
        assertEquals(Phase.ROUND_SUMMARY, game.phase)
        assertEquals(3, game.stuckDarts.size)
        assertEquals(150, game.trainingTotal)
        assertEquals(150, game.rules.turnPoints)
        assertTrue(game.isNewRecord)
        assertEquals(150, game.record)

        game.update(GameConfig.roundSummarySeconds + 0.01f)
        assertEquals(2, game.round)
        assertEquals(0, game.stuckDarts.size)
        assertEquals(1, game.dartNumber)
        assertEquals(0, game.rules.turnPoints)
        assertEquals(150, game.trainingTotal)

        game.restart()
        assertEquals(0, game.trainingTotal)
        assertEquals(1, game.round)
        assertEquals(150, game.record)
    }

    @Test
    fun x01FinishesOnExactZeroAndStoresFewestDarts() {
        val records = RecordBook(InMemoryRecordStore())
        val type = GameType.X01(100)
        val game = GameState(seed = 2, type = type, difficulty = Difficulty.EASY, records = records)
        assertNull(game.record)
        game.throwDart()
        assertEquals(50, (game.rules as X01Rules).remaining)
        game.throwDart()
        assertEquals(Phase.GAME_OVER, game.phase)
        assertEquals(2, game.totalDarts)
        assertTrue(game.isNewRecord)
        assertEquals(2, records.best(type, Difficulty.EASY))
        assertNull(records.best(type, Difficulty.HARD))
        // Po konci hry sa vstup ignoruje
        game.update(5f)
        assertFalse(game.onPointerDown())
    }

    @Test
    fun x01BustEndsTurnEarly() {
        val game = GameState(seed = 3, type = GameType.X01(70), difficulty = Difficulty.EASY)
        game.throwDart() // bull: 70 -> 20
        game.throwDart() // bull: prestrelenie
        assertEquals(Phase.ROUND_SUMMARY, game.phase)
        val rules = game.rules as X01Rules
        assertTrue(rules.busted)
        assertEquals(70, rules.remaining)
        game.update(GameConfig.roundSummarySeconds + 0.01f)
        assertEquals(2, game.round)
        assertFalse(rules.busted)
        assertEquals(2, game.totalDarts)
    }
}
