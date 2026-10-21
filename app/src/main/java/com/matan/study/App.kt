package com.matan.study

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.matan.study.data.Repo
import com.matan.study.data.Settings
import com.matan.study.data.SettingsStore
import com.matan.study.data.StudyDatabase
import com.matan.study.data.StudyState
import com.matan.study.logic.Agenda
import com.matan.study.logic.EventKind
import com.matan.study.logic.Srs
import com.matan.study.ui.AppNav
import com.matan.study.ui.StudyTheme
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class StudyApp : Application() {
    lateinit var repo: Repo
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repo(Room.databaseBuilder(this, StudyDatabase::class.java, "study.db").build())
        settings = SettingsStore(this)
        Reminders.createChannel(this)
        Reminders.schedule(this, settings.flow.value, replace = false)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { AppNav() }
            }
        }
    }
}

object Reminders {
    private const val CHANNEL = "study"
    private const val WORK = "daily-study"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "תזכורות לימודים", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun schedule(context: Context, s: Settings, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(s.notifHour, s.notifMinute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP, req,
        )
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("force" to true)).build())
    }

    /** Lines for today's reminder: assignments 7 days / 1 day before, exams 14 / 3 / 1 days before, cards due. */
    fun lines(st: StudyState, s: Settings, today: Long): List<String> {
        val out = mutableListOf<String>()
        Agenda.upcoming(st, today).forEach { d ->
            val left = d.day - today
            val hit = when (d.kind) {
                EventKind.ASSIGNMENT -> left == 7L || left == 1L || left == 0L
                else -> left == 14L || left == 3L || left == 1L
            }
            if (hit) {
                val whenText = when (left) { 0L -> "היום"; 1L -> "מחר"; else -> "בעוד $left ימים" }
                out += "${d.course.name}: ${d.title} $whenText"
            }
        }
        if (s.cardReminder) {
            val due = Srs.due(st.cards, today).size
            if (due > 0) out += "$due כרטיסיות מחכות לחזרה"
        }
        return out
    }

    suspend fun check(context: Context, force: Boolean) {
        val app = context.applicationContext as StudyApp
        val prefs = context.getSharedPreferences("notif_state", Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        if (!force && prefs.getLong("lastRun", -1) == today) return
        prefs.edit().putLong("lastRun", today).apply()
        val lines = lines(app.repo.state().first(), app.settings.flow.value, today)
        if (lines.isEmpty()) {
            if (force) notify(context, listOf("אין דדליינים קרובים – יום רגוע 🌿"))
            return
        }
        notify(context, lines)
    }

    private fun notify(context: Context, lines: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().also { st -> lines.forEach { st.addLine(it) } }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📚 הלימודים להיום")
            .setContentText(if (lines.size == 1) lines[0] else "${lines.size} דברים שכדאי לדעת")
            .setStyle(style)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, n)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Reminders.check(applicationContext, force = inputData.getBoolean("force", false))
        return Result.success()
    }
}
