package sk.sipky.darts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.sipky.darts.R
import sk.sipky.darts.game.Difficulty
import sk.sipky.darts.game.GameType
import sk.sipky.darts.game.RoundaboutVariant

val BackgroundBrush = Brush.verticalGradient(listOf(Color(0xFF232A36), Color(0xFF12151B)))

private val TextShadow = Shadow(Color.Black, Offset(0f, 2f), 6f)
val TitleStyle = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, shadow = TextShadow)
val ScoreStyle = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, shadow = TextShadow)
val HeadingStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
val InfoStyle = TextStyle(color = Color(0xFFE6E9EF), fontSize = 17.sp, fontWeight = FontWeight.Medium, shadow = TextShadow)
val SmallStyle = TextStyle(color = Color(0xFFAEB6C2), fontSize = 14.sp)
val ButtonStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
val RecordStyle = TextStyle(color = Color(0xFFFFD166), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
val OverlayStyle = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
private val ChipStyle = TextStyle(color = Color(0xFFCDD3DC), fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val ChipSelectedStyle = TextStyle(color = Color(0xFF12151B), fontSize = 15.sp, fontWeight = FontWeight.Bold)
private val SelectedChip = Color(0xFFF2F2F2)
private val ButtonFill = Color(0x33FFFFFF)
private val ButtonBorder = Color(0x80FFFFFF)

/** Obdĺžnikové tlačidlo s textom a voliteľným druhým riadkom (napr. rekord). */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(ButtonFill)
            .border(1.dp, ButtonBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text, style = ButtonStyle)
        if (subtitle != null) BasicText(subtitle, style = SmallStyle.copy(textAlign = TextAlign.Center))
    }
}

/** Prepínač náročnosti. */
@Composable
fun DifficultySelector(selected: Difficulty, onSelect: (Difficulty) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .clip(shape)
            .border(1.dp, ButtonBorder, shape),
    ) {
        for (difficulty in Difficulty.entries) {
            val isSelected = difficulty == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (isSelected) SelectedChip else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(difficulty) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(stringResource(difficulty.labelRes), style = if (isSelected) ChipSelectedStyle else ChipStyle)
            }
        }
    }
}

/** Tmavý panel s textom cez hraciu plochu. */
@Composable
fun OverlayPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE60E1116))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
    }

val RoundaboutVariant.labelRes: Int
    get() = when (this) {
        RoundaboutVariant.ANY -> R.string.variant_any
        RoundaboutVariant.MULTIPLE -> R.string.variant_multiple
        RoundaboutVariant.DOUBLE_TRIPLE -> R.string.variant_double_triple
    }

val RoundaboutVariant.descriptionRes: Int
    get() = when (this) {
        RoundaboutVariant.ANY -> R.string.variant_any_desc
        RoundaboutVariant.MULTIPLE -> R.string.variant_multiple_desc
        RoundaboutVariant.DOUBLE_TRIPLE -> R.string.variant_double_triple_desc
    }

/** Text rekordu: počet šípok, body kola, alebo pomlčka. */
@Composable
@ReadOnlyComposable
fun recordText(type: GameType, record: Int?): String = when {
    record == null -> stringResource(R.string.no_record)
    type.lowerIsBetter -> pluralStringResource(R.plurals.darts_count, record, record)
    else -> stringResource(R.string.points_short, record)
}
