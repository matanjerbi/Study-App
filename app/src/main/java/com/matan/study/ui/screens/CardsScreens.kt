package com.matan.study.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.study.data.Card
import com.matan.study.logic.Agenda
import com.matan.study.logic.Answer
import com.matan.study.logic.Srs
import com.matan.study.ui.AppViewModel
import com.matan.study.ui.CourseBadge
import com.matan.study.ui.Danger
import com.matan.study.ui.DaysPill
import com.matan.study.ui.Loading
import com.matan.study.ui.Ok
import com.matan.study.ui.Routes
import com.matan.study.ui.Screen
import com.matan.study.ui.SoftCard
import com.matan.study.ui.Stat
import com.matan.study.ui.TextInput
import com.matan.study.ui.Warn
import com.matan.study.ui.courseColor
import kotlinx.coroutines.launch
import java.time.LocalDate

// ======================= Cards tab =======================

@Composable
fun CardsScreen(vm: AppViewModel, nav: NavController) {
    val st = vm.state.collectAsStateWithLifecycle().value ?: return Loading()
    val t = LocalDate.now().toEpochDay()
    val due = Srs.due(st.cards, t)
    val courses = st.courses.sortedBy { it.status.ordinal }

    Screen("כרטיסיות", tab = true, actions = { SettingsAction(nav) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${due.size}", "לחזרה היום", Modifier.weight(1f))
                    Stat("${st.cards.size}", "כרטיסיות", Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                    Stat("${st.cards.count { it.intervalDays >= 21 }}", "כבר בזיכרון", Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                }
            }
            if (due.isNotEmpty()) item {
                Button(onClick = { nav.navigate(Routes.review(0)) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                    Text("🧠 חזרה על הכל (${due.size})")
                }
            }
            if (courses.isEmpty()) item { Text("הוסף קורס כדי ליצור כרטיסיות", Modifier.padding(16.dp)) }
            items(courses, key = { it.id }) { c ->
                val cards = st.cardsOf(c.id)
                val cDue = Srs.due(cards, t).size
                val exam = Agenda.nextExam(c, t)
                SoftCard(onClick = { nav.navigate(Routes.manage(c.id)) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CourseBadge(c.name, courseColor(c.color), 40.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text("${cards.size} כרטיסיות · $cDue לחזרה", style = MaterialTheme.typography.bodySmall)
                            }
                            exam?.let { DaysPill(it - t) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cDue > 0) Button(onClick = { nav.navigate(Routes.review(c.id)) }) { Text("חזרה") }
                            if (cards.isNotEmpty()) FilledTonalButton(onClick = { nav.navigate(Routes.review(c.id, cram = true)) }) { Text("מרוכז") }
                            OutlinedButton(onClick = { nav.navigate(Routes.manage(c.id)) }) { Text(if (cards.isEmpty()) "+ צור כרטיסיות" else "ניהול") }
                        }
                    }
                }
            }
        }
    }
}

// ======================= Review =======================

