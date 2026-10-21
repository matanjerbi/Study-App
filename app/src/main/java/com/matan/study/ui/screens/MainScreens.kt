package com.matan.study.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.study.data.Course
import com.matan.study.data.CourseStatus
import com.matan.study.data.StudyState
import com.matan.study.logic.Agenda
import com.matan.study.logic.DegreeCalc
import com.matan.study.logic.Deadline
import com.matan.study.logic.Grades
import com.matan.study.logic.Semesters
import com.matan.study.logic.Srs
import com.matan.study.ui.AppViewModel
import com.matan.study.ui.CourseBadge
import com.matan.study.ui.DaysPill
import com.matan.study.ui.Loading
import com.matan.study.ui.Ok
import com.matan.study.ui.Pill
import com.matan.study.ui.ProgressRing
import com.matan.study.ui.Routes
import com.matan.study.ui.Screen
import com.matan.study.ui.SectionTitle
import com.matan.study.ui.SoftCard
import com.matan.study.ui.Stat
import com.matan.study.ui.courseColor
import com.matan.study.ui.fmtDay
import com.matan.study.ui.fmtLong
import com.matan.study.ui.hours
import com.matan.study.ui.num
import com.matan.study.ui.toast
import kotlinx.coroutines.delay
import java.time.LocalDate

private val pagePad = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp)

@Composable
internal fun SettingsAction(nav: NavController) {
    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "הגדרות") }
}

fun weekStart(d: LocalDate) = d.toEpochDay() - (d.dayOfWeek.value % 7) // week starts on Sunday

// ======================= Today =======================

