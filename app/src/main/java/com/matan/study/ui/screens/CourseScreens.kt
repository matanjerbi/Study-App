package com.matan.study.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.study.data.Assignment
import com.matan.study.data.AssignmentKind
import com.matan.study.data.Course
import com.matan.study.data.CourseStatus
import com.matan.study.data.ExamType
import com.matan.study.logic.Agenda
import com.matan.study.logic.DegreeCalc
import com.matan.study.logic.Grades
import com.matan.study.logic.Semesters
import com.matan.study.logic.Srs
import com.matan.study.logic.label
import com.matan.study.logic.title
import com.matan.study.ui.AppViewModel
import com.matan.study.ui.ChoiceChips
import com.matan.study.ui.ColorPicker
import com.matan.study.ui.ConfirmDialog
import com.matan.study.ui.CourseBadge
import com.matan.study.ui.Danger
import com.matan.study.ui.DateField
import com.matan.study.ui.DaysPill
import com.matan.study.ui.Dropdown
import com.matan.study.ui.Loading
import com.matan.study.ui.NumberField
import com.matan.study.ui.Ok
import com.matan.study.ui.Pill
import com.matan.study.ui.Routes
import com.matan.study.ui.Screen
import com.matan.study.ui.SectionTitle
import com.matan.study.ui.SoftCard
import com.matan.study.ui.Stat
import com.matan.study.ui.SwitchRow
import com.matan.study.ui.TextInput
import com.matan.study.ui.courseColor
import com.matan.study.ui.fmtDay
import com.matan.study.ui.hours
import com.matan.study.ui.num
import com.matan.study.ui.toD
import com.matan.study.ui.toI
import com.matan.study.ui.toast
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

fun ExamType.label() = when (this) {
    ExamType.MULTIPLE_CHOICE -> "אמריקאית"
    ExamType.OPEN -> "פתוחה"
    ExamType.MIXED -> "משולבת"
}

fun CourseStatus.label() = when (this) {
    CourseStatus.ACTIVE -> "פעיל"
    CourseStatus.PLANNED -> "מתוכנן"
    CourseStatus.DONE -> "הושלם"
}

// ======================= Course =======================

