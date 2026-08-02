// AssignmentFormComponents.kt — Shared form building blocks for assigning/editing
// a patient's exercise prescription. Used by both EditAssignmentScreen (edit an
// existing assignment) and AssignExerciseScreen (create a new one).
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srcardiocare.data.model.Assignment
import com.srcardiocare.ui.theme.DesignTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal val AssignmentFormDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
internal fun ExerciseHeaderCard(name: String, category: String?, difficulty: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Primary.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.Spacing.LG),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(DesignTokens.Colors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                val sub = listOfNotNull(category, difficulty).joinToString(" • ")
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.4.sp
    )
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
}

@Composable
internal fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Base),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = DesignTokens.Colors.Primary)
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)

            IconButton(
                onClick = { if (value > min) onChange(value - 1) },
                enabled = value > min
            ) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease")
            }
            Text(
                "$value",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.widthIn(min = 36.dp),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = { if (value < max) onChange(value + 1) },
                enabled = value < max
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipPicker(
    label: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    renderOption: (Int) -> String,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { opt ->
                val isSelected = selected == opt
                Surface(
                    modifier = Modifier.clickable { onSelect(opt) },
                    shape = RoundedCornerShape(DesignTokens.Radius.Chip),
                    color = if (isSelected) DesignTokens.Colors.Primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Text(
                        renderOption(opt),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * Upper bound on times-per-day, shared with the read path so a prescription
 * cannot be written at a value the patient app will silently clamp away.
 */
internal const val MAX_DAILY_FREQUENCY = Assignment.MAX_DAILY_FREQUENCY

/**
 * A chip picker that starts at the common values and grows on demand.
 *
 * The fixed 1/2/3 chips covered the usual prescription and nothing else — a
 * doctor wanting four sessions a day had no way to say so. Rather than paint
 * ten chips nobody reads, the extra values are reachable one tap at a time
 * through the trailing +, and any value already selected stays on screen so an
 * existing assignment never renders with its own frequency missing.
 */
@Composable
internal fun ExpandableCountPicker(
    label: String,
    value: Int,
    onSelect: (Int) -> Unit,
    baseOptions: List<Int>,
    max: Int,
    renderOption: (Int) -> String,
    modifier: Modifier = Modifier
) {
    val options = (baseOptions + value).distinct().sorted().filter { it <= max }
    val highest = options.maxOrNull() ?: 0

    val plusChip: (@Composable () -> Unit)? = if (highest < max) {
        {
            Surface(
                modifier = Modifier.clickable { onSelect((highest + 1).coerceAtMost(max)) },
                shape = RoundedCornerShape(DesignTokens.Radius.Chip),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add one more",
                        tint = DesignTokens.Colors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    } else null

    ChipPicker(
        label = label,
        options = options,
        selected = value,
        onSelect = onSelect,
        renderOption = renderOption,
        modifier = modifier,
        trailing = plusChip
    )
}

@Composable
internal fun DateRow(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(10)) },
        label = { Text(label) },
        placeholder = { Text("DD/MM/YYYY") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(DesignTokens.Radius.Input),
        leadingIcon = {
            Icon(Icons.Default.Event, contentDescription = null, tint = DesignTokens.Colors.Primary)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = value.isNotBlank() && runCatching { LocalDate.parse(value, AssignmentFormDateFormat) }.isFailure
    )
}
