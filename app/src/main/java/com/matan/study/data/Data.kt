package com.matan.study.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

// Dates are epoch days (LocalDate.toEpochDay()).

enum class CourseStatus { ACTIVE, PLANNED, DONE }
enum class ExamType { MULTIPLE_CHOICE, OPEN, MIXED }
enum class AssignmentKind { MAMAN, MAMACH }

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val number: String = "",
    val credits: Double,
    /** e.g. "2026א" */
    val semester: String,
    val status: CourseStatus = CourseStatus.ACTIVE,
    val color: Int = 0,
    val examType: ExamType = ExamType.MIXED,
    val examDateA: Long? = null,
    val examDateB: Long? = null,
    val examGrade: Int? = null,
    /** official final grade if it differs from the calculation */
    val finalOverride: Int? = null,
    val notes: String = "",
)

@Entity(tableName = "assignments", indices = [Index("courseId")])
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val kind: AssignmentKind,
    /** e.g. 11 for ממ"ן 11 */
    val number: Int,
    val dueDate: Long? = null,
    /** percent of the final grade */
    val weight: Double = 0.0,
    val grade: Int? = null,
    val submitted: Boolean = false,
)

@Entity(tableName = "cards", indices = [Index("courseId")])
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val front: String,
    val back: String,
    val ease: Double = 2.5,
    val intervalDays: Int = 0,
    val dueDay: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
)

@Entity(tableName = "sessions", indices = [Index("courseId")])
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val day: Long,
    val minutes: Int,
)

@Dao
interface StudyDao {
    @Query("SELECT * FROM courses ORDER BY id") fun courses(): Flow<List<Course>>
    @Query("SELECT * FROM assignments ORDER BY courseId, kind, number") fun assignments(): Flow<List<Assignment>>
    @Query("SELECT * FROM cards ORDER BY id") fun cards(): Flow<List<Card>>
    @Query("SELECT * FROM sessions ORDER BY day") fun sessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM courses") suspend fun allCourses(): List<Course>
    @Query("SELECT * FROM assignments") suspend fun allAssignments(): List<Assignment>
    @Query("SELECT * FROM cards") suspend fun allCards(): List<Card>
    @Query("SELECT * FROM sessions") suspend fun allSessions(): List<StudySession>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCourse(c: Course): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAssignment(a: Assignment): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCard(c: Card): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSession(s: StudySession): Long

    @Query("DELETE FROM courses WHERE id = :id") suspend fun deleteCourse(id: Long)
    @Query("DELETE FROM assignments WHERE id = :id") suspend fun deleteAssignment(id: Long)
    @Query("DELETE FROM assignments WHERE courseId = :cid") suspend fun deleteAssignmentsOf(cid: Long)
    @Query("DELETE FROM cards WHERE id = :id") suspend fun deleteCard(id: Long)
    @Query("DELETE FROM cards WHERE courseId = :cid") suspend fun deleteCardsOf(cid: Long)
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteSession(id: Long)
    @Query("DELETE FROM sessions WHERE courseId = :cid") suspend fun deleteSessionsOf(cid: Long)

    @Insert suspend fun insertCourses(l: List<Course>)
    @Insert suspend fun insertAssignments(l: List<Assignment>)
    @Insert suspend fun insertCards(l: List<Card>)
    @Insert suspend fun insertSessions(l: List<StudySession>)
    @Query("DELETE FROM courses") suspend fun clearCourses()
    @Query("DELETE FROM assignments") suspend fun clearAssignments()
    @Query("DELETE FROM cards") suspend fun clearCards()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
}

@Database(entities = [Course::class, Assignment::class, Card::class, StudySession::class], version = 1, exportSchema = false)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun dao(): StudyDao
}

data class StudyState(
    val courses: List<Course> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val cards: List<Card> = emptyList(),
    val sessions: List<StudySession> = emptyList(),
) {
    fun course(id: Long) = courses.find { it.id == id }
    fun assignmentsOf(cid: Long) = assignments.filter { it.courseId == cid }
    fun cardsOf(cid: Long) = cards.filter { it.courseId == cid }
    fun sessionsOf(cid: Long) = sessions.filter { it.courseId == cid }
}