@Composable
fun TodayScreen(vm: AppViewModel, nav: NavController) {
    val st = vm.state.collectAsStateWithLifecycle().value ?: return Loading()
    val s by vm.settings.collectAsStateWithLifecycle()
    val date = LocalDate.now()
    val t = date.toEpochDay()
    val ws = weekStart(date)
    val deadlines = Agenda.upcoming(st, t)
    val due = Srs.due(st.cards, t)
    val weekMinutes = Agenda.minutesThisWeek(st, t, ws)
    val goal = s.weeklyGoalHours * 60
    val degree = DegreeCalc.compute(st, s, Semesters.current(date))
    val hello = when (java.time.LocalTime.now().hour) { in 5..11 -> "בוקר טוב"; in 12..16 -> "צהריים טובים"; in 17..21 -> "ערב טוב"; else -> "לילה טוב" }

    Screen("", tab = true, actions = { SettingsAction(nav) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = pagePad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                            RoundedCornerShape(28.dp),
                        )
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("$hello 👋", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(fmtLong(date), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    val next = deadlines.firstOrNull()
                    Text(
                        when {
                            next != null -> "הבא בתור: ${next.title} ב${next.course.name} – ${com.matan.study.logic.daysText(next.day - t)}"
                            st.courses.isEmpty() -> "בוא נוסיף את הקורסים שלך ונתחיל"
                            else -> "אין דדליינים קרובים – זמן מצוין להתקדם בחומר 🌱"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = Color.White,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (!s.degreeSet) item {
                SoftCard(onClick = { nav.navigate(Routes.SETTINGS) }, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🎓 הגדרת התואר", style = MaterialTheme.typography.titleMedium)
                        Text("הזן כמה נקודות זכות יש לך וכמה צריך לתואר, ואחשב לך התקדמות, ממוצע ותאריך סיום משוער.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (st.courses.isEmpty()) item {
                SoftCard(onClick = { nav.navigate(Routes.courseEdit(0)) }, color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📚 הוסף את הקורס הראשון", style = MaterialTheme.typography.titleMedium)
                        Text("שם, נקודות זכות, כמה ממ\"נים וממ\"חים – והמטלות נוצרות לבד.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { TimerCard(vm, st) }
            if (due.isNotEmpty()) item {
                SoftCard(onClick = { nav.navigate(Routes.review(0)) }, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🧠", style = MaterialTheme.typography.headlineMedium)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("${due.size} כרטיסיות לחזרה היום", style = MaterialTheme.typography.titleMedium)
                            Text("כמה דקות, וזה נשאר בראש", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            if (deadlines.isNotEmpty()) {
                item { SectionTitle("מה מחכה לך") }
                items(deadlines.take(6), key = { "${it.course.id}_${it.kind}_${it.assignment?.id}_${it.day}" }) { d ->
                    DeadlineRow(d, t) { nav.navigate(Routes.course(d.course.id)) }
                }
            }
            item { WeekCard(st, ws, t, weekMinutes, goal) }
            if (s.degreeSet) item {
                SoftCard(onClick = { nav.navigate(Routes.DEGREE) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(degree.progress, 64.dp, MaterialTheme.colorScheme.secondary, 8.dp) {
                            Text("${(degree.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("התואר שלך", style = MaterialTheme.typography.titleMedium)
                            Text("${num(degree.creditsDone)} מתוך ${num(degree.creditsRequired)} נק\"ז" +
                                (degree.average?.let { " · ממוצע ${"%.1f".format(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                            degree.expectedFinish?.let { Text("סיום משוער: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeadlineRow(d: Deadline, today: Long, onClick: () -> Unit) {
    val color = courseColor(d.course.color)
    SoftCard(onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).height(44.dp).background(color, RoundedCornerShape(3.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(d.title, style = MaterialTheme.typography.titleMedium)
                Text("${d.course.name} · ${fmtDay(d.day)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DaysPill(d.day - today)
        }
    }
}

@Composable
private fun TimerCard(vm: AppViewModel, st: StudyState) {
    val context = LocalContext.current
    val active = st.courses.filter { it.status == CourseStatus.ACTIVE }
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.timerRunning) {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(vm.timerStart) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                val sec = ((now - vm.timerStart) / 1000).coerceAtLeast(0)
                val c = st.course(vm.timerCourse)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("⏱️ לומד עכשיו", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(c?.name ?: "", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("%d:%02d:%02d".format(sec / 3600, (sec / 60) % 60, sec % 60), style = MaterialTheme.typography.headlineMedium,
                        color = courseColor(c?.color ?: 0))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { val m = vm.stopTimer(); context.toast("נשמרו $m דקות 💪") }) { Text("סיימתי, שמור") }
                    TextButton(onClick = { vm.cancelTimer() }) { Text("בטל") }
                }
            } else {
                Text("⏱️ להתחיל ללמוד", style = MaterialTheme.typography.titleMedium)
                if (active.isEmpty()) Text("הוסף קורס פעיל כדי למדוד זמן לימוד", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(active, key = { it.id }) { c ->
                        AssistChip(onClick = { vm.startTimer(c.id) }, label = { Text("▶ ${c.name}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(st: StudyState, ws: Long, today: Long, weekMinutes: Int, goal: Int) {
    val perDay = (0..6).map { i -> st.sessions.filter { it.day == ws + i }.sumOf { it.minutes } }
    val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(60)
    val bar = MaterialTheme.colorScheme.primary
    val todayBar = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceVariant
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(if (goal > 0) weekMinutes.toFloat() / goal else 0f, 72.dp, MaterialTheme.colorScheme.primary, 9.dp) {
                    Text(if (goal > 0) "${(weekMinutes * 100 / goal)}%" else "–", style = MaterialTheme.typography.labelLarge)
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text("השבוע", style = MaterialTheme.typography.titleMedium)
                    Text("${hours(weekMinutes)} מתוך ${hours(goal)}", style = MaterialTheme.typography.bodyMedium)
                    if (goal in 1..weekMinutes) Text("עמדת ביעד! 🎉", color = Ok, style = MaterialTheme.typography.bodySmall)
                }
            }
            // Sunday on the right, like a Hebrew calendar
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                val slot = size.width / 7
                val w = slot * 0.5f
                perDay.forEachIndexed { i, m ->
                    val x = size.width - (i + 1) * slot + (slot - w) / 2
                    drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                    val h = size.height * m / max
                    if (h > 0) drawRoundRect(if (ws + i == today) todayBar else bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("א", "ב", "ג", "ד", "ה", "ו", "ש").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ======================= Courses =======================

@Composable
fun CoursesScreen(vm: AppViewModel, nav: NavController) {
    val st = vm.state.collectAsStateWithLifecycle().value ?: return Loading()
    val t = LocalDate.now().toEpochDay()
    val groups = listOf(
        "פעילים" to st.courses.filter { it.status == CourseStatus.ACTIVE },
        "מתוכננים" to st.courses.filter { it.status == CourseStatus.PLANNED },
        "הושלמו" to st.courses.filter { it.status == CourseStatus.DONE },
    )
    Screen("הקורסים שלי", tab = true, actions = { SettingsAction(nav) }, fab = {
        ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.courseEdit(0)) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("קורס חדש") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = pagePad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (st.courses.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", style = MaterialTheme.typography.displaySmall)
                    Text("עוד אין קורסים", style = MaterialTheme.typography.titleMedium)
                    Text("לחץ על \"קורס חדש\" כדי להתחיל", style = MaterialTheme.typography.bodySmall)
                }
            }
            groups.forEach { (title, list) ->
                if (list.isNotEmpty()) {
                    item(key = "h_$title") { SectionTitle("$title (${list.size})") }
                    items(list, key = { it.id }) { c -> CourseCard(st, c, t) { nav.navigate(Routes.course(c.id)) } }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(st: StudyState, c: Course, today: Long, onClick: () -> Unit) {
    val color = courseColor(c.color)
    val assignments = st.assignmentsOf(c.id)
    val submitted = assignments.count { it.submitted }
    val final = Grades.finalGrade(c, assignments)
    val next = Agenda.upcoming(st, today).firstOrNull { it.course.id == c.id }
    SoftCard(onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CourseBadge(c.name, color)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(c.number, "${num(c.credits)} נק\"ז", c.semester).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (final != null) Pill("$final", if (final >= Grades.PASS) Ok else com.matan.study.ui.Danger, filled = true)
            }
            if (assignments.isNotEmpty() && c.status != CourseStatus.DONE) {
                LinearProgressIndicator(
                    progress = { submitted.toFloat() / assignments.size }, color = color, trackColor = color.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Row {
                    Text("$submitted/${assignments.size} מטלות הוגשו", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    next?.let { Text("${it.title} · ${com.matan.study.logic.daysText(it.day - today)}", style = MaterialTheme.typography.labelMedium, color = color) }
                }
            }
        }
    }
}

// ======================= Degree =======================

@Composable
fun DegreeScreen(vm: AppViewModel, nav: NavController) {
    val st = vm.state.collectAsStateWithLifecycle().value ?: return Loading()
    val s by vm.settings.collectAsStateWithLifecycle()
    val date = LocalDate.now()
    val d = DegreeCalc.compute(st, s, Semesters.current(date))
    val bySemester = st.courses.groupBy { it.semester }.entries.sortedByDescending { e -> Semesters.parse(e.key)?.let { p -> p.first * 10 + listOf(0, 2, 1)[p.second] } ?: 0 }

    Screen("התואר שלי", tab = true, actions = { SettingsAction(nav) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = pagePad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!s.degreeSet) item {
                SoftCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎓 בוא נגדיר את התואר", style = MaterialTheme.typography.titleMedium)
                        Text("כמה נקודות זכות צריך לתואר, כמה כבר יש לך ומה הממוצע עד עכשיו.")
                        FilledTonalButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Text("להגדרות") }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressRing(d.progress, 200.dp, MaterialTheme.colorScheme.primary, 18.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(num(d.creditsDone), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                            Text("מתוך ${num(d.creditsRequired)} נק\"ז", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(d.average?.let { "%.1f".format(it) } ?: "–", "ממוצע משוקלל", Modifier.weight(1f))
                    Stat(num((d.creditsRequired - d.creditsDone).coerceAtLeast(0.0)), "נק\"ז שנשארו", Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                    Stat(d.expectedFinish ?: "–", "סיום משוער", Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                }
            }
            if (d.creditsInProgress > 0 || d.pace > 0) item {
                Text(
                    buildString {
                        if (d.creditsInProgress > 0) append("${num(d.creditsInProgress)} נק\"ז בתהליך עכשיו. ")
                        if (d.pace > 0) append("הקצב שלך: ~${num(d.pace)} נק\"ז לסמסטר" + (d.semestersLeft?.let { ", כלומר עוד $it סמסטרים." } ?: "."))
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            bySemester.forEach { (sem, courses) ->
                item(key = "s_$sem") { SectionTitle("סמסטר $sem") }
                items(courses, key = { "d_${it.id}" }) { c ->
                    val f = Grades.finalGrade(c, st.assignmentsOf(c.id))
                    SoftCard(onClick = { nav.navigate(Routes.course(c.id)) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CourseBadge(c.name, courseColor(c.color), 36.dp)
                            Text(c.name, Modifier.weight(1f).padding(horizontal = 10.dp))
                            Text("${num(c.credits)} נק\"ז", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
                            if (f != null) Pill("$f", if (f >= Grades.PASS) Ok else com.matan.study.ui.Danger, filled = true)
                            else Pill(if (c.status == CourseStatus.PLANNED) "מתוכנן" else "בתהליך")
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Text("עריכת נתוני התואר") } }
        }
    }
}
