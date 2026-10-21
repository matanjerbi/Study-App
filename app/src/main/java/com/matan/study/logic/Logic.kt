package com.matan.study.logic

import com.matan.study.data.Assignment
import com.matan.study.data.AssignmentKind
import com.matan.study.data.Card
import com.matan.study.data.Course
import com.matan.study.data.CourseStatus
import com.matan.study.data.Settings
import com.matan.study.data.StudyState
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ======================= grades =======================

object Grades {
    const val PASS = 60

    /**
     * Open University "ציון מגן": assignments count only when they improve the grade.
     * combined = Σ(grade × weight) + exam × (100 − Σweight), unsubmitted assignments count as 0.
     * final = max(exam, combined). An exam below 60 is a fail regardless of assignments.
     */
    fun finalGrade(course: Course, assignments: List<Assignment>, exam: Int? = course.examGrade): Int? {
        course.finalOverride?.let { return it }
        exam ?: return null
        if (exam < PASS) return exam
        val w = assignments.sumOf { it.weight }.coerceIn(0.0, 100.0)
        val fromAssignments = assignments.sumOf { (it.grade ?: 0) * it.weight }
        val combined = (fromAssignments + exam * (100 - w)) / 100.0
        return max(exam.toDouble(), combined).roundToInt()
    }

    /** Average of assignments graded so far (weighted), for display. */
    fun assignmentsAverage(assignments: List<Assignment>): Double? {
        val graded = assignments.filter { it.grade != null && it.weight > 0 }
        if (graded.isEmpty()) return null
        return graded.sumOf { it.grade!! * it.weight } / graded.sumOf { it.weight }
    }

    /** Exam grade needed so the course final reaches [target] (null = impossible / not needed). */
    fun examNeededFor(course: Course, assignments: List<Assignment>, target: Int): Int? {
        for (e in PASS..100) {
            val f = finalGrade(course.copy(finalOverride = null), assignments, e) ?: continue
            if (f >= target) return e
        }
        return null
    }
}

fun AssignmentKind.label() = if (this == AssignmentKind.MAMAN) "ממ\"ן" else "ממ\"ח"
fun Assignment.title() = "${kind.label()} ${if (kind == AssignmentKind.MAMACH) "%02d".format(number) else number}"

// ======================= degree =======================

data class Degree(
    val creditsDone: Double,
    val creditsRequired: Double,
    val creditsInProgress: Double,
    val average: Double?,
    val progress: Float,
    /** semesters left at your pace, null if unknown */
    val semestersLeft: Int?,
    val expectedFinish: String?,
    val pace: Double,
)

object DegreeCalc {
    fun passed(st: StudyState, c: Course): Int? {
        val f = Grades.finalGrade(c, st.assignmentsOf(c.id)) ?: return null
        return if (f >= Grades.PASS) f else null
    }

    fun compute(st: StudyState, s: Settings, currentSemester: String, extra: Pair<Course, Int>? = null): Degree {
        var credits = s.creditsBefore
        var sum = (s.averageBefore ?: 0.0) * (if (s.averageBefore != null) s.creditsBefore else 0.0)
        var weighted = if (s.averageBefore != null) s.creditsBefore else 0.0
        for (c in st.courses) {
            val grade = if (extra != null && extra.first.id == c.id) {
                Grades.finalGrade(c.copy(finalOverride = null), st.assignmentsOf(c.id), extra.second)
            } else passed(st, c)
            if (grade != null && grade >= Grades.PASS) {
                credits += c.credits
                sum += grade * c.credits
                weighted += c.credits
            }
        }
        val inProgress = st.courses.filter { c -> c.status == CourseStatus.ACTIVE && passed(st, c) == null }.sumOf { it.credits }
        val remaining = max(0.0, s.creditsRequired - credits)
        val pace = pace(st, currentSemester)
        val left = if (s.creditsRequired <= 0) null else if (remaining <= 0) 0 else if (pace > 0) ceil(remaining / pace).toInt() else null
        val finish = left?.let { Semesters.add(currentSemester, if (it == 0) 0 else it - 1, s.semestersPerYear) }
        return Degree(
            creditsDone = credits,
            creditsRequired = s.creditsRequired,
            creditsInProgress = inProgress,
            average = if (weighted > 0) sum / weighted else null,
            progress = if (s.creditsRequired > 0) min(1.0, credits / s.creditsRequired).toFloat() else 0f,
            semestersLeft = left,
            expectedFinish = finish,
            pace = pace,
        )
    }

    /** Average credits per semester: from semesters with courses in the app, else the current semester's load. */
    fun pace(st: StudyState, current: String): Double {
        val bySem = st.courses.filter { it.status != CourseStatus.PLANNED }.groupBy { it.semester }.mapValues { e -> e.value.sumOf { it.credits } }
        if (bySem.isEmpty()) return 0.0
        return bySem.values.average()
    }
}

/** Open University semesters: א (spring), ב (fall), ג (summer). "2026א". */
object Semesters {
    private val letters = listOf("א", "ב", "ג")

