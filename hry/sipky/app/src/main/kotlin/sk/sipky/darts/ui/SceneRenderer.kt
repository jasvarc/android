package sk.sipky.darts.ui

import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.withTranslation
import sk.sipky.darts.game.BoardGeometry
import sk.sipky.darts.game.GameConfig
import sk.sipky.darts.game.GameState
import sk.sipky.darts.game.Phase
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Kreslí scénu: terč, zapichnuté šípky, ruku, letiacu šípku a zameriavač.
 *
 * Všetky cesty a štetce sa pripravia vopred; statický terč sa vykreslí raz do bitmapy
 * pri zmene veľkosti, takže snímok neobsahuje žiadne alokácie.
 */
class SceneRenderer {

    // --- Layout (px), prepočíta sa pri zmene veľkosti plochy ---
    private var width = 0f
    private var height = 0f
    private var boardCx = 0f
    private var boardCy = 0f

    /** Koľko pixelov na obrazovke zodpovedá 1 mm terča. */
    var pxPerMm = 0f
        private set
    private var handRestX = 0f
    private var handRestY = 0f
    private var handScale = 1f
    private var stuckScale = 1f
    private var handDropPx = 0f
    private var arcHeightPx = 0f
    private var crosshairRadiusPx = 0f
    private var crosshairLine = Stroke(2f)
    private var crosshairOutline = Stroke(5f)
    private var boardBitmap: ImageBitmap? = null

    // --- Terč (v mm) ---
    private val wedges: List<Pair<Path, Color>> = buildList {
        val rings = listOf(
            BoardGeometry.OUTER_BULL_RADIUS to BoardGeometry.TRIPLE_INNER_RADIUS,
            BoardGeometry.TRIPLE_INNER_RADIUS to BoardGeometry.TRIPLE_OUTER_RADIUS,
            BoardGeometry.TRIPLE_OUTER_RADIUS to BoardGeometry.DOUBLE_INNER_RADIUS,
            BoardGeometry.DOUBLE_INNER_RADIUS to BoardGeometry.DOUBLE_OUTER_RADIUS,
        )
        for (i in BoardGeometry.SECTORS.indices) {
            val dark = i % 2 == 0
            rings.forEachIndexed { ringIndex, (inner, outer) ->
                val scoring = ringIndex == 1 || ringIndex == 3
                val color = when {
                    scoring && dark -> BOARD_RED
                    scoring -> BOARD_GREEN
                    dark -> BOARD_BLACK
                    else -> BOARD_CREAM
                }
                add(wedge(inner, outer, i) to color)
            }
        }
    }
    private val wireRadii = floatArrayOf(
        BoardGeometry.BULL_RADIUS,
        BoardGeometry.OUTER_BULL_RADIUS,
        BoardGeometry.TRIPLE_INNER_RADIUS,
        BoardGeometry.TRIPLE_OUTER_RADIUS,
        BoardGeometry.DOUBLE_INNER_RADIUS,
        BoardGeometry.DOUBLE_OUTER_RADIUS,
    )
    private val wireStroke = Stroke(0.9f)
    private val rimStroke = Stroke(3f)
    private val numberLabels = Array(BoardGeometry.SECTORS.size) { BoardGeometry.SECTORS[it].toString() }
    private val numberPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F2F2.toInt()
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    // --- Šípka v lokálnych súradniciach: hrot v (0,0), smeruje nahor, chvost v (0, DART_LENGTH) ---
    private val needle = Path().apply {
        moveTo(0f, 0f)
        lineTo(1.4f, 5f)
        lineTo(1.4f, 24f)
        lineTo(-1.4f, 24f)
        lineTo(-1.4f, 5f)
        close()
    }
    private val barrel = Path().apply {
        addRoundRect(RoundRect(-5f, 24f, 5f, 72f, CornerRadius(4f)))
    }
    private val grip = Path().apply {
        var y = 34f
        while (y <= 62f) {
            moveTo(-5f, y)
            lineTo(5f, y)
            y += 4f
        }
    }
    private val shaft = Path().apply { addRect(Rect(-2f, 72f, 2f, 112f)) }
    private val flight = Path().apply {
        moveTo(0f, 100f)
        lineTo(14f, 126f)
        lineTo(12f, DART_LENGTH)
        lineTo(0f, 144f)
        lineTo(-12f, DART_LENGTH)
        lineTo(-14f, 126f)
        close()
    }
    private val flightSpine = Path().apply {
        moveTo(0f, 102f)
        lineTo(0f, 144f)
    }
    private val gripStroke = Stroke(1.3f)
    private val spineStroke = Stroke(1.6f)

