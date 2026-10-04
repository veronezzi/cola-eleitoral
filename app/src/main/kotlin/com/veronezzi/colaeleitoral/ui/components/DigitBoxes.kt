package com.veronezzi.colaeleitoral.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.ui.common.spokenDigits
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import com.veronezzi.colaeleitoral.ui.theme.NumberTextStyle

enum class DigitBoxSize { Small, Medium, Large }

/**
 * A candidate number as typed on the urna: one box per digit ([digitCount] boxes, empty when
 * [digits] is null). TalkBack reads the digits one by one ("1 2 3"); empty boxes are announced as
 * "N dígitos, sem escolha". Boxes have a minimum size but grow with the font scale.
 */
@Composable
fun DigitBoxes(
    digits: String?,
    digitCount: Int,
    modifier: Modifier = Modifier,
    size: DigitBoxSize = DigitBoxSize.Medium,
) {
    val number = digits?.filter { it.isDigit() }.orEmpty()
    val description = if (number.isEmpty()) {
        pluralStringResource(R.plurals.digit_boxes_empty, digitCount, digitCount)
    } else {
        spokenDigits(number)
    }
    val (minSize: Dp, textStyle: TextStyle) = when (size) {
        DigitBoxSize.Small -> 28.dp to MaterialTheme.typography.titleMedium
        DigitBoxSize.Medium -> 40.dp to MaterialTheme.typography.headlineSmall
        DigitBoxSize.Large -> 52.dp to MaterialTheme.typography.headlineLarge
    }
    val spacing = if (size == DigitBoxSize.Small) 4.dp else 6.dp
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(digitCount.coerceAtLeast(number.length)) { index ->
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = minSize, minHeight = minSize * BOX_HEIGHT_RATIO)
                    .border(
                        width = if (size == DigitBoxSize.Small) 1.dp else 2.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.getOrNull(index)?.toString().orEmpty(),
                    style = textStyle.merge(NumberTextStyle),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private const val BOX_HEIGHT_RATIO = 1.2f

@ThemePreviews
@Composable
private fun DigitBoxesPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        PreviewSurface {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DigitBoxes(digits = "12345", digitCount = 5, size = DigitBoxSize.Large)
                DigitBoxes(digits = "123", digitCount = 3)
                DigitBoxes(digits = null, digitCount = 4)
                DigitBoxes(digits = "45", digitCount = 2, size = DigitBoxSize.Small)
            }
        }
    }
}
