package sk.sipky.darts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SwayTest {

    @Test
    fun sameSeedGivesSameMotion() {
        val a = Sway(seed = 42)
        val b = Sway(seed = 42)
        repeat(3) {
            for (i in 0..500) {
                val t = i * 0.017
                assertEquals(a.x(t), b.x(t), 0f)
                assertEquals(a.y(t), b.y(t), 0f)
            }
            a.newDart()
            b.newDart()
        }
    }

    @Test
    fun differentSeedsDiffer() {
        assertNotEquals(Sway(seed = 1).x(0.5), Sway(seed = 2).x(0.5))
    }

    @Test
    fun staysWithinAmplitude() {
        val amplitude = 35f
        val sway = Sway(seed = 7, amplitudeMm = amplitude)
        var maxX = 0f
        var maxY = 0f
        repeat(20) {
            for (i in 0..2000) {
                val t = i * 0.01
                maxX = maxOf(maxX, abs(sway.x(t)))
                maxY = maxOf(maxY, abs(sway.y(t)))
            }
            sway.newDart()
        }
        assertTrue("x $maxX", maxX <= amplitude + 1e-3f)
        assertTrue("y $maxY", maxY <= amplitude + 1e-3f)
        // A trasenie je naozaj citeľné
        assertTrue(maxX > amplitude * 0.5f)
        assertTrue(maxY > amplitude * 0.5f)
    }

    @Test
    fun levelsLimitAmplitudeAndEasyIsAlmostStill() {
        for (difficulty in Difficulty.entries) {
            val sway = Sway(seed = 13)
            sway.setLevel(difficulty.sway)
            var max = 0f
            for (i in 0..3000) {
                val t = i * 0.01
                max = maxOf(max, abs(sway.x(t)), abs(sway.y(t)))
            }
            assertTrue("$difficulty $max", max <= difficulty.sway.amplitudeMm + 1e-3f)
        }
        assertTrue(Difficulty.EASY.sway.amplitudeMm <= 5f)
        assertTrue(Difficulty.EASY.sway.amplitudeMm < Difficulty.MEDIUM.sway.amplitudeMm)
        assertTrue(Difficulty.MEDIUM.sway.amplitudeMm < Difficulty.HARD.sway.amplitudeMm)
        assertEquals(GameConfig.swayAmplitudeMm, Difficulty.HARD.sway.amplitudeMm, 0f)
        assertEquals(1f, Difficulty.HARD.sway.freqScale, 0f)
    }

    @Test
    fun lowerFreqScaleMovesSlower() {
        val fast = Sway(seed = 21).apply { setLevel(SwayLevel(35f, 1f)) }
        val slow = Sway(seed = 21).apply { setLevel(SwayLevel(35f, 0.35f)) }
        var fastPath = 0f
        var slowPath = 0f
        for (i in 1..1000) {
            val t0 = (i - 1) * 0.01
            val t1 = i * 0.01
            fastPath += abs(fast.x(t1) - fast.x(t0))
            slowPath += abs(slow.x(t1) - slow.x(t0))
        }
        assertTrue("slow $slowPath fast $fastPath", slowPath < fastPath * 0.6f)
    }

    @Test
    fun newDartChangesPhase() {
        val sway = Sway(seed = 3)
        val before = sway.x(1.0)
        sway.newDart()
        assertNotEquals(before, sway.x(1.0))
    }
}