    // --- Ruka v rovnakých lokálnych súradniciach ako šípka v nej ---
    private val forearm = Path().apply {
        moveTo(4f, 80f)
        lineTo(52f, 66f)
        lineTo(124f, 340f)
        lineTo(28f, 352f)
        close()
    }
    private val sleeve = Path().apply {
        moveTo(12f, 196f)
        lineTo(86f, 180f)
        lineTo(136f, 360f)
        lineTo(22f, 372f)
        close()
    }
    private val palm = Path().apply { addOval(Rect(-12f, 38f, 56f, 112f)) }
    private val thumb = capsule(-20f, 86f, -6f, 46f, 8.5f)
    private val fingers = listOf(
        capsule(36f, 88f, 8f, 86f, 7f),
        capsule(40f, 72f, 4f, 68f, 8f),
        capsule(40f, 56f, 0f, 50f, 8.5f),
        capsule(34f, 40f, -2f, 34f, 8f),
    )
    private val skinStroke = Stroke(2.2f)

    /** Prepočíta rozloženie scény; pri nezmenenej veľkosti nerobí nič. */
    fun updateLayout(size: Size, density: Density) {
        if (size.width == width && size.height == height && boardBitmap != null) return
        if (size.width <= 0f || size.height <= 0f) return
        width = size.width
        height = size.height

        val minHandArea = height * 0.27f
        val boardRadiusPx = min(width * 0.48f, (height - minHandArea) / 2f)
        pxPerMm = boardRadiusPx / BoardGeometry.BOARD_RADIUS
        boardCx = width / 2f
        boardCy = boardRadiusPx + height * 0.01f

        val boardBottom = boardCy + boardRadiusPx
        val handArea = height - boardBottom
        handRestX = width * 0.5f
        handRestY = boardBottom + handArea * 0.22f
        handScale = min(handArea * 0.62f / DART_LENGTH, width * 0.004f)
        stuckScale = STUCK_DART_LENGTH_MM * pxPerMm / DART_LENGTH
        handDropPx = handArea
        arcHeightPx = height * GameConfig.arcHeightFraction

        crosshairRadiusPx = GameConfig.crosshairDiameterMm / 2f * pxPerMm
        val line = (0.9f * pxPerMm).coerceAtLeast(density.density * 1.5f)
        crosshairLine = Stroke(line)
        crosshairOutline = Stroke(line + density.density * 2.5f)

        boardBitmap = renderBoard(density, boardRadiusPx)
    }

    fun draw(scope: DrawScope, game: GameState) = with(scope) {
        // Čítanie času napojí kreslenie na hernú slučku – prekresľuje sa každý snímok.
        game.timeSeconds
        val bitmap = boardBitmap ?: return@with
        drawImage(bitmap, Offset(boardCx - bitmap.width / 2f, boardCy - bitmap.height / 2f))

        for (dart in game.stuckDarts) {
            drawDart(boardX(dart.xMm), boardY(dart.yMm), 180f + dart.tiltDeg, stuckScale)
        }

        when (game.phase) {
            Phase.READY -> {
                drawHand(game, drop = 0f, withDart = true)
                drawCrosshair(boardX(game.aimXMm), boardY(game.aimYMm))
            }
            Phase.FLIGHT -> {
                val p = game.flightProgress
                drawHand(game, drop = smoothstep(p) * handDropPx, withDart = false)
                drawFlyingDart(game, p)
            }
            Phase.RETURNING -> {
                val p = game.returnProgress
                val easeOut = 1f - (1f - p) * (1f - p)
                drawHand(game, drop = (1f - easeOut) * handDropPx, withDart = true)
            }
            Phase.ROUND_SUMMARY -> drawHand(game, drop = handDropPx, withDart = false)
        }
    }

    private fun boardX(xMm: Float) = boardCx + xMm * pxPerMm
    private fun boardY(yMm: Float) = boardCy + yMm * pxPerMm

    private fun DrawScope.drawFlyingDart(game: GameState, p: Float) {
        // Ľahké zrýchlenie na začiatku a spomalenie na konci
        val e = 0.5f * p + 0.5f * smoothstep(p)
        val sx = handRestX + game.releaseHandXMm * pxPerMm
        val sy = handRestY + game.releaseHandYMm * pxPerMm
        val ex = boardX(game.targetXMm)
        val ey = boardY(game.targetYMm)
        val arc = arcHeightPx * 4f * e * (1f - e)
        val x = sx + (ex - sx) * e
        val y = sy + (ey - sy) * e - arc

        val dx = ex - sx
        val dy = (ey - sy) - arcHeightPx * 4f * (1f - 2f * e)
        val tangent = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
        val settle = smoothstep(((p - 0.7f) / 0.3f).coerceIn(0f, 1f))
        val rotation = lerpAngle(tangent, 180f + game.flightTiltDeg, settle)
        // Perspektíva: šípka sa vzďaľuje, mierka klesá od ruky k veľkosti v terči
        val scale = handScale / (1f + e * (handScale / stuckScale - 1f))
        drawDart(x, y, rotation, scale)
    }

