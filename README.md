# Study

Personal Android app for an Open University BA: courses, assignments (ממ"נים / ממ"חים), exams, grades, degree progress, study time and spaced-repetition flashcards. All data stays on the phone.

**Install:** [Study.apk](https://github.com/matanjerbi/Study-App/releases/latest/download/Study.apk) – every push to `main` builds a new APK (GitHub Actions); it installs as an update and keeps your data.

- **Today** – next deadline, study timer, cards due, weekly goal ring, degree progress.
- **Courses** – quick add (credits, semester, number of ממ"נים/ממ"חים and their total weight → rows are created for you), assignments with dates, weights and grades, exam type and dates A/B.
- **Grades** – final grade with the "ציון מגן" rule (assignments count only if they help; exam < 60 fails), what-if slider, exam grade needed for 70/80/90.
- **Degree** – credits done/required, weighted average (including what you had before the app), pace and expected finish semester.
- **Cards** – SM-2 style spaced repetition, intervals capped so every card comes back before the exam, cram mode (weakest first).
- **Reminders** – daily push: assignments 7 / 1 days before, exams 14 / 3 / 1 days before, cards due.
- JSON backup / restore.

Kotlin, Jetpack Compose (Material 3), Room, WorkManager. Logic + tests: `logic/Logic.kt`, `./gradlew testDebugUnitTest`.
