package sk.sipky.darts.game

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BoardGeometryTest {

    /** Bod na polomere [r] mm pod uhlom [deg] v smere hodinových ručičiek od vrchu. */
    private fun polar(r: Double, deg: Double): ThrowResult {
        val rad = Math.toRadians(deg)
        return BoardGeometry.score((r * sin(rad)).toFloat(), (-r * cos(rad)).toFloat())
    }

    private fun assertHit(expected: ThrowResult, actual: ThrowResult) = assertEquals(expected, actual)

    @Test
    fun centerIsBull() {
        val result = BoardGeometry.score(0f, 0f)
        assertEquals(50, result.points)
        assertEquals(HitType.BULL, result.type)
        assertEquals("Bull", result.label)
    }

    @Test
    fun outerBullIs25() {
        val result = BoardGeometry.score(10f, 0f)
        assertEquals(25, result.points)
        assertEquals(HitType.OUTER_BULL, result.type)
        assertEquals("25", result.label)
    }

    @Test
    fun sector20TripleAndDouble() {
        val triple = BoardGeometry.score(0f, -103f)
        assertHit(ThrowResult(60, 3, 20, HitType.TRIPLE), triple)
        assertEquals("T20", triple.label)

        val double = BoardGeometry.score(0f, -166f)
        assertHit(ThrowResult(40, 2, 20, HitType.DOUBLE), double)
        assertEquals("D20", double.label)

        assertHit(ThrowResult(20, 1, 20, HitType.SINGLE), BoardGeometry.score(0f, -50f))
        assertHit(ThrowResult(20, 1, 20, HitType.SINGLE), BoardGeometry.score(0f, -130f))
    }

    @Test
    fun ringBoundaries() {
        val e = 0.01
        // Bull do 6.35 vrátane
        assertEquals(HitType.BULL, polar(6.35 - e, 0.0).type)
        assertEquals(HitType.BULL, BoardGeometry.score(0f, -6.35f).type)
        assertEquals(HitType.OUTER_BULL, polar(6.35 + e, 0.0).type)
        // Outer bull do 15.9 vrátane
        assertEquals(HitType.OUTER_BULL, BoardGeometry.score(0f, -15.9f).type)
        assertEquals(HitType.SINGLE, polar(15.9 + e, 0.0).type)
        // Triple 99–107
        assertEquals(HitType.SINGLE, polar(99 - e, 0.0).type)
        assertEquals(HitType.TRIPLE, BoardGeometry.score(0f, -99f).type)
        assertEquals(HitType.TRIPLE, BoardGeometry.score(0f, -107f).type)
        assertEquals(HitType.SINGLE, polar(107 + e, 0.0).type)
        // Double 162–170
        assertEquals(HitType.SINGLE, polar(162 - e, 0.0).type)
        assertEquals(HitType.DOUBLE, BoardGeometry.score(0f, -162f).type)
        assertEquals(HitType.DOUBLE, BoardGeometry.score(0f, -170f).type)
        assertEquals(HitType.MISS, polar(170 + e, 0.0).type)
    }

    @Test
    fun sectorBoundariesAt9Degrees() {
        val r = 50.0
        assertEquals(20, polar(r, 8.9).sector)
        assertEquals(1, polar(r, 9.1).sector)
        assertEquals(20, polar(r, -8.9).sector)
        assertEquals(5, polar(r, -9.1).sector)
        // Presne na hranici patrí bod do sektora v smere hodinových ručičiek
        assertEquals(1, BoardGeometry.SECTORS[BoardGeometry.sectorIndex(9.0)])
        assertEquals(20, BoardGeometry.SECTORS[BoardGeometry.sectorIndex(351.0)])
    }

    @Test
    fun allSectorsInClockwiseOrder() {
        val expected = listOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)
        val actual = (0 until 20).map { i -> polar(130.0, i * 18.0).sector }
        assertEquals(expected, actual)
        // Aj pri okrajoch každého sektora
        val nearLeftEdge = (0 until 20).map { i -> polar(130.0, i * 18.0 - 8.5).sector }
        val nearRightEdge = (0 until 20).map { i -> polar(130.0, i * 18.0 + 8.5).sector }
        assertEquals(expected, nearLeftEdge)
        assertEquals(expected, nearRightEdge)
    }

    @Test
    fun outsideBoardIsMiss() {
        val result = BoardGeometry.score(0f, -200f)
        assertHit(ThrowResult(0, 0, 0, HitType.MISS), result)
        assertEquals("Mimo", result.label)
        assertEquals(0, BoardGeometry.score(300f, 300f).points)
    }

    @Test
    fun quadrants() {
        assertEquals(6, BoardGeometry.score(130f, 0f).sector) // vpravo
        assertEquals(11, BoardGeometry.score(-130f, 0f).sector) // vľavo
        assertEquals(3, BoardGeometry.score(0f, 130f).sector) // dole
        assertEquals(20, BoardGeometry.score(0f, -130f).sector) // hore
        assertHit(ThrowResult(18, 3, 6, HitType.TRIPLE), BoardGeometry.score(103f, 0f))
        assertHit(ThrowResult(22, 2, 11, HitType.DOUBLE), BoardGeometry.score(-166f, 0f))
        assertEquals("D16", polar(166.0, 13 * 18.0).label)
    }

    @Test
    fun angleIsClockwiseFromTop() {
        assertEquals(0.0, BoardGeometry.angleDegrees(0f, -10f), 1e-9)
        assertEquals(90.0, BoardGeometry.angleDegrees(10f, 0f), 1e-9)
        assertEquals(180.0, BoardGeometry.angleDegrees(0f, 10f), 1e-9)
        assertEquals(270.0, BoardGeometry.angleDegrees(-10f, 0f), 1e-9)
    }
}