    fun current(today: java.time.LocalDate): String {
        val m = today.monthValue
        return when (m) {
            in 2..6 -> "${today.year}א"
            in 7..9 -> "${today.year}ג"
            in 10..12 -> "${today.year}ב"
            else -> "${today.year - 1}ב" // January belongs to the fall semester that started in October
        }
    }

    fun parse(s: String): Pair<Int, Int>? {
        val y = s.take(4).toIntOrNull() ?: return null
        val l = letters.indexOf(s.drop(4).trim())
        return if (l < 0) null else y to l
    }

    /** Semester [n] steps after [from]. With 2 per year the summer semester (ג) is skipped. */
    fun add(from: String, n: Int, perYear: Int): String {
        var (y, l) = parse(from) ?: return from
        val order = if (perYear >= 3) listOf(0, 2, 1) else listOf(0, 1) // calendar order: א, ג, ב
        var idx = order.indexOf(l).let { if (it < 0) 0 else it }
        repeat(n) {
            idx++
            if (idx >= order.size) { idx = 0; y++ }
        }
        return "$y${letters[order[idx]]}"
    }

    fun options(today: java.time.LocalDate): List<String> {
        val y = today.year
        return (y - 3..y + 2).flatMap { yr -> listOf("${yr}א", "${yr}ב", "${yr}ג") }
    }
}

// ======================= spaced repetition =======================

enum class Answer(val label: String) { AGAIN("שוב"), HARD("קשה"), GOOD("טוב"), EASY("קל") }

object Srs {
    /**
     * SM-2 style scheduling. If an exam is coming, intervals are capped so the card comes back
     * at least once before the exam (the day before at the latest).
     */
    fun review(card: Card, answer: Answer, today: Long, examDay: Long? = null): Card {
        var ease = card.ease
        var interval: Int
        var lapses = card.lapses
        when (answer) {
            Answer.AGAIN -> { interval = 0; ease -= 0.2; lapses++ }
            Answer.HARD -> { interval = max(1, (card.intervalDays * 1.2).roundToInt()); ease -= 0.15 }
            Answer.GOOD -> interval = when (card.reps) { 0 -> 1; 1 -> 3; else -> max(card.intervalDays + 1, (card.intervalDays * ease).roundToInt()) }
            Answer.EASY -> {
                interval = when (card.reps) { 0 -> 3; 1 -> 5; else -> (card.intervalDays * ease * 1.3).roundToInt() }
                ease += 0.15
            }
        }
        ease = ease.coerceIn(1.3, 3.0)
        if (examDay != null && examDay > today && interval > 0) {
            interval = min(interval, max(1, (examDay - today - 1).toInt()))
        }
        return card.copy(
            ease = ease, intervalDays = interval, dueDay = today + interval,
            reps = if (answer == Answer.AGAIN) 0 else card.reps + 1, lapses = lapses,
        )
    }

    fun preview(card: Card, answer: Answer, today: Long, examDay: Long?): String {
        val d = review(card, answer, today, examDay).intervalDays
        return when (d) { 0 -> "עכשיו"; 1 -> "מחר"; else -> "$d ימים" }
    }

    fun due(cards: List<Card>, today: Long) = cards.filter { it.dueDay <= today }

    /** Cram before an exam: weakest first (most lapses, lowest ease). */
    fun cramOrder(cards: List<Card>) = cards.sortedWith(compareByDescending<Card> { it.lapses }.thenBy { it.ease }.thenBy { it.reps })
}

// ======================= agenda =======================

enum class EventKind { ASSIGNMENT, EXAM_A, EXAM_B }

data class Deadline(val course: Course, val kind: EventKind, val day: Long, val title: String, val assignment: Assignment? = null)

object Agenda {
    /** Upcoming (today or later) unsubmitted assignments and exams of active courses, soonest first. */
    fun upcoming(st: StudyState, today: Long): List<Deadline> {
        val list = mutableListOf<Deadline>()
        st.courses.filter { it.status == CourseStatus.ACTIVE }.forEach { c ->
            st.assignmentsOf(c.id).filter { !it.submitted && it.dueDate != null && it.dueDate >= today }.forEach {
                list += Deadline(c, EventKind.ASSIGNMENT, it.dueDate!!, it.title(), it)
            }
            if (c.examGrade == null) {
                c.examDateA?.takeIf { it >= today }?.let { list += Deadline(c, EventKind.EXAM_A, it, "בחינה – מועד א'") }
                c.examDateB?.takeIf { it >= today && (c.examDateA == null || c.examDateA < today) }?.let {
                    list += Deadline(c, EventKind.EXAM_B, it, "בחינה – מועד ב'")
                }
            }
        }
        return list.sortedBy { it.day }
    }

    /** The next exam of a course, used to time card reviews. */
    fun nextExam(c: Course, today: Long): Long? =
        listOfNotNull(c.examDateA, c.examDateB).filter { it >= today }.minOrNull()

    fun minutesThisWeek(st: StudyState, today: Long, weekStart: Long): Int =
        st.sessions.filter { it.day in weekStart..today }.sumOf { it.minutes }
}

fun daysText(days: Long) = when {
    days == 0L -> "היום"
    days == 1L -> "מחר"
    days < 0 -> "לפני ${-days} ימים"
    else -> "בעוד $days ימים"
}