    private fun DrawScope.drawDart(x: Float, y: Float, rotationDeg: Float, scale: Float) {
        withTransform({
            translate(x, y)
            rotate(rotationDeg, Offset.Zero)
            scale(scale, scale, Offset.Zero)
        }) {
            drawDartLocal()
        }
    }

    private fun DrawScope.drawDartLocal() {
        drawPath(flight, FLIGHT_RED)
        drawPath(flightSpine, FLIGHT_SPINE, style = spineStroke)
        drawPath(shaft, SHAFT)
        drawPath(barrel, BARREL)
        drawPath(grip, GRIP, style = gripStroke)
        drawPath(needle, NEEDLE)
    }

    private fun DrawScope.drawHand(game: GameState, drop: Float, withDart: Boolean) {
        val x = handRestX + game.handXMm * pxPerMm
        val y = handRestY + game.handYMm * pxPerMm + drop
        val rotation = game.handXMm * 0.12f
        withTransform({
            translate(x, y)
            rotate(rotation, Offset.Zero)
            scale(handScale, handScale, Offset.Zero)
        }) {
            drawPath(forearm, SKIN_SHADE)
            drawPath(forearm, SKIN_OUTLINE, style = skinStroke)
            drawPath(sleeve, SLEEVE)
            drawPath(sleeve, SLEEVE_OUTLINE, style = skinStroke)
            drawPath(palm, SKIN)
            drawPath(palm, SKIN_OUTLINE, style = skinStroke)
            if (withDart) drawDartLocal()
            drawPath(thumb, SKIN)
            drawPath(thumb, SKIN_OUTLINE, style = skinStroke)
            for (finger in fingers) {
                drawPath(finger, SKIN)
                drawPath(finger, SKIN_OUTLINE, style = skinStroke)
            }
        }
    }

    private fun DrawScope.drawCrosshair(cx: Float, cy: Float) {
        val center = Offset(cx, cy)
        val r = crosshairRadiusPx
        drawCrosshairPass(center, r, CROSSHAIR_OUTLINE, crosshairOutline)
        drawCrosshairPass(center, r, CROSSHAIR, crosshairLine)
    }

    private fun DrawScope.drawCrosshairPass(c: Offset, r: Float, color: Color, stroke: Stroke) {
        val inner = r * 0.45f
        val outer = r * 1.8f
        drawCircle(color, r, c, style = stroke)
        drawLine(color, Offset(c.x - outer, c.y), Offset(c.x - inner, c.y), stroke.width)
        drawLine(color, Offset(c.x + inner, c.y), Offset(c.x + outer, c.y), stroke.width)
        drawLine(color, Offset(c.x, c.y - outer), Offset(c.x, c.y - inner), stroke.width)
        drawLine(color, Offset(c.x, c.y + inner), Offset(c.x, c.y + outer), stroke.width)
    }

