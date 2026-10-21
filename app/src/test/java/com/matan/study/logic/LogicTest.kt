package com.matan.study.logic

import com.matan.study.data.Assignment
import com.matan.study.data.AssignmentKind
import com.matan.study.data.Card
import com.matan.study.data.Course
import com.matan.study.data.CourseStatus
import com.matan.study.data.Settings
import com.matan.study.data.StudyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    private val course = Course(id = 1, name = "סטטיסטיקה", credits = 6.0, semester = "2026א")
    private fun a(grade: Int?, weight: Double) = Assignment(courseId = 1, kind = AssignmentKind.MAMAN, number = 11, weight = weight, grade = grade)

    @Test fun magen_assignmentsHelpOnlyWhenBetter() {
        val good = listOf(a(100, 10.0), a(100, 10.0))
        assertEquals(84, Grades.finalGrade(course, good, 80)) // 0.8*80 + 20 = 84
        val bad = listOf(a(50, 10.0), a(50, 10.0))
        assertEquals(80, Grades.finalGrade(course, bad, 80)) // exam alone is better
    }

    @Test fun magen_failedExamIsFail() {
        assertEquals(55, Grades.finalGrade(course, listOf(a(100, 20.0)), 55))
        assertNull(Grades.finalGrade(course, emptyList(), null))
    }

    @Test fun examNeeded() {
        val e = Grades.examNeededFor(course, listOf(a(100, 20.0)), 90)!!
        assertEquals(87, e) // 0.8*87 + 20 = 89.6, rounds to 90
    }

    @Test fun degree_weightedAverageAndProgress() {
        val st = StudyState(
            courses = listOf(course.copy(examGrade = 90, status = CourseStatus.DONE), course.copy(id = 2, credits = 4.0, examGrade = 80, status = CourseStatus.DONE)),
        )
        val s = Settings(creditsBefore = 10.0, averageBefore = 85.0, creditsRequired = 120.0)
        val d = DegreeCalc.compute(st, s, "2026ב")
        assertEquals(20.0, d.creditsDone, 0.001)
        assertEquals((85 * 10 + 90 * 6 + 80 * 4) / 20.0, d.average!!, 0.001)
    }

    @Test fun semesters() {
        assertEquals("2026ב", Semesters.add("2026א", 1, 2))
        assertEquals("2027א", Semesters.add("2026א", 2, 2))
        assertEquals("2026ג", Semesters.add("2026א", 1, 3))
        assertEquals("2026ב", Semesters.add("2026א", 2, 3))
        assertEquals("2026ב", Semesters.current(LocalDate.of(2026, 10, 22)))
        assertEquals("2026ב", Semesters.current(LocalDate.of(2027, 1, 10)))
    }

    @Test fun srs_intervalsGrowAndExamCaps() {
        var c = Card(courseId = 1, front = "q", back = "a")
        c = Srs.review(c, Answer.GOOD, 100)
        assertEquals(101L, c.dueDay)
        c = Srs.review(c, Answer.GOOD, 101)
        assertEquals(3, c.intervalDays)
        c = Srs.review(c, Answer.GOOD, 104)
        assertTrue(c.intervalDays > 3)
        val capped = Srs.review(c, Answer.EASY, 104, examDay = 108)
        assertTrue(capped.dueDay <= 107)
        val again = Srs.review(c, Answer.AGAIN, 104)
        assertEquals(104L, again.dueDay)
        assertEquals(1, again.lapses)
    }
}