class Repo(val db: StudyDatabase) {
    val dao = db.dao()

    fun state(): Flow<StudyState> =
        combine(dao.courses(), dao.assignments(), dao.cards(), dao.sessions()) { c, a, k, s -> StudyState(c, a, k, s) }

    /** New course + its assignment rows, created in one go. */
    suspend fun addCourse(c: Course, mamans: Int, mamachs: Int, assignmentsWeight: Double): Long = db.withTransaction {
        val id = dao.saveCourse(c)
        val total = mamans + mamachs
        val each = if (total > 0) assignmentsWeight / total else 0.0
        (1..mamans).forEach { dao.saveAssignment(Assignment(courseId = id, kind = AssignmentKind.MAMAN, number = 10 + it, weight = each)) }
        (1..mamachs).forEach { dao.saveAssignment(Assignment(courseId = id, kind = AssignmentKind.MAMACH, number = 0 + it, weight = each)) }
        id
    }

    suspend fun saveCourse(c: Course) = dao.saveCourse(c)
    suspend fun deleteCourse(id: Long) = db.withTransaction {
        dao.deleteAssignmentsOf(id); dao.deleteCardsOf(id); dao.deleteSessionsOf(id); dao.deleteCourse(id)
    }
    suspend fun saveAssignment(a: Assignment) = dao.saveAssignment(a)
    suspend fun deleteAssignment(id: Long) = dao.deleteAssignment(id)
    suspend fun saveCard(c: Card) = dao.saveCard(c)
    suspend fun deleteCard(id: Long) = dao.deleteCard(id)
    suspend fun addSession(courseId: Long, day: Long, minutes: Int) {
        if (minutes > 0) dao.saveSession(StudySession(courseId = courseId, day = day, minutes = minutes))
    }
    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    // ---------- backup ----------
    suspend fun export(out: OutputStream, settings: Settings) {
        val j = JSONObject().put("version", 1)
            .put("settings", JSONObject().put("creditsBefore", settings.creditsBefore).put("averageBefore", settings.averageBefore ?: JSONObject.NULL)
                .put("creditsRequired", settings.creditsRequired).put("semestersPerYear", settings.semestersPerYear)
                .put("weeklyGoalHours", settings.weeklyGoalHours))
            .put("courses", JSONArray().apply {
                dao.allCourses().forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("number", it.number).put("credits", it.credits)
                        .put("semester", it.semester).put("status", it.status.name).put("color", it.color).put("examType", it.examType.name)
                        .put("examDateA", it.examDateA ?: JSONObject.NULL).put("examDateB", it.examDateB ?: JSONObject.NULL)
                        .put("examGrade", it.examGrade ?: JSONObject.NULL).put("finalOverride", it.finalOverride ?: JSONObject.NULL)
                        .put("notes", it.notes))
                }
            })
            .put("assignments", JSONArray().apply {
                dao.allAssignments().forEach {
                    put(JSONObject().put("id", it.id).put("courseId", it.courseId).put("kind", it.kind.name).put("number", it.number)
                        .put("dueDate", it.dueDate ?: JSONObject.NULL).put("weight", it.weight).put("grade", it.grade ?: JSONObject.NULL)
                        .put("submitted", it.submitted))
                }
            })
            .put("cards", JSONArray().apply {
                dao.allCards().forEach {
                    put(JSONObject().put("id", it.id).put("courseId", it.courseId).put("front", it.front).put("back", it.back)
                        .put("ease", it.ease).put("intervalDays", it.intervalDays).put("dueDay", it.dueDay).put("reps", it.reps).put("lapses", it.lapses))
                }
            })
            .put("sessions", JSONArray().apply {
                dao.allSessions().forEach { put(JSONObject().put("id", it.id).put("courseId", it.courseId).put("day", it.day).put("minutes", it.minutes)) }
            })
        out.write(j.toString(2).toByteArray(Charsets.UTF_8))
    }

    /** Returns the settings stored in the backup. */
    suspend fun import(input: InputStream): Settings {
        val j = JSONObject(input.readBytes().toString(Charsets.UTF_8))
        fun JSONObject.intN(k: String) = if (isNull(k)) null else getInt(k)
        fun JSONObject.longN(k: String) = if (isNull(k)) null else getLong(k)
        fun arr(k: String) = j.getJSONArray(k).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val courses = arr("courses").map {
            Course(it.getLong("id"), it.getString("name"), it.optString("number"), it.getDouble("credits"), it.getString("semester"),
                CourseStatus.valueOf(it.getString("status")), it.optInt("color"), ExamType.valueOf(it.getString("examType")),
                it.longN("examDateA"), it.longN("examDateB"), it.intN("examGrade"), it.intN("finalOverride"), it.optString("notes"))
        }
        val assignments = arr("assignments").map {
            Assignment(it.getLong("id"), it.getLong("courseId"), AssignmentKind.valueOf(it.getString("kind")), it.getInt("number"),
                it.longN("dueDate"), it.getDouble("weight"), it.intN("grade"), it.optBoolean("submitted"))
        }
        val cards = arr("cards").map {
            Card(it.getLong("id"), it.getLong("courseId"), it.getString("front"), it.getString("back"), it.getDouble("ease"),
                it.getInt("intervalDays"), it.getLong("dueDay"), it.getInt("reps"), it.getInt("lapses"))
        }
        val sessions = arr("sessions").map { StudySession(it.getLong("id"), it.getLong("courseId"), it.getLong("day"), it.getInt("minutes")) }
        db.withTransaction {
            dao.clearSessions(); dao.clearCards(); dao.clearAssignments(); dao.clearCourses()
            dao.insertCourses(courses); dao.insertAssignments(assignments); dao.insertCards(cards); dao.insertSessions(sessions)
        }
        val s = j.getJSONObject("settings")
        return Settings(
            creditsBefore = s.getDouble("creditsBefore"),
            averageBefore = if (s.isNull("averageBefore")) null else s.getDouble("averageBefore"),
            creditsRequired = s.getDouble("creditsRequired"),
            semestersPerYear = s.optInt("semestersPerYear", 2),
            weeklyGoalHours = s.optInt("weeklyGoalHours", 8),
        )
    }
}