@Composable
fun CourseScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val st = vm.state.collectAsStateWithLifecycle().value ?: return Loading()
    val s by vm.settings.collectAsStateWithLifecycle()
    val c = st.course(id) ?: return Loading()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val color = courseColor(c.color)
    val t = LocalDate.now().toEpochDay()
    val ws = weekStart(LocalDate.now())
    val assignments = st.assignmentsOf(id)
    val final = Grades.finalGrade(c, assignments)
    val avg = Grades.assignmentsAverage(assignments)
    val cards = st.cardsOf(id)
    val due = Srs.due(cards, t)
    val sessions = st.sessionsOf(id)
    val nextExam = Agenda.nextExam(c, t)

    var editing by remember { mutableStateOf<Assignment?>(null) }
    var examDialog by remember { mutableStateOf(false) }
    var minutesDialog by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var whatIf by remember { mutableFloatStateOf(85f) }
    var notes by remember(id) { mutableStateOf(c.notes) }

    Screen("", nav, actions = {
        IconButton(onClick = { nav.navigate(Routes.courseEdit(id)) }) { Icon(Icons.Default.Edit, "עריכה") }
        IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, "מחק") }
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // banner
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.1f))), RoundedCornerShape(28.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CourseBadge(c.name, color, 52.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.name, style = MaterialTheme.typography.headlineSmall)
                        Text(listOf(c.number, c.semester).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${num(c.credits)} נק\"ז", color)
                    Pill(c.status.label(), color)
                    Pill("בחינה ${c.examType.label()}", color)
                }
            }

            // ---- grade ----
            SectionTitle("ציון")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(c.examGrade?.toString() ?: "–", "בחינה", Modifier.weight(1f).clickable { examDialog = true }, color)
                Stat(avg?.let { "%.0f".format(it) } ?: "–", "ממוצע מטלות", Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                Stat(final?.toString() ?: "–", "ציון סופי", Modifier.weight(1f), if (final == null) MaterialTheme.colorScheme.tertiary else if (final >= Grades.PASS) Ok else Danger)
            }
            if (c.examGrade == null) {
                SoftCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val e = whatIf.roundToInt()
                        val f = Grades.finalGrade(c.copy(finalOverride = null), assignments, e)
                        val deg = DegreeCalc.compute(st, s, Semesters.current(LocalDate.now()), c to e)
                        Text("🔮 מה אם אקבל $e בבחינה?", style = MaterialTheme.typography.titleMedium)
                        Slider(whatIf, { whatIf = it }, valueRange = 60f..100f, steps = 39)
                        Text("ציון סופי: $f" + (deg.average?.let { " · ממוצע התואר: ${"%.2f".format(it)}" } ?: ""))
                        val needs = listOf(70, 80, 90).mapNotNull { target -> Grades.examNeededFor(c, assignments, target)?.let { target to it } }
                        if (needs.isNotEmpty()) {
                            Text(needs.joinToString(" · ") { (target, ex) -> "ל-$target צריך $ex" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedButton(onClick = { examDialog = true }) { Text("הזנת ציון בחינה") }
            }

            // ---- assignments ----
            SectionTitle("מטלות")
            if (assignments.isEmpty()) Text("אין מטלות בקורס", style = MaterialTheme.typography.bodySmall)
            assignments.forEach { a ->
                SoftCard(onClick = { editing = a }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (a.submitted) "✅" else if (a.kind == AssignmentKind.MAMAN) "📝" else "💻")
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(a.title(), style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(a.dueDate?.let { "הגשה ${fmtDay(it)}" } ?: "לא הוזן תאריך", "משקל ${num(a.weight)}%").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            a.grade != null -> Pill("${a.grade}", color, filled = true)
                            !a.submitted && a.dueDate != null && a.dueDate >= t -> DaysPill(a.dueDate - t)
                            a.submitted -> Pill("הוגש", Ok)
                        }
                    }
                }
            }
            TextButton(onClick = { editing = Assignment(courseId = id, kind = AssignmentKind.MAMAN, number = 10 + assignments.count { it.kind == AssignmentKind.MAMAN } + 1) }) {
                Text("+ מטלה")
            }

            // ---- exam ----
            SectionTitle("בחינה")
            SoftCard(onClick = { nav.navigate(Routes.courseEdit(id)) }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("בחינה ${c.examType.label()}", style = MaterialTheme.typography.titleMedium)
                    Text("מועד א': ${c.examDateA?.let { fmtDay(it) } ?: "לא הוזן"} · מועד ב': ${c.examDateB?.let { fmtDay(it) } ?: "לא הוזן"}",
                        style = MaterialTheme.typography.bodySmall)
                    nextExam?.let { Row { Text("הבחינה הבאה ", style = MaterialTheme.typography.bodySmall); DaysPill(it - t) } }
                }
            }

            // ---- cards ----
            SectionTitle("כרטיסיות")
            SoftCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${cards.size} כרטיסיות · ${due.size} לחזרה היום")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (due.isNotEmpty()) Button(onClick = { nav.navigate(Routes.review(id)) }) { Text("חזרה") }
                        if (cards.isNotEmpty()) FilledTonalButton(onClick = { nav.navigate(Routes.review(id, cram = true)) }) { Text("חזרה מרוכזת") }
                        OutlinedButton(onClick = { nav.navigate(Routes.manage(id)) }) { Text(if (cards.isEmpty()) "+ כרטיסיות" else "ניהול") }
                    }
                    if (nextExam != null && nextExam - t <= 14 && cards.isNotEmpty()) {
                        Text("הבחינה קרובה – החזרות מתוזמנות כך שהחומר יהיה טרי ביום הבחינה", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            // ---- study time ----
            SectionTitle("זמן לימוד")
            SoftCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val week = sessions.filter { it.day >= ws }.sumOf { it.minutes }
                    Text("השבוע ${hours(week)} · סה\"כ ${hours(sessions.sumOf { it.minutes })}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!vm.timerRunning) Button(onClick = { vm.startTimer(id); context.toast("הטיימר רץ – בהצלחה! 📖") }) { Text("▶ התחל טיימר") }
                        else if (vm.timerCourse == id) Button(onClick = { val m = vm.stopTimer(); context.toast("נשמרו $m דקות") }) { Text("■ עצור ושמור") }
                        OutlinedButton(onClick = { minutesDialog = true }) { Text("+ הוספה ידנית") }
                    }
                }
            }

            // ---- notes ----
            SectionTitle("הערות")
            TextInput("הערות לקורס", notes, { notes = it }, multiline = true)
            if (notes != c.notes) Button(onClick = { scope.launch { vm.repo.saveCourse(c.copy(notes = notes)) } }) { Text("שמור הערות") }
            HorizontalDivider(Modifier.padding(vertical = 40.dp), color = androidx.compose.ui.graphics.Color.Transparent)
        }
    }

    editing?.let { a ->
        AssignmentDialog(a, onSave = { scope.launch { vm.repo.saveAssignment(it) } },
            onDelete = if (a.id == 0L) null else ({ scope.launch { vm.repo.deleteAssignment(a.id) } }),
            onDismiss = { editing = null })
    }
    if (examDialog) {
        var g by remember { mutableStateOf(c.examGrade?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { examDialog = false },
            title = { Text("ציון בחינה") },
            text = { NumberField("ציון", g, { g = it }) },
            confirmButton = {
                TextButton(onClick = {
                    val grade = g.toI()?.coerceIn(0, 100)
                    val updated = c.copy(examGrade = grade, status = if (grade != null) CourseStatus.DONE else c.status)
                    scope.launch { vm.repo.saveCourse(updated) }
                    examDialog = false
                    if (grade != null) {
                        val f = Grades.finalGrade(updated, assignments)
                        context.toast(if ((f ?: 0) >= Grades.PASS) "מזל טוב! ציון סופי $f 🎉" else "ציון סופי $f – יש מועד ב'")
                    }
                }) { Text("שמור") }
            },
            dismissButton = { TextButton(onClick = { examDialog = false }) { Text("ביטול") } },
        )
    }
    if (minutesDialog) {
        var m by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { minutesDialog = false },
            title = { Text("כמה דקות למדת היום?") },
            text = { NumberField("דקות", m, { m = it }) },
            confirmButton = {
                TextButton(onClick = {
                    m.toI()?.let { min -> scope.launch { vm.repo.addSession(id, t, min) } }
                    minutesDialog = false
                }) { Text("הוסף") }
            },
            dismissButton = { TextButton(onClick = { minutesDialog = false }) { Text("ביטול") } },
        )
    }
    if (askDelete) {
        ConfirmDialog("למחוק את ${c.name} עם כל המטלות, הכרטיסיות וזמני הלימוד?", onConfirm = {
            scope.launch { vm.repo.deleteCourse(id) }
            nav.popBackStack()
        }, onDismiss = { askDelete = false })
    }
}

