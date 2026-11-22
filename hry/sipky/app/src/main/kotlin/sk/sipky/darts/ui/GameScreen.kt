package sk.sipky.darts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sk.sipky.darts.R
import sk.sipky.darts.game.GameConfig
import sk.sipky.darts.game.GameState
import sk.sipky.darts.game.Phase
import sk.sipky.darts.game.RoundaboutRules
import sk.sipky.darts.game.TrainingRules
import sk.sipky.darts.game.X01Rules

@Composable
fun GameScreen(game: GameState, onExit: () -> Unit) {
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
            Hud(game, onExit)
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
                val overlayModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 48.dp)
                when (game.phase) {
                    Phase.ROUND_SUMMARY -> RoundSummary(game, overlayModifier)
                    Phase.GAME_OVER -> GameOver(game, onExit, overlayModifier)
                    else -> Unit
                }
            }
        }
    }
}

/** Relatívne ťahanie prstom posúva zameriavač, zdvihnutie prsta hodí šípku. */
private suspend fun PointerInputScope.aimAndThrow(game: GameState, renderer: SceneRenderer) {
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
private fun Hud(game: GameState, onExit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            when (val rules = game.rules) {
                is TrainingRules -> BasicText(stringResource(R.string.hud_score, rules.total), style = ScoreStyle)
                is X01Rules -> BasicText(stringResource(R.string.hud_remaining, rules.remaining), style = ScoreStyle)
                is RoundaboutRules -> {
                    val label = rules.currentTarget?.label ?: stringResource(R.string.no_record)
                    BasicText(stringResource(R.string.hud_target, label), style = ScoreStyle)
                    BasicText(
                        stringResource(R.string.hud_progress, minOf(rules.targetIndex + 1, rules.targets.size), rules.targets.size),
                        style = InfoStyle,
                    )
                }
            }
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
            BasicText(stringResource(R.string.hud_record, recordText(game.type, game.record)), style = SmallStyle)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton(stringResource(R.string.exit), onClick = onExit)
            GameButton(stringResource(R.string.restart), onClick = game::restart)
        }
    }
}

@Composable
private fun RoundSummary(game: GameState, modifier: Modifier) {
    OverlayPanel(modifier) {
        val text = when (val rules = game.rules) {
            is TrainingRules -> pluralStringResource(R.plurals.round_summary, rules.turnPoints, game.round, rules.turnPoints)
            is X01Rules -> if (rules.busted) {
                stringResource(R.string.summary_bust, rules.remaining)
            } else {
                stringResource(R.string.summary_x01, game.round, rules.turnPoints, rules.remaining)
            }
            is RoundaboutRules -> pluralStringResource(R.plurals.summary_hits, rules.turnPoints, game.round, rules.turnPoints)
        }
        BasicText(text, style = OverlayStyle)
        if (game.isNewRecord) BasicText(stringResource(R.string.new_record), style = RecordStyle)
    }
}

@Composable
private fun GameOver(game: GameState, onExit: () -> Unit, modifier: Modifier) {
    OverlayPanel(modifier) {
        BasicText(stringResource(R.string.game_over), style = OverlayStyle)
        BasicText(pluralStringResource(R.plurals.game_over_darts, game.totalDarts, game.totalDarts), style = OverlayStyle)
        if (game.isNewRecord) {
            BasicText(stringResource(R.string.new_record), style = RecordStyle)
        } else {
            BasicText(stringResource(R.string.hud_record, recordText(game.type, game.record)), style = InfoStyle)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GameButton(stringResource(R.string.play_again), onClick = game::restart)
            GameButton(stringResource(R.string.menu), onClick = onExit)
        }
    }
}

private const val MAX_FRAME_SECONDS = 0.05f
