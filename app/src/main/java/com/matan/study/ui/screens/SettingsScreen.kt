package com.matan.study.ui.screens

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.study.Reminders
import com.matan.study.ui.AppViewModel
import com.matan.study.ui.ChoiceChips
import com.matan.study.ui.ConfirmDialog
import com.matan.study.ui.NumberField
import com.matan.study.ui.Screen
import com.matan.study.ui.SectionTitle
import com.matan.study.ui.SoftCard
import com.matan.study.ui.SwitchRow
import com.matan.study.ui.num
import com.matan.study.ui.toD
import com.matan.study.ui.toI
import com.matan.study.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var before by remember { mutableStateOf(if (s.creditsBefore > 0) num(s.creditsBefore) else "") }
    var avg by remember { mutableStateOf(s.averageBefore?.let { num(it) } ?: "") }
    var required by remember { mutableStateOf(if (s.creditsRequired > 0) num(s.creditsRequired) else "") }
    var perYear by remember { mutableStateOf(s.semestersPerYear) }
    var goal by remember { mutableStateOf(s.weeklyGoalHours.toString()) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)!!.use { vm.repo.export(it, s) } }.isSuccess }
            context.toast(if (ok) "הגיבוי נשמר" else "שמירת הגיבוי נכשלה")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingImport = it }

    Screen("הגדרות", nav) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("🎓 התואר")
            SoftCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("נקודות זכות שצריך לתואר", required, { required = it })
                    NumberField("נקודות זכות שיש לי כבר (לפני האפליקציה)", before, { before = it })
                    NumberField("הממוצע שלי עד עכשיו (לא חובה)", avg, { avg = it })
                    Text("כמה סמסטרים בשנה אתה לומד?", style = MaterialTheme.typography.bodyMedium)
                    ChoiceChips(listOf(2, 3), perYear, { if (it == 2) "2 (א'+ב')" else "3 (כולל קיץ)" }) { perYear = it }
                    NumberField("יעד שעות לימוד בשבוע", goal, { goal = it })
                    Button(onClick = {
                        vm.settingsStore.update(
                            s.copy(
                                creditsRequired = required.toD() ?: 0.0, creditsBefore = before.toD() ?: 0.0,
                                averageBefore = avg.toD()?.coerceIn(0.0, 100.0), semestersPerYear = perYear,
                                weeklyGoalHours = goal.toI() ?: s.weeklyGoalHours,
                            )
                        )
                        context.toast("נשמר")
                    }) { Text("שמור") }
                }
            }

            SectionTitle("🔔 תזכורות")
            SoftCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("שעת התזכורת היומית: %02d:%02d".format(s.notifHour, s.notifMinute), Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                val ns = s.copy(notifHour = h, notifMinute = m)
                                vm.settingsStore.update(ns)
                                Reminders.schedule(context, ns, replace = true)
                            }, s.notifHour, s.notifMinute, true).show()
                        }) { Text("שנה") }
                    }
                    SwitchRow("להזכיר כשיש כרטיסיות לחזרה", s.cardReminder) { vm.settingsStore.update(s.copy(cardReminder = it)) }
                    Text("מטלות: שבוע ויום לפני ההגשה. בחינות: שבועיים, 3 ימים ויום לפני.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { Reminders.runNow(context); context.toast("שולח תזכורת לבדיקה") }) { Text("שלח תזכורת עכשיו") }
                }
            }

            SectionTitle("💾 גיבוי")
            Text("כל הנתונים נשמרים רק בטלפון. כדאי לגבות פעם בכמה זמן.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exporter.launch("study-backup-${LocalDate.now()}.json") }) { Text("ייצוא גיבוי") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) { Text("שחזור") }
            }
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog("השחזור יחליף את כל הנתונים הנוכחיים. להמשיך?", confirm = "שחזר", onConfirm = {
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)!!.use { vm.repo.import(it) } } }
                r.getOrNull()?.let { imported ->
                    vm.settingsStore.update(imported.copy(notifHour = s.notifHour, notifMinute = s.notifMinute, cardReminder = s.cardReminder))
                }
                context.toast(if (r.isSuccess) "השחזור הושלם" else "השחזור נכשל")
            }
        }, onDismiss = { pendingImport = null })
    }
}
