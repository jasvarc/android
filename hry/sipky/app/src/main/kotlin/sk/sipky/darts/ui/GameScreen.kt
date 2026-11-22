package sk.sipky.darts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.sipky.darts.R
import sk.sipky.darts.game.GameConfig
import sk.sipky.darts.game.GameState
import sk.sipky.darts.game.Phase

private val BackgroundBrush = Brush.verticalGradient(listOf(Color(0xFF232A36), Color(0xFF12151B)))

private val TextShadow = Shadow(Color.Black, Offset(0f, 2f), 6f)
private val ScoreStyle = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, shadow = TextShadow)
private val InfoStyle = TextStyle(color = Color(0xFFE6E9EF), fontSize = 17.sp, fontWeight = FontWeight.Medium, shadow = TextShadow)
private val ButtonStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
private val SummaryStyle = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)

@Composable
fun GameScreen(game: GameState) {
    val haptic = LocalHapticFeedback.current
    val renderer = remember { SceneRenderer() }

    // Herná slučka: jeden krok logiky na každý snímok displeja.
    LaunchedEffect(game) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
                last = now
                if (game.update(dt)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundBrush),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures)),
        ) {
            Hud(game)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(game) { aimAndThrow(game, renderer) },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    renderer.updateLayout(size, this)
                    renderer.draw(this, game)
                }
                if (game.phase == Phase.ROUND_SUMMARY) {
                    RoundSummary(game.round, game.roundPoints, Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/** Relatívne ťahanie prstom posúva zameriavač, zdvihnutie prsta hodí šípku. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.aimAndThrow(
    game: GameState,
    renderer: SceneRenderer,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (!game.onPointerDown()) return@awaitEachGesture
        down.consume()
        var last = down.position
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                break
            }
            val pxPerMm = renderer.pxPerMm
            if (pxPerMm > 0f) {
                val delta = change.position - last
                val mmPerPx = GameConfig.dragSensitivity / pxPerMm
                game.onPointerMove(delta.x * mmPerPx, delta.y * mmPerPx)
            }
            last = change.position
            change.consume()
        }
        game.onPointerUp()
    }
}

@Composable
private fun Hud(game: GameState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(stringResource(R.string.hud_score, game.score), style = ScoreStyle)
            BasicText(
                stringResource(R.string.hud_round, game.round, game.dartNumber, GameConfig.dartsPerRound),
                style = InfoStyle,
            )
            val last = game.lastResult
            BasicText(
                if (last != null) {
                    stringResource(R.string.hud_last_throw, last.label, last.points)
                } else {
                    stringResource(R.string.hud_hint)
                },
                style = InfoStyle,
            )
        }
        NewGameButton(onClick = game::newGame)
    }
}

@Composable
private fun NewGameButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(Color(0x33FFFFFF))
            .border(1.dp, Color(0x80FFFFFF), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicText(stringResource(R.string.new_game), style = ButtonStyle)
    }
}

@Composable
private fun RoundSummary(round: Int, points: Int, modifier: Modifier = Modifier) {
    BasicText(
        pluralStringResource(R.plurals.round_summary, points, round, points),
        style = SummaryStyle,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC0E1116))
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

private const val MAX_FRAME_SECONDS = 0.05f
