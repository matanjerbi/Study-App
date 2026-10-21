package com.matan.study.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ======================= theme =======================

private val Light = lightColorScheme(
    primary = Color(0xFF5B63D6), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E6FF), onPrimaryContainer = Color(0xFF1E2470),
    secondary = Color(0xFFF2784B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D6), onSecondaryContainer = Color(0xFF5A220C),
    tertiary = Color(0xFF22A08A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4F5EE), onTertiaryContainer = Color(0xFF00382F),
    background = Color(0xFFFBF7F0), onBackground = Color(0xFF2B2A3A),
    surface = Color(0xFFFBF7F0), onSurface = Color(0xFF2B2A3A),
    surfaceVariant = Color(0xFFEFEAF4), onSurfaceVariant = Color(0xFF5C5A6C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF6F2EC), surfaceContainerHighest = Color(0xFFEFEAE3),
    outline = Color(0xFF8A8798), outlineVariant = Color(0xFFE2DDE6),
    error = Color(0xFFD94F5C), errorContainer = Color(0xFFFFE0E3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9AEFF), onPrimary = Color(0xFF1A1E66),
    primaryContainer = Color(0xFF333A8F), onPrimaryContainer = Color(0xFFE4E6FF),
    secondary = Color(0xFFFFA483), onSecondary = Color(0xFF4A1B08),
    secondaryContainer = Color(0xFF6A3018), onSecondaryContainer = Color(0xFFFFE3D6),
    tertiary = Color(0xFF6FDCC5), onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF0E5146), onTertiaryContainer = Color(0xFFD4F5EE),
    background = Color(0xFF14152B), onBackground = Color(0xFFECEAF6),
    surface = Color(0xFF14152B), onSurface = Color(0xFFECEAF6),
    surfaceVariant = Color(0xFF2A2C4A), onSurfaceVariant = Color(0xFFC6C3D8),
    surfaceContainerLowest = Color(0xFF101123), surfaceContainerLow = Color(0xFF191A33),
    surfaceContainer = Color(0xFF1E2040), surfaceContainerHigh = Color(0xFF26284A), surfaceContainerHighest = Color(0xFF2F3156),
    outline = Color(0xFF908DA6), outlineVariant = Color(0xFF3A3C5E),
)

private val Type = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun StudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content,
    )
}

/** Course colours: soft, friendly, readable in both themes. */
val CoursePalette = listOf(
    Color(0xFF7C83FD), Color(0xFFFF8FA3), Color(0xFF3FBF9B), Color(0xFFFFB347),
    Color(0xFF5DADEC), Color(0xFFB57EDC), Color(0xFFFF8A65), Color(0xFF8BC34A),
)
fun courseColor(i: Int) = CoursePalette[((i % CoursePalette.size) + CoursePalette.size) % CoursePalette.size]

val Danger = Color(0xFFE5566A)
val Warn = Color(0xFFF2A33A)
val Ok = Color(0xFF2FAF8F)

// ======================= formatting =======================

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yy")
private val longFmt = DateTimeFormatter.ofPattern("EEEE, d 'ב'MMMM", Locale.forLanguageTag("he"))
fun fmtDay(day: Long): String = LocalDate.ofEpochDay(day).format(dateFmt)
fun fmtLong(d: LocalDate): String = d.format(longFmt)
fun num(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
fun hours(minutes: Int) = if (minutes < 60) "$minutes דק'" else "${num(minutes / 60.0)} ש'"
fun today() = LocalDate.now().toEpochDay()

fun Context.toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

// ======================= components =======================

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = color ?: MaterialTheme.colorScheme.surfaceContainer)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, elevation = elevation, shape = RoundedCornerShape(24.dp), content = content)
    else Card(modifier = modifier.fillMaxWidth(), colors = colors, elevation = elevation, shape = RoundedCornerShape(24.dp), content = content)
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.primary, filled: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (filled) Color.White else color,
        modifier = Modifier.background(if (filled) color else color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Colored letter circle for a course. */
@Composable
fun CourseBadge(name: String, color: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1), color = color, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp, color: Color, stroke: Dp = 12.dp, center: @Composable () -> Unit = {}) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), arc, style = Stroke(s))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(s / 2, s / 2), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Column(
        modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, multiline: Boolean = false) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 3 else 1,
        shape = RoundedCornerShape(16.dp))
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

fun String.toD(): Double? = replace(",", ".").trim().toDoubleOrNull()
fun String.toI(): Int? = trim().toIntOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, day: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(day?.let { fmtDay(it) } ?: "", {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) }, shape = RoundedCornerShape(16.dp))
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(day ?: today()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    open = false
                }) { Text("אישור") }
            },
            dismissButton = {
                Row {
                    if (clearable && day != null) TextButton(onClick = { onChange(null); open = false }) { Text("נקה") }
                    TextButton(onClick = { open = false }) { Text("ביטול") }
                }
            },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, options: List<T>, selected: T, labelOf: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(labelOf(selected), {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, shape = RoundedCornerShape(16.dp))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { DropdownMenuItem(text = { Text(labelOf(it)) }, onClick = { onSelect(it); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, labelOf: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { FilterChip(selected = it == selected, onClick = { onSelect(it) }, label = { Text(labelOf(it)) }) }
    }
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CoursePalette.forEachIndexed { i, c ->
            Box(
                Modifier.size(32.dp).background(c, CircleShape)
                    .then(if (i == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(i) },
            )
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun ConfirmDialog(text: String, confirm: String = "מחק", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** Days-left pill: red ≤2, amber ≤7, calm otherwise. */
@Composable
fun DaysPill(daysLeft: Long) {
    val color = when {
        daysLeft <= 2 -> Danger
        daysLeft <= 7 -> Warn
        else -> Ok
    }
    Pill(com.matan.study.logic.daysText(daysLeft), color, filled = daysLeft <= 2)
}
