package sk.sipky.darts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun dartLandsExactlyAtCrosshair() {
        val game = GameState(seed = 11)
        val (x, y) = game.throwDart(dx = 0f, dy = -103f)
        val dart = game.stuckDarts.single()
        assertEquals(x, dart.xMm, 0f)
        assertEquals(y, dart.yMm, 0f)
        assertEquals(BoardGeometry.score(x, y), dart.result)
        assertEquals(dart.result.points, game.score)
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
    fun changingDifficultyStartsNewGame() {
        val game = GameState(seed = 4)
        assertEquals(GameConfig.defaultDifficulty, game.difficulty)
        game.throwDart()
        game.changeDifficulty(Difficulty.EASY)
        assertEquals(Difficulty.EASY, game.difficulty)
        assertEquals(0, game.score)
        assertEquals(0, game.stuckDarts.size)
        // Na ľahkej úrovni sa zameriavač takmer nehýbe
        repeat(300) {
            game.update(0.016f)
            assertTrue(kotlin.math.abs(game.aimXMm) <= Difficulty.EASY.sway.amplitudeMm + 1e-3f)
        }
    }

    @Test
    fun roundOfThreeDartsThenNextRound() {
        val game = GameState(seed = 1)
        repeat(3) { game.throwDart() }
        assertEquals(Phase.ROUND_SUMMARY, game.phase)
        assertEquals(3, game.stuckDarts.size)
        assertEquals(game.score, game.roundPoints)
        val scoreAfterRound = game.score

        game.update(GameConfig.roundSummarySeconds + 0.01f)
        assertEquals(2, game.round)
        assertEquals(0, game.stuckDarts.size)
        assertEquals(1, game.dartNumber)
        assertEquals(scoreAfterRound, game.score)

        game.newGame()
        assertEquals(0, game.score)
        assertEquals(1, game.round)
    }
}
