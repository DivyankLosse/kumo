package com.ascend.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ascend.app.di.AppContainer
import com.ascend.app.domain.*
import com.ascend.app.ui.components.*
import com.ascend.app.ui.navigation.appViewModel
import com.ascend.app.ui.theme.LocalCategoryColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlinx.coroutines.flow.combine

private const val WEEKS = 8

data class WeekStat(
    val label: String,
    val completion: Int,
    val careerHours: Double,
    val focusMinutes: Int,
    val gymSessions: Int,
    val proteinDaysInRange: Int,
    val sleepOnTarget: Int,
)

class AnalyticsViewModel(private val c: AppContainer) : BaseViewModel() {
    private val firstWeek = Dates.weekStart().minusWeeks((WEEKS - 1).toLong())
    private val from = firstWeek.toEpochDay()
    private val to = Dates.todayEpoch()

    val weeks = combine(
        c.planner.observeRange(from, to),
        c.focus.observeSessionsBetween(Dates.startOfDayMs(firstWeek), Dates.endOfDayMs(Dates.today())),
        c.fitness.observeSessions(),
        c.nutrition.observeFoodRange(from, to),
        c.nutrition.observeSleep(),
    ) { tasks, focus, workouts, food, sleep ->
        val p = c.profile.get() ?: SeedData.profile
        (0 until WEEKS).map { w ->
            val ws = firstWeek.plusWeeks(w.toLong())
            val range = ws.toEpochDay()..ws.toEpochDay() + 6
            val wt = tasks.filter { it.date in range }
            val wf = focus.filter { Dates.epochDayOfMs(it.startedAt) in range }
            WeekStat(
                label = ws.format(Dates.shortFmt),
                completion = Metrics.completionPercent(wt),
                careerHours = Metrics.careerMinutes(wt, wf) / 60.0,
                focusMinutes = wf.sumOf { it.actualSeconds } / 60,
                gymSessions = workouts.count { it.date in range && it.finishedAt != null },
                proteinDaysInRange = food.filter { it.date in range }.groupBy { it.date }.count { (_, e) ->
                    Metrics.rangeStatus(Metrics.nutritionTotals(e).proteinG, p.proteinMinG, p.proteinMaxG) == RangeStatus.IN_RANGE
                },
                sleepOnTarget = sleep.count { it.date in range && Metrics.sleepOnTarget(it.bedMin, it.wakeMin) },
            )
        }
    }.state(emptyList())

    val focusByCategory = c.focus.observeSessionsBetween(Dates.startOfDayMs(firstWeek), Dates.endOfDayMs(Dates.today()))
        .state(emptyList())
    val weights = c.fitness.observeWeights().state(emptyList())
}

@Composable
private fun VicoColumns(values: List<Number>, labels: List<String>, color: Color, description: String) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        if (values.isNotEmpty()) producer.runTransaction { columnSeries { series(values) } }
    }
    if (values.isEmpty()) return
    val formatter = remember(labels) { CartesianValueFormatter { _, x, _ -> labels.getOrElse(x.toInt()) { "" } } }
    CartesianChartHost(
        rememberCartesianChart(
            rememberColumnCartesianLayer(
                ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = fill(color), thickness = 14.dp, shape = CorneredShape.rounded(allPercent = 40)),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = formatter),
        ),
        producer,
        Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description },
    )
}

@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    val vm = appViewModel { AnalyticsViewModel(it) }
    val weeks by vm.weeks.collectAsState()
    val focus by vm.focusByCategory.collectAsState()
    val weights by vm.weights.collectAsState()
    val cat = LocalCategoryColors.current
    val labels = weeks.map { it.label }

    DetailScreen("Analytics", onBack) {
        item {
            Text("Last $WEEKS weeks, from your own logs only.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("Plan completion")
            AscendCard {
                VicoColumns(weeks.map { it.completion }, labels, MaterialTheme.colorScheme.primary,
                    "Weekly plan completion percent: " + weeks.joinToString { "${it.label} ${it.completion}%" })
            }
        }
        item {
            SectionTitle("Career hours")
            AscendCard {
                VicoColumns(weeks.map { it.careerHours }, labels, cat.of(Category.CAREER),
                    "Career hours per week: " + weeks.joinToString { "${it.label} ${it.careerHours.fmt()}" })
                Text("Target: 28 h/week (4 h × 7)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionTitle("Focus by category")
            AscendCard {
                val byCat = focus.groupBy { Category.of(it.category) }.mapValues { e -> e.value.sumOf { it.actualSeconds } / 60 }
                val total = byCat.values.sum().coerceAtLeast(1)
                if (byCat.isEmpty()) EmptyHint("No focus sessions yet.")
                byCat.entries.sortedByDescending { it.value }.forEach { (c, m) ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Text(c.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(110.dp))
                        Column(Modifier.weight(1f)) {
                            PillProgress(m / total.toFloat(), cat.of(c), height = 10.dp)
                        }
                        Text(if (m < 60) " $m min" else " ${(m / 60.0).fmt()} h", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item {
            SectionTitle("Consistency")
            val last = weeks.lastOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(Modifier.weight(1f), "Gym", "${last?.gymSessions ?: 0}/5", "sessions this week", cat.of(Category.FITNESS), (last?.gymSessions ?: 0) / 5f) {}
                MetricTile(Modifier.weight(1f), "Protein", "${last?.proteinDaysInRange ?: 0}/7", "days in range", cat.of(Category.FITNESS), (last?.proteinDaysInRange ?: 0) / 7f) {}
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(Modifier.weight(1f), "Sleep", "${last?.sleepOnTarget ?: 0}/7", "nights on target", cat.of(Category.HEALTH), (last?.sleepOnTarget ?: 0) / 7f) {}
                MetricTile(Modifier.weight(1f), "Focus", "${((last?.focusMinutes ?: 0) / 60.0).fmt()} h", "this week", cat.of(Category.COMMUNICATION), null) {}
            }
        }
        item {
            SectionTitle("Gym sessions per week")
            AscendCard {
                BarChart(weeks.map { it.gymSessions.toFloat() }, weeks.map { it.label.substringBefore(' ') }, cat.of(Category.FITNESS),
                    highlightIndex = weeks.lastIndex, target = 5f, contentDescription = "Gym sessions per week against a target of 5")
            }
        }
        item {
            SectionTitle("Body weight")
            AscendCard { LineChart(weights.map { it.kg.toFloat() }, cat.of(Category.FITNESS), contentDescription = "Body weight trend") }
        }
    }
}
