package sk.sipky.darts.game

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Trasenie ruky: pre každú os súčet dvoch sínusoviek s nesúmernými frekvenciami.
 * Fázy sa náhodne zvolia pri každej novej šípke ([newDart]), takže každý hod sa trasie inak.
 * Pri rovnakom seede je priebeh deterministický.
 */
class Sway(
    seed: Long,
    private var amplitudeMm: Float = GameConfig.swayAmplitudeMm,
    private val freqX: FloatArray = GameConfig.swayFreqX,
    private val freqY: FloatArray = GameConfig.swayFreqY,
    weights: FloatArray = GameConfig.swayWeights,
) {
    private val random = Random(seed)
    private val phases = DoubleArray(4)
    private val w0: Double
    private val w1: Double
    private var freqScale = 1.0

    init {
        val sum = (weights[0] + weights[1]).toDouble()
        w0 = weights[0] / sum
        w1 = weights[1] / sum
        newDart()
    }

    /** Zmení silu trasenia (napr. pri zmene náročnosti). */
    fun setLevel(level: SwayLevel) {
        amplitudeMm = level.amplitudeMm
        freqScale = level.freqScale.toDouble()
    }

    fun newDart() {
        for (i in phases.indices) phases[i] = random.nextDouble() * 2 * PI
    }

    fun x(timeSeconds: Double): Float = component(timeSeconds, freqX, phases[0], phases[1])

    fun y(timeSeconds: Double): Float = component(timeSeconds, freqY, phases[2], phases[3])

    private fun component(t: Double, freq: FloatArray, p0: Double, p1: Double): Float {
        val w = 2 * PI * freqScale * t
        val v = w0 * sin(w * freq[0] + p0) + w1 * sin(w * freq[1] + p1)
        return (amplitudeMm * v).toFloat()
    }
}
