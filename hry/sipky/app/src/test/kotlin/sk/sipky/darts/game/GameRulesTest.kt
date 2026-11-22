package sk.sipky.darts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {

    private fun single(n: Int) = ThrowResult(n, 1, n, HitType.SINGLE)
    private fun double(n: Int) = ThrowResult(n * 2, 2, n, HitType.DOUBLE)
    private fun triple(n: Int) = ThrowResult(n * 3, 3, n, HitType.TRIPLE)
    private val outerBull = ThrowResult(25, 1, 0, HitType.OUTER_BULL)
    private val bull = ThrowResult(50, 1, 0, HitType.BULL)
    private val miss = ThrowResult(0, 0, 0, HitType.MISS)

    @Test
    fun x01CountsDownAndFinishesExactly() {
        val rules = X01Rules(301).apply { startTurn() }
        assertEquals(DartOutcome.CONTINUE, rules.onDart(triple(20)))
        assertEquals(241, rules.remaining)
        assertEquals(60, rules.turnPoints)
        repeat(4) { rules.startTurn(); rules.onDart(triple(20)) } // 241 - 240 = 1
        assertEquals(1, rules.remaining)
        rules.startTurn()
        assertEquals(DartOutcome.FINISHED, rules.onDart(single(1)))
        assertEquals(0, rules.remaining)
    }

    @Test
    fun x01BustRestoresTurnStart() {
        val rules = X01Rules(301).apply { startTurn() }
        rules.onDart(triple(20))
        rules.onDart(triple(20))
        rules.onDart(triple(20)) // 121
        rules.startTurn()
        rules.onDart(triple(20)) // 61
        assertEquals(DartOutcome.BUST, rules.onDart(triple(19).copy(points = 62))) // nad zostatok
        assertTrue(rules.busted)
        assertEquals(121, rules.remaining)
        assertEquals(0, rules.turnPoints)
        rules.startTurn()
        assertFalse(rules.busted)
        assertEquals(DartOutcome.CONTINUE, rules.onDart(miss))
        assertEquals(121, rules.remaining)
    }

    @Test
    fun x01AllowsAnyFinishingDart() {
        val rules = X01Rules(301).apply { startTurn() }
        repeat(5) { rules.onDart(triple(20)) } // 1
        assertEquals(DartOutcome.BUST, rules.onDart(double(1)))
        assertEquals(301, rules.remaining)
    }

    @Test
    fun roundaboutTargetCounts() {
        assertEquals(21, RoundaboutRules.targetsFor(RoundaboutVariant.ANY).size)
        assertEquals(42, RoundaboutRules.targetsFor(RoundaboutVariant.MULTIPLE).size)
        assertEquals(62, RoundaboutRules.targetsFor(RoundaboutVariant.DOUBLE_TRIPLE).size)
    }

    @Test
    fun roundaboutAnyAcceptsEveryRingAndBullAtTheEnd() {
        val rules = RoundaboutRules(RoundaboutVariant.ANY)
        rules.startTurn()
        assertEquals(DartOutcome.CONTINUE, rules.onDart(single(2)))
        assertEquals(0, rules.targetIndex) // musí byť najprv 1
        rules.onDart(triple(1))
        rules.onDart(double(2))
        assertEquals(2, rules.targetIndex)
        assertEquals(2, rules.turnPoints)
        for (n in 3..20) rules.onDart(single(n))
        assertEquals("25", rules.currentTarget?.label)
        assertEquals(DartOutcome.FINISHED, rules.onDart(bull))
        assertNull(rules.currentTarget)
    }

    @Test
    fun roundaboutMultipleNeedsSinglesThenDoubleOrTriple() {
        val rules = RoundaboutRules(RoundaboutVariant.MULTIPLE)
        rules.onDart(double(1))
        assertEquals(0, rules.targetIndex) // v prvej fáze platí len single
        for (n in 1..20) rules.onDart(single(n))
        assertEquals("D/T 1", rules.currentTarget?.label)
        rules.onDart(single(1))
        assertEquals(20, rules.targetIndex)
        rules.onDart(triple(1))
        rules.onDart(double(2))
        for (n in 3..20) rules.onDart(triple(n))
        assertEquals("25", rules.currentTarget?.label)
        rules.onDart(bull)
        assertEquals("25", rules.currentTarget?.label) // 25 musí byť outer bull
        rules.onDart(outerBull)
        assertEquals("50", rules.currentTarget?.label)
        assertEquals(DartOutcome.FINISHED, rules.onDart(bull))
    }

    @Test
    fun roundaboutDoubleTripleOrder() {
        val rules = RoundaboutRules(RoundaboutVariant.DOUBLE_TRIPLE)
        for (n in 1..20) rules.onDart(single(n))
        assertEquals("D1", rules.currentTarget?.label)
        rules.onDart(triple(1))
        assertEquals("D1", rules.currentTarget?.label)
        for (n in 1..20) rules.onDart(double(n))
        assertEquals("T1", rules.currentTarget?.label)
        rules.onDart(double(1))
        assertEquals("T1", rules.currentTarget?.label)
        for (n in 1..20) rules.onDart(triple(n))
        rules.onDart(outerBull)
        assertEquals(DartOutcome.FINISHED, rules.onDart(bull))
    }

    @Test
    fun recordsPerTypeAndDifficulty() {
        val book = RecordBook(InMemoryRecordStore())
        val x01 = GameType.X01(501)
        assertTrue(book.submit(x01, Difficulty.HARD, 30))
        assertFalse(book.submit(x01, Difficulty.HARD, 35))
        assertTrue(book.submit(x01, Difficulty.HARD, 25))
        assertEquals(25, book.best(x01, Difficulty.HARD))
        assertNull(book.best(x01, Difficulty.EASY))
        assertNull(book.best(GameType.X01(301), Difficulty.HARD))

        assertFalse(book.submit(GameType.Training, Difficulty.EASY, 0))
        assertTrue(book.submit(GameType.Training, Difficulty.EASY, 60))
        assertFalse(book.submit(GameType.Training, Difficulty.EASY, 40))
        assertTrue(book.submit(GameType.Training, Difficulty.EASY, 180))
        assertEquals(180, book.best(GameType.Training, Difficulty.EASY))
    }
}