// ---------- settings ----------

data class Settings(
    /** credits earned before using the app */
    val creditsBefore: Double = 0.0,
    val averageBefore: Double? = null,
    val creditsRequired: Double = 0.0,
    val semestersPerYear: Int = 2,
    val weeklyGoalHours: Int = 8,
    val notifHour: Int = 19,
    val notifMinute: Int = 0,
    val cardReminder: Boolean = true,
) {
    val degreeSet get() = creditsRequired > 0
}

class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(
        Settings(
            creditsBefore = p.getFloat("creditsBefore", 0f).toDouble(),
            averageBefore = p.getFloat("averageBefore", -1f).takeIf { it >= 0 }?.toDouble(),
            creditsRequired = p.getFloat("creditsRequired", 0f).toDouble(),
            semestersPerYear = p.getInt("semestersPerYear", 2),
            weeklyGoalHours = p.getInt("weeklyGoalHours", 8),
            notifHour = p.getInt("notifHour", 19),
            notifMinute = p.getInt("notifMinute", 0),
            cardReminder = p.getBoolean("cardReminder", true),
        )
    )
    val flow: StateFlow<Settings> = state

    fun update(s: Settings) {
        p.edit()
            .putFloat("creditsBefore", s.creditsBefore.toFloat())
            .putFloat("averageBefore", (s.averageBefore ?: -1.0).toFloat())
            .putFloat("creditsRequired", s.creditsRequired.toFloat())
            .putInt("semestersPerYear", s.semestersPerYear)
            .putInt("weeklyGoalHours", s.weeklyGoalHours)
            .putInt("notifHour", s.notifHour)
            .putInt("notifMinute", s.notifMinute)
            .putBoolean("cardReminder", s.cardReminder)
            .apply()
        state.value = s
    }

    /** Running study timer (survives app restarts). */
    var timerCourse: Long
        get() = p.getLong("timerCourse", 0)
        set(v) = p.edit().putLong("timerCourse", v).apply()
    var timerStart: Long
        get() = p.getLong("timerStart", 0)
        set(v) = p.edit().putLong("timerStart", v).apply()
}
