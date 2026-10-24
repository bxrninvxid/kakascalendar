package com.example.kakascalendar.system

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.kakascalendar.MainActivity
import com.example.kakascalendar.data.AppDb
import com.example.kakascalendar.domain.daysOf
import com.example.kakascalendar.domain.streakDates
import java.time.LocalDate

/** Home-screen widget: current streak + whether today is logged. */
class StreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val days = daysOf(AppDb.get(context).dao().allOnce())
        val streak = streakDates(days).size
        val today = LocalDate.now() in days
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(Color(0xFF9A5B34))
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔥 $streak", style = TextStyle(color = ColorProvider(Color.White), fontSize = 30.sp, fontWeight = FontWeight.Bold))
                Text("day streak", style = TextStyle(color = ColorProvider(Color(0xDDFFFFFF)), fontSize = 13.sp))
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    if (today) "✅ Logged today" else "💩 Log today!",
                    style = TextStyle(color = ColorProvider(Color(0xFFFFE6C7)), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { StreakWidget().updateAll(context) }
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