@Composable
fun ReviewScreen(vm: AppViewModel, nav: NavController, courseId: Long, cram: Boolean) {
    val st = vm.state.collectAsStateWithLifecycle().value ?: return Loading()
    val scope = rememberCoroutineScope()
    val t = LocalDate.now().toEpochDay()
    // queue of card ids, built once when the screen opens
    val queue = remember {
        val pool = if (courseId == 0L) st.cards else st.cardsOf(courseId)
        mutableStateListOf<Long>().apply {
            addAll((if (cram) Srs.cramOrder(pool) else Srs.due(pool, t).sortedBy { it.dueDay }).map { it.id })
        }
    }
    val total = remember { queue.size }
    var done by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val title = if (courseId == 0L) "חזרה" else st.course(courseId)?.name ?: "חזרה"

    Screen(if (cram) "$title · מרוכז" else title, nav) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val card: Card? = queue.firstOrNull()?.let { id -> st.cards.find { it.id == id } }
            if (card == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", style = MaterialTheme.typography.displayLarge)
                    Text(if (total == 0) "אין כרטיסיות לחזרה כרגע" else "סיימת! עברת על $done כרטיסיות", style = MaterialTheme.typography.headlineSmall)
                    Text("החזרה הבאה מתוזמנת אוטומטית", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { nav.popBackStack() }, Modifier.padding(top = 20.dp)) { Text("חזרה") }
                }
                return@Column
            }
            val course = st.course(card.courseId)
            val color = courseColor(course?.color ?: 0)
            val exam = course?.let { Agenda.nextExam(it, t) }

            LinearProgressIndicator(progress = { if (total > 0) done.toFloat() / (done + queue.size) else 0f }, Modifier.fillMaxWidth(), color = color)
            Text("${done + 1} / ${done + queue.size}" + (course?.let { " · ${it.name}" } ?: ""), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            val rotation by animateFloatAsState(if (revealed) 180f else 0f, tween(350), label = "flip")
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
                    .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainer)), RoundedCornerShape(28.dp))
                    .clickable { revealed = !revealed }
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val showBack = rotation > 90f
                Column(
                    Modifier.graphicsLayer { rotationY = if (showBack) 180f else 0f }.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(if (showBack) "תשובה" else "שאלה", style = MaterialTheme.typography.labelLarge, color = color)
                    Text(if (showBack) card.back else card.front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    if (!showBack) Text("לחץ כדי לראות את התשובה", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!revealed) {
                Button(onClick = { revealed = true }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("הצג תשובה") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Answer.entries.forEach { ans ->
                        val c = when (ans) { Answer.AGAIN -> Danger; Answer.HARD -> Warn; Answer.GOOD -> Ok; Answer.EASY -> MaterialTheme.colorScheme.primary }
                        Button(
                            onClick = {
                                val updated = Srs.review(card, ans, t, exam)
                                scope.launch { vm.repo.saveCard(updated) }
                                queue.removeAt(0)
                                if (ans == Answer.AGAIN) queue.add(card.id) else done++
                                revealed = false
                            },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c, contentColor = Color.White),
                            contentPadding = PaddingValues(4.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(ans.label)
                                Text(Srs.preview(card, ans, t, exam), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================= Manage cards =======================

@Composable
fun CardsManageScreen(vm: AppViewModel, nav: NavController, courseId: Long) {
    val st = vm.state.collectAsStateWithLifecycle().value ?: return Loading()
    val course = st.course(courseId) ?: return Loading()
    val scope = rememberCoroutineScope()
    val t = LocalDate.now().toEpochDay()
    val cards = st.cardsOf(courseId).sortedByDescending { it.id }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Card?>(null) }
    val color = courseColor(course.color)

    Screen("כרטיסיות · ${course.name}", nav) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SoftCard(color = color.copy(alpha = 0.12f)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("כרטיס חדש", style = MaterialTheme.typography.titleMedium)
                        TextInput("שאלה / מושג", front, { front = it })
                        TextInput("תשובה / הסבר", back, { back = it }, multiline = true)
                        Button(enabled = front.isNotBlank() && back.isNotBlank(), onClick = {
                            scope.launch { vm.repo.saveCard(Card(courseId = courseId, front = front.trim(), back = back.trim(), dueDay = t)) }
                            front = ""; back = ""
                        }) { Text("הוסף") }
                        Text("טיפ: כרטיס טוב שואל דבר אחד. \"מה טען פיאז'ה על שלב האופרציות המוחשיות?\" עדיף על \"פיאז'ה\".",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (cards.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Srs.due(cards, t).isNotEmpty()) Button(onClick = { nav.navigate(Routes.review(courseId)) }) { Text("חזרה עכשיו") }
                    FilledTonalButton(onClick = { nav.navigate(Routes.review(courseId, cram = true)) }) { Text("חזרה מרוכזת") }
                }
            }
            items(cards, key = { it.id }) { c ->
                SoftCard(onClick = { editing = c }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.front, style = MaterialTheme.typography.titleMedium)
                        Text(c.back, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val left = c.dueDay - t
                        Text(
                            if (left <= 0) "לחזרה היום" else "חזרה בעוד $left ימים" + if (c.lapses > 0) " · טעויות: ${c.lapses}" else "",
                            style = MaterialTheme.typography.labelSmall, color = if (left <= 0) color else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }

    editing?.let { c ->
        var f by remember(c.id) { mutableStateOf(c.front) }
        var b by remember(c.id) { mutableStateOf(c.back) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("עריכת כרטיס") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput("שאלה", f, { f = it })
                    TextInput("תשובה", b, { b = it }, multiline = true)
                    TextButton(onClick = { scope.launch { vm.repo.deleteCard(c.id) }; editing = null }) { Text("מחק כרטיס", color = Danger) }
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { vm.repo.saveCard(c.copy(front = f, back = b)) }; editing = null }) { Text("שמור") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("ביטול") } },
        )
    }
}
