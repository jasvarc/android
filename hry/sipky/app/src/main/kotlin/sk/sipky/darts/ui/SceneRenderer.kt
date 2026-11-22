package sk.sipky.darts.ui

import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

    // --- Pravá ruka zozadu v rovnakých lokálnych súradniciach ako šípka v nej ---
    // Šípka je držaná ako ceruzka: palec zľava, ukazovák a prostredník zhora sprava,
    // prstenník a malíček voľne skrčené pod dlaňou.
    private val forearm = Path().apply {
        moveTo(20f, 112f)
        cubicTo(34f, 104f, 54f, 102f, 66f, 108f)
        cubicTo(84f, 180f, 104f, 270f, 124f, 400f)
        lineTo(38f, 410f)
        cubicTo(34f, 300f, 26f, 200f, 20f, 112f)
        close()
    }
    private val sleeve = Path().apply {
        moveTo(26f, 236f)
        cubicTo(48f, 224f, 78f, 220f, 98f, 226f)
        lineTo(132f, 420f)
        lineTo(36f, 425f)
        close()
    }
    private val sleeveCuff = Path().apply {
        moveTo(27f, 246f)
        cubicTo(49f, 234f, 79f, 230f, 100f, 236f)
    }
    private val backOfHand = Path().apply {
        moveTo(6f, 50f)
        cubicTo(18f, 38f, 50f, 38f, 64f, 54f)
        cubicTo(74f, 70f, 72f, 100f, 64f, 120f)
        cubicTo(50f, 126f, 34f, 128f, 20f, 122f)
        cubicTo(8f, 104f, 1f, 76f, 6f, 50f)
        close()
    }

    /** Šľachy a hánky na chrbte ruky (jemné čiary). */
    private val handDetails = Path().apply {
        moveTo(14f, 58f)
        quadraticTo(16f, 52f, 22f, 50f)
        moveTo(29f, 54f)
        quadraticTo(32f, 48f, 38f, 48f)
        moveTo(43f, 57f)
        quadraticTo(46f, 52f, 51f, 53f)
        moveTo(20f, 70f)
        quadraticTo(24f, 92f, 30f, 112f)
        moveTo(36f, 70f)
        quadraticTo(40f, 92f, 44f, 114f)
    }

    /** Prsty pod dlaňou (kreslia sa pred chrbtom ruky). */
    private val tuckedFingers = listOf(
        taperedCapsule(56f, 60f, 7f, 56f, 50f, 6.5f),
        taperedCapsule(56f, 50f, 6.5f, 48f, 62f, 5.5f),
        taperedCapsule(44f, 54f, 8f, 42f, 40f, 7.5f),
        taperedCapsule(42f, 40f, 7.5f, 32f, 50f, 6.5f),
    )
    private val thumb = listOf(
        taperedCapsule(16f, 104f, 12f, 0f, 74f, 9.5f),
        taperedCapsule(0f, 74f, 9.5f, -6f, 50f, 7.5f),
    )

    /** Prostredník a ukazovák cez šípku; posledný článok sa kreslí ako posledný. */
    private val frontFingers = listOf(
        taperedCapsule(30f, 50f, 8.5f, 22f, 32f, 7.8f),
        taperedCapsule(22f, 32f, 7.8f, 8f, 37f, 6.8f),
        taperedCapsule(15f, 52f, 8.8f, 10f, 34f, 8f),
        taperedCapsule(10f, 34f, 8f, 2f, 24f, 6.8f),
    )
    private val nails = Path().apply {
        addOval(Rect(-10f, 45f, -3f, 52f))
        addOval(Rect(-1.5f, 20f, 5f, 26f))
    }
    private val skinBrush = Brush.linearGradient(
        listOf(SKIN_LIGHT, SKIN, SKIN_SHADE),
        start = Offset(-10f, 20f),
        end = Offset(80f, 140f),
    )
    private val forearmBrush = Brush.horizontalGradient(
        listOf(SKIN, SKIN_SHADE),
        startX = 20f,
        endX = 110f,
    )
    private val sleeveBrush = Brush.horizontalGradient(
        listOf(SLEEVE_LIGHT, SLEEVE),
        startX = 30f,
        endX = 130f,
    )
    private val skinStroke = Stroke(1.5f)
    private val detailStroke = Stroke(1.2f)

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
        handRestY = boardBottom + handArea * 0.12f
        handScale = min(handArea * 0.7f / DART_LENGTH, width * 0.0045f)
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
            drawPath(forearm, forearmBrush)
            drawPath(forearm, SKIN_OUTLINE, style = skinStroke)
            drawPath(sleeve, sleeveBrush)
            drawPath(sleeveCuff, SLEEVE_OUTLINE, style = skinStroke)
            drawPath(sleeve, SLEEVE_OUTLINE, style = skinStroke)
            drawSkin(tuckedFingers)
            drawPath(backOfHand, skinBrush)
            drawPath(backOfHand, SKIN_OUTLINE, style = skinStroke)
            drawPath(handDetails, SKIN_CREASE, style = detailStroke)
            if (withDart) drawDartLocal()
            drawSkin(thumb)
            drawSkin(frontFingers)
            drawPath(nails, NAIL)
            drawPath(nails, SKIN_CREASE, style = detailStroke)
        }
    }

    private fun DrawScope.drawSkin(parts: List<Path>) {
        for (part in parts) {
            drawPath(part, skinBrush)
            drawPath(part, SKIN_OUTLINE, style = skinStroke)
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

        val SKIN_LIGHT = Color(0xFFF6CFAF)
        val SKIN = Color(0xFFE8B48F)
        val SKIN_SHADE = Color(0xFFC98E6A)
        val SKIN_OUTLINE = Color(0xB3824F35)
        val SKIN_CREASE = Color(0x669C6142)
        val NAIL = Color(0xFFF7DCCB)
        val SLEEVE_LIGHT = Color(0xFF3D5F95)
        val SLEEVE = Color(0xFF26406B)
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

        /** Zužujúca sa kapsula (článok prsta) z bodu a s polomerom ra do bodu b s polomerom rb. */
        fun taperedCapsule(ax: Float, ay: Float, ra: Float, bx: Float, by: Float, rb: Float): Path {
            val len = hypot(bx - ax, by - ay)
            val dx = (bx - ax) / len
            val dy = (by - ay) / len
            val nx = -dy
            val ny = dx
            val theta = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            return Path().apply {
                moveTo(ax + nx * ra, ay + ny * ra)
                lineTo(bx + nx * rb, by + ny * rb)
                arcTo(Rect(Offset(bx, by), rb), theta + 90f, -180f, false)
                lineTo(ax - nx * ra, ay - ny * ra)
                arcTo(Rect(Offset(ax, ay), ra), theta - 90f, -180f, false)
                close()
            }
        }
    }
}
