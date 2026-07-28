package org.hedgewars.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hedgewars.android.ui.theme.HwColors

/**
 * A labeled integer control: [−] value [+], the standard row of the scheme
 * editor. Tapping the value swaps it for a direct numeric entry (committed
 * with the keyboard's Done, clamped to [range]) — steppers alone would be
 * painful for ranges like 1..9999. [specialLabels] renders magic values as
 * text, e.g. -1 -> "Random" for the mine timer.
 */
@Composable
fun NumberStepperRow(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    specialLabels: Map<Int, String> = emptyMap(),
    onValue: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = HwColors.TextLight,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        HwChip("−", selected = false, onClick = {
            editing = false
            onValue((value - 1).coerceIn(range))
        })
        if (editing) {
            var text by remember { mutableStateOf(value.toString()) }
            val focus = remember { FocusRequester() }
            val commit = {
                text.toIntOrNull()?.let { onValue(it.coerceIn(range)) }
                editing = false
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '-' } },
                singleLine = true,
                textStyle = TextStyle(
                    color = HwColors.Gold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(HwColors.Gold),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HwColors.IndigoDeep)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .focusRequester(focus),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        } else {
            Box(
                Modifier
                    .widthIn(min = 64.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { editing = true }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    specialLabels[value] ?: value.toString(),
                    color = HwColors.Gold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        HwChip("+", selected = false, onClick = {
            editing = false
            onValue((value + 1).coerceIn(range))
        })
    }
}

/** A labeled on/off rule row (whole row tappable) in the Hedgewars palette. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onChecked(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = HwColors.TextLight,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HwColors.IndigoDeep,
                checkedTrackColor = HwColors.Gold,
                uncheckedThumbColor = HwColors.TextMuted,
                uncheckedTrackColor = HwColors.PanelSolid,
                uncheckedBorderColor = HwColors.OutlineSoft,
            ),
        )
    }
}
