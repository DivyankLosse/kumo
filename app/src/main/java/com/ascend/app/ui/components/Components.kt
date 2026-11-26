package com.ascend.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ascend.app.domain.Category
import com.ascend.app.ui.theme.LocalCategoryColors
import com.ascend.app.ui.theme.LocalReduceMotion
import com.ascend.app.ui.theme.Motion

val ScreenPadding = 20.dp

/** Press feedback: a quick spring down to 97% while held. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.snappy(reduce), label = "press")
    return this.scale(s)
}

@Composable
fun AscendCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val base = modifier.fillMaxWidth()
    val m = if (onClick != null) base.pressScale(source) else base
    Surface(
        modifier = m,
        shape = MaterialTheme.shapes.large,
        color = color,
        onClick = onClick ?: {},
        enabled = onClick != null,
        interactionSource = source,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

@Composable
fun CategoryDot(category: Category, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(LocalCategoryColors.current.of(category)))
}

@Composable
fun CategoryPill(category: Category) {
    val c = LocalCategoryColors.current.of(category)
    Row(
        Modifier.clip(CircleShape).background(c.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(category.label, style = MaterialTheme.typography.labelMedium, color = c)
    }
}

/** A round check that springs and buzzes; 48dp hit area around a 28dp mark. */
@Composable
fun CheckMark(checked: Boolean, tint: Color, onToggle: () -> Unit, label: String) {
    val haptics = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    val fill by animateColorAsState(if (checked) tint else Color.Transparent, Motion.snappy(reduce), label = "fill")
    val pop by animateFloatAsState(if (checked) 1f else 0.6f, Motion.snappy(reduce), label = "pop")
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .clickable(role = Role.Checkbox) {
                haptics.performHapticFeedback(if (checked) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
                onToggle()
            }
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "Done" else "Not done"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(28.dp)) {
            drawCircle(fill, radius = size.minDimension / 2)
            if (!checked) drawCircle(tint.copy(alpha = 0.7f), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        if (checked) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(18.dp).scale(pop))
    }
}

@Composable
fun ChecklistRow(title: String, detail: String?, checked: Boolean, tint: Color, onToggle: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckMark(checked, tint, onToggle, title)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

data class RingSpec(val progress: Float, val color: Color, val label: String)

/** Concentric activity rings that spring to their values. */
@Composable
fun ActivityRings(rings: List<RingSpec>, modifier: Modifier = Modifier, stroke: Dp = 14.dp, gap: Dp = 4.dp) {
    val reduce = LocalReduceMotion.current
    val animated = rings.map { r ->
        animateFloatAsState(r.progress.coerceIn(0f, 1f), Motion.gentle(reduce), label = r.label).value
    }
    val desc = rings.joinToString { "${it.label} ${(it.progress * 100).toInt()} percent" }
    Canvas(modifier.semantics { contentDescription = desc }) {
        val sw = stroke.toPx()
        val g = gap.toPx()
        rings.forEachIndexed { i, r ->
            val inset = sw / 2 + i * (sw + g)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(r.color.copy(alpha = 0.16f), 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            if (animated[i] > 0.002f) drawArc(r.color, -90f, 360f * animated[i], false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
    }
}

/** Thin pill progress bar with a springy fill. */
@Composable
fun PillProgress(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val reduce = LocalReduceMotion.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.gentle(reduce), label = "pill")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(color.copy(alpha = 0.16f), cornerRadius = r)
        if (p > 0f) drawRoundRect(color, size = Size(size.width * p, size.height), cornerRadius = r)
    }
}

/** Custom bar chart: rounded bars, today highlighted, optional target line. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    highlightIndex: Int = -1,
    target: Float? = null,
    contentDescription: String,
) {
    val reduce = LocalReduceMotion.current
    val grow by animateFloatAsState(1f, Motion.gentle(reduce), label = "grow")
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(target ?: 0f).coerceAtLeast(1f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.semantics { this.contentDescription = contentDescription }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.5f
            values.forEachIndexed { i, v ->
                val h = (v / max) * size.height * grow
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(color.copy(alpha = 0.14f), Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                if (h > 0f) drawRoundRect(
                    if (i == highlightIndex) color else color.copy(alpha = 0.6f),
                    Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2),
                )
            }
            target?.let {
                val y = size.height - (it / max) * size.height
                drawLine(muted.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = if (i == highlightIndex) MaterialTheme.colorScheme.onSurface else muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Minimal line chart for trends like body weight. */
@Composable
fun LineChart(points: List<Float>, color: Color, modifier: Modifier = Modifier, contentDescription: String) {
    if (points.size < 2) {
        Text("Log at least two entries to see a trend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    val min = points.min()
    val max = points.max().let { if (it == min) min + 1f else it }
    Canvas(modifier.fillMaxWidth().height(100.dp).semantics { this.contentDescription = contentDescription }) {
        val step = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v - min) / (max - min) * size.height * 0.85f - size.height * 0.075f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if (i == points.lastIndex) drawCircle(color, 5.dp.toPx(), Offset(x, y))
        }
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(vertical = 8.dp))
}

/** Standard secondary-screen scaffold: large title, system-back aware top bar, lazy content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = floating,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = actions,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 96.dp),
            content = content,
        )
    }
}

/** Text field dialog with any number of fields; returns trimmed values. */
data class FieldSpec(val label: String, val initial: String = "", val numeric: Boolean = false, val singleLine: Boolean = true)

@Composable
fun FormDialog(
    title: String,
    fields: List<FieldSpec>,
    confirm: String = "Save",
    onDismiss: () -> Unit,
    validate: (List<String>) -> String? = { null },
    onConfirm: (List<String>) -> Unit,
) {
    val values = remember { fields.map { mutableStateOf(it.initial) } }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.forEachIndexed { i, f ->
                    OutlinedTextField(
                        value = values[i].value,
                        onValueChange = { values[i].value = it; error = null },
                        label = { Text(f.label) },
                        singleLine = f.singleLine,
                        minLines = if (f.singleLine) 1 else 3,
                        keyboardOptions = if (f.numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = values.map { it.value.trim() }
                val e = validate(v)
                if (e != null) error = e else onConfirm(v)
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ChipRow(options: List<String>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { i ->
            val o = options[i]
            FilterChip(
                selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) },
                modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
            )
        }
    }
}

@Composable
fun DisclaimerCard(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