@Composable
fun AssignmentDialog(a: Assignment, onSave: (Assignment) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(a.kind) }
    var number by remember { mutableStateOf(a.number.toString()) }
    var due by remember { mutableStateOf(a.dueDate) }
    var weight by remember { mutableStateOf(num(a.weight)) }
    var grade by remember { mutableStateOf(a.grade?.toString() ?: "") }
    var submitted by remember { mutableStateOf(a.submitted) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (a.id == 0L) "מטלה חדשה" else a.title()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChips(AssignmentKind.entries, kind, { it.label() }) { kind = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("מספר", number, { number = it }, Modifier.weight(1f))
                    NumberField("משקל %", weight, { weight = it }, Modifier.weight(1f))
                }
                DateField("תאריך הגשה", due, { due = it })
                SwitchRow("הוגשה", submitted) { submitted = it }
                NumberField("ציון (לא חובה)", grade, { grade = it; if (it.isNotBlank()) submitted = true })
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("מחק מטלה", color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(a.copy(kind = kind, number = number.toI() ?: a.number, dueDate = due, weight = weight.toD() ?: 0.0,
                    grade = grade.toI()?.coerceIn(0, 100), submitted = submitted))
                onDismiss()
            }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

// ======================= Course edit =======================

@Composable
fun CourseEditScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val st = vm.state.collectAsStateWithLifecycle().value ?: return Loading()
    val existing = st.course(id)
    val scope = rememberCoroutineScope()
    val date = LocalDate.now()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var number by remember { mutableStateOf(existing?.number ?: "") }
    var credits by remember { mutableStateOf(existing?.credits?.let { num(it) } ?: "") }
    var semester by remember { mutableStateOf(existing?.semester ?: Semesters.current(date)) }
    var status by remember { mutableStateOf(existing?.status ?: CourseStatus.ACTIVE) }
    var color by remember { mutableIntStateOf(existing?.color ?: (st.courses.size % 8)) }
    var examType by remember { mutableStateOf(existing?.examType ?: ExamType.MIXED) }
    var examA by remember { mutableStateOf(existing?.examDateA) }
    var examB by remember { mutableStateOf(existing?.examDateB) }
    var finalOverride by remember { mutableStateOf(existing?.finalOverride?.toString() ?: "") }
    var mamans by remember { mutableStateOf("4") }
    var mamachs by remember { mutableStateOf("0") }
    var weight by remember { mutableStateOf("20") }

    val ok = name.isNotBlank() && credits.toD() != null
    Screen(if (existing == null) "קורס חדש" else "עריכת קורס", nav, actions = {
        TextButton(enabled = ok, onClick = {
            val c = Course(
                id = existing?.id ?: 0, name = name.trim(), number = number.trim(), credits = credits.toD() ?: 0.0, semester = semester,
                status = status, color = color, examType = examType, examDateA = examA, examDateB = examB,
                examGrade = existing?.examGrade, finalOverride = finalOverride.toI(), notes = existing?.notes ?: "",
            )
            scope.launch {
                if (existing == null) {
                    val newId = vm.repo.addCourse(c, mamans.toI() ?: 0, mamachs.toI() ?: 0, weight.toD() ?: 0.0)
                    nav.popBackStack()
                    nav.navigate(Routes.course(newId))
                } else {
                    vm.repo.saveCourse(c); nav.popBackStack()
                }
            }
        }) { Text("שמור") }
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextInput("שם הקורס", name, { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("מספר קורס", number, { number = it }, Modifier.weight(1f))
                NumberField("נקודות זכות", credits, { credits = it }, Modifier.weight(1f))
            }
            Dropdown("סמסטר", Semesters.options(date), semester, { it }, { semester = it })
            ChoiceChips(CourseStatus.entries, status, { it.label() }) { status = it }
            Text("צבע", style = MaterialTheme.typography.titleSmall)
            ColorPicker(color) { color = it }

            if (existing == null) {
                SectionTitle("מטלות")
                Text("ייווצרו אוטומטית – אחר כך רק ממלאים תאריכים", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("ממ\"נים", mamans, { mamans = it }, Modifier.weight(1f))
                    NumberField("ממ\"חים", mamachs, { mamachs = it }, Modifier.weight(1f))
                }
                NumberField("משקל כל המטלות יחד (%)", weight, { weight = it })
                Text("המשקל מתחלק שווה בין המטלות, ואפשר לשנות כל אחת בנפרד. לפי עקרון ציון המגן – המטלות נספרות רק אם הן משפרות את הציון.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("בחינה")
            ChoiceChips(ExamType.entries, examType, { it.label() }) { examType = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("מועד א'", examA, { examA = it }, Modifier.weight(1f))
                DateField("מועד ב'", examB, { examB = it }, Modifier.weight(1f))
            }
            if (existing != null) {
                NumberField("ציון סופי רשמי (אם שונה מהחישוב)", finalOverride, { finalOverride = it })
            }
        }
    }
}
