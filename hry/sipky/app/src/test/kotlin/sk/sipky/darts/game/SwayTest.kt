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
    fun newDartChangesPhase() {
        val sway = Sway(seed = 3)
        val before = sway.x(1.0)
        sway.newDart()
        assertNotEquals(before, sway.x(1.0))
    }
}
