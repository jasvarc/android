package sk.sipky.darts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sk.sipky.darts.R
import sk.sipky.darts.game.Difficulty
import sk.sipky.darts.game.GameType
import sk.sipky.darts.game.RoundaboutVariant

/** Výber hry a náročnosti. Pri každej voľbe je zobrazený rekord pre zvolenú náročnosť. */
@Composable
fun MenuScreen(
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    recordFor: (GameType) -> Int?,
    onStart: (GameType) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(stringResource(R.string.app_name), style = TitleStyle)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(stringResource(R.string.menu_difficulty), style = InfoStyle)
            DifficultySelector(difficulty, onDifficulty, Modifier.fillMaxWidth())
        }

        ModeCard(stringResource(R.string.mode_training), stringResource(R.string.mode_training_desc)) {
            GameButton(
                text = stringResource(R.string.play),
                subtitle = recordText(GameType.Training, recordFor(GameType.Training)),
                onClick = { onStart(GameType.Training) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ModeCard(stringResource(R.string.mode_x01), stringResource(R.string.mode_x01_desc)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (start in GameType.X01_STARTS) {
                    val type = GameType.X01(start)
                    GameButton(
                        text = start.toString(),
                        subtitle = recordText(type, recordFor(type)),
                        onClick = { onStart(type) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        ModeCard(stringResource(R.string.mode_roundabout), stringResource(R.string.mode_roundabout_desc)) {
            for (variant in RoundaboutVariant.entries) {
                val type = GameType.Roundabout(variant)
                GameButton(
                    text = stringResource(variant.labelRes),
                    subtitle = stringResource(variant.descriptionRes) + "\n" +
                        stringResource(R.string.hud_record, recordText(type, recordFor(type))),
                    onClick = { onStart(type) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ModeCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1FFFFFFF))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(title, style = HeadingStyle)
        BasicText(description, style = SmallStyle)
        content()
    }
}
