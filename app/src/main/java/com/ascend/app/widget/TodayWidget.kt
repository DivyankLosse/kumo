package com.ascend.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ascend.app.AscendApp
import com.ascend.app.MainActivity
import com.ascend.app.data.local.PlannedTaskEntity
import com.ascend.app.domain.Dates
import com.ascend.app.domain.Metrics
import com.ascend.app.domain.ScheduleRules

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as AscendApp).container
        c.awaitReady()
        val today = Dates.today()
        c.planner.ensureDay(today)
        val tasks = c.planner.day(today.toEpochDay())
        provideContent { Content(tasks) }
    }

    @Composable
    private fun Content(tasks: List<PlannedTaskEntity>) {
        val ink = ColorProvider(Color(0xFF0D1120))
        val cream = ColorProvider(Color(0xFFFFF7D6))
        val muted = ColorProvider(Color(0xFFB9C3DA))
        val butter = ColorProvider(Color(0xFFFFE08A))
        val pct = Metrics.completionPercent(tasks)
        val nowMin = Dates.nowMinutes()
        val upcoming = tasks.filter { !it.completed && it.endMin > nowMin }.take(3)
        Column(
            GlanceModifier.fillMaxSize().background(ink).cornerRadius(24.dp).padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(GlanceModifier.fillMaxWidth()) {
                Text("Kumo today", style = TextStyle(color = cream, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text("$pct%", style = TextStyle(color = butter, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = pct / 100f, modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = butter, backgroundColor = ColorProvider(Color(0xFF2A3150)),
            )
            Spacer(GlanceModifier.height(10.dp))
            if (upcoming.isEmpty()) {
                Text("All clear. Rest well.", style = TextStyle(color = muted, fontSize = 13.sp))
            } else {
                upcoming.forEach {
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(ScheduleRules.format(it.startMin), style = TextStyle(color = muted, fontSize = 13.sp))
                        Spacer(GlanceModifier.width(10.dp))
                        Text(it.title, style = TextStyle(color = cream, fontSize = 13.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