    private fun renderBoard(density: Density, radiusPx: Float): ImageBitmap {
        val sizePx = ceil(radiusPx * 2f).toInt().coerceAtLeast(1)
        val bitmap = ImageBitmap(sizePx, sizePx)
        val c = sizePx / 2f
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(sizePx.toFloat(), sizePx.toFloat())) {
            withTransform({
                translate(c, c)
                scale(pxPerMm, pxPerMm, Offset.Zero)
            }) {
                drawCircle(NUMBER_RING, BoardGeometry.BOARD_RADIUS, Offset.Zero)
                for ((path, color) in wedges) drawPath(path, color)
                drawCircle(BOARD_GREEN, BoardGeometry.OUTER_BULL_RADIUS, Offset.Zero)
                drawCircle(BOARD_RED, BoardGeometry.BULL_RADIUS, Offset.Zero)
                for (r in wireRadii) drawCircle(WIRE, r, Offset.Zero, style = wireStroke)
                for (i in BoardGeometry.SECTORS.indices) {
                    val rad = Math.toRadians(i * BoardGeometry.SECTOR_DEGREES - BoardGeometry.SECTOR_DEGREES / 2)
                    val sx = sin(rad).toFloat()
                    val cy = -cos(rad).toFloat()
                    drawLine(
                        WIRE,
                        Offset(sx * BoardGeometry.OUTER_BULL_RADIUS, cy * BoardGeometry.OUTER_BULL_RADIUS),
                        Offset(sx * BoardGeometry.DOUBLE_OUTER_RADIUS, cy * BoardGeometry.DOUBLE_OUTER_RADIUS),
                        wireStroke.width,
                    )
                }
                drawCircle(RIM, BoardGeometry.BOARD_RADIUS - 1.5f, Offset.Zero, style = rimStroke)
            }
            drawNumbers(drawContext.canvas.nativeCanvas, c)
        }
        return bitmap
    }

    private fun drawNumbers(canvas: android.graphics.Canvas, c: Float) {
        numberPaint.textSize = NUMBER_SIZE_MM * pxPerMm
        val metrics = numberPaint.fontMetrics
        val baseline = -(metrics.ascent + metrics.descent) / 2f
        val radius = NUMBER_RADIUS_MM * pxPerMm
        for (i in numberLabels.indices) {
            val angle = i * BoardGeometry.SECTOR_DEGREES
            val rad = Math.toRadians(angle)
            // Čísla sú otočené po obvode, v dolnej polovici obrátene, aby sa dali čítať.
            val rotation = if (angle > 90.0 && angle < 270.0) angle + 180.0 else angle
            canvas.withTranslation(c + (sin(rad) * radius).toFloat(), c - (cos(rad) * radius).toFloat()) {
                rotate(rotation.toFloat())
                drawText(numberLabels[i], 0f, baseline, numberPaint)
            }
        }
    }

    private companion object {
        const val DART_LENGTH = 150f
        const val STUCK_DART_LENGTH_MM = 48f
        const val NUMBER_SIZE_MM = 30f
        const val NUMBER_RADIUS_MM = 197f

        val BOARD_BLACK = Color(0xFF151515)
        val BOARD_CREAM = Color(0xFFEAD9B0)
        val BOARD_RED = Color(0xFFC8102E)
        val BOARD_GREEN = Color(0xFF0F7B3F)
        val NUMBER_RING = Color(0xFF0B0B0B)
        val WIRE = Color(0xFFC9CCD1)
        val RIM = Color(0xFF3A3A3A)

        val FLIGHT_RED = Color(0xFFE63946)
        val FLIGHT_SPINE = Color(0xFFFFFFFF)
        val SHAFT = Color(0xFF2B2B2B)
        val BARREL = Color(0xFFB8BCC4)
        val GRIP = Color(0xFF6E737C)
        val NEEDLE = Color(0xFFDADDE2)

        val SKIN = Color(0xFFE9B38C)
        val SKIN_SHADE = Color(0xFFD59A73)
        val SKIN_OUTLINE = Color(0xFF8E5A3C)
        val SLEEVE = Color(0xFF2E4A7A)
        val SLEEVE_OUTLINE = Color(0xFF1B2D4D)

        val CROSSHAIR = Color.White
        val CROSSHAIR_OUTLINE = Color(0xE6000000)

        fun smoothstep(t: Float) = t * t * (3f - 2f * t)

        fun lerpAngle(from: Float, to: Float, f: Float): Float {
            val diff = ((to - from) % 360f + 540f) % 360f - 180f
            return from + diff * f
        }

        /** Výsek prstenca [inner, outer] pre sektor s indexom [index] (mm). */
        fun wedge(inner: Float, outer: Float, index: Int): Path {
            // Uhly v Compose sa merajú od osi x, uhly terča od vrchu.
            val start = (index * BoardGeometry.SECTOR_DEGREES - BoardGeometry.SECTOR_DEGREES / 2 - 90.0).toFloat()
            val sweep = BoardGeometry.SECTOR_DEGREES.toFloat()
            return Path().apply {
                arcTo(Rect(Offset.Zero, outer), start, sweep, true)
                arcTo(Rect(Offset.Zero, inner), start + sweep, -sweep, false)
                close()
            }
        }

        /** Kapsula (prst) z bodu a do bodu b s polomerom r. */
        fun capsule(ax: Float, ay: Float, bx: Float, by: Float, r: Float): Path {
            val len = hypot(bx - ax, by - ay)
            val dx = (bx - ax) / len
            val dy = (by - ay) / len
            val nx = -dy
            val ny = dx
            val theta = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            return Path().apply {
                moveTo(ax + nx * r, ay + ny * r)
                lineTo(bx + nx * r, by + ny * r)
                arcTo(Rect(Offset(bx, by), r), theta + 90f, -180f, false)
                lineTo(ax - nx * r, ay - ny * r)
                arcTo(Rect(Offset(ax, ay), r), theta - 90f, -180f, false)
                close()
            }
        }
    }
}
