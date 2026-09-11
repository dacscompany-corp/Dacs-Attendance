# Time In / Time Out Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a home-screen widget that shows today's attendance status and opens the existing
four-step Time In / Time Out flow in one tap.

**Architecture:**
- **Widget:** a Jetpack Glance widget renders a `WidgetState`. A pure function computes that
  state from local Room data, so the widget never goes online.
- **Refresh:** the app pushes refreshes through a `WidgetRefresher`. It is called from
  decorators around `AttendanceRepository` and `AuthRepository`, and from the upload worker.
- **Tap:** the widget's button launches `MainActivity` with a one-shot start-flow request. The
  root and the dashboard resolve that request against today's live state before opening the
  flow.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose (BOM 2025.05.01), Jetpack Glance
`glance-appwidget` 1.1.1, Hilt 2.56.2, Room 2.8.4, WorkManager 2.11.2, JUnit 4 +
kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-11-time-widget-design.md`

## Global Constraints

- **The four-step flow is unchanged.** The widget never records attendance and never skips or
  pre-fills a step. It only opens `TimeFlowScreen`.
- **The widget makes no network calls.** It reads Room plus the worker id only.
- **Worker id rule:** `client.auth.currentUserOrNull()?.id ?: workerCache.lastSignedInId`,
  the same as `OfflineAttendanceRepository.currentWorkerId()`.
- **Work date:** always `WorkDate.today()` (Asia/Manila). Widget times are shown in
  Asia/Manila in the phone's 12-hour or 24-hour style.
- **Copy:** English only (v2 redesign rule) and in `res/values/strings.xml`, never hard-coded.
  The widget has no failure notices, so there are no `_tl` strings.
- **Privacy:** the widget shows no name, photo or project.
- **Refresh must never break anything:** it never throws into a caller, and a widget failure
  must not fail a submit, a sync or a sign-out.
- **Package root:** `com.dacs.attendance`. Sources are under
  `app/src/main/java/com/dacs/attendance/`, JVM tests under `app/src/test/java/com/dacs/attendance/`,
  and instrumented tests under `app/src/androidTest/java/com/dacs/attendance/`.
- **Code style:** match the codebase. That means explanatory KDoc and `//` comments that say WHY,
  `runCatchingExceptCancellation` for failable suspend calls, and never swallowing
  `CancellationException`.
- **Gradle invocation (required on this machine):** there is no `java` on PATH, and the user's
  global gradle.properties starves KSP. Every Gradle command in this plan is written as `$G <tasks>`,
  meaning:
  ```bash
  export JAVA_HOME=~/.jdks/jbr-21.0.11
  ./gradlew -Dorg.gradle.jvmargs="-Xmx2560m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8" <tasks>
  ```
- **Emulator:** AVD `Medium_Phone_API_36.1`. Launch it DETACHED from PowerShell:
  ```powershell
  Start-Process -FilePath "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" `
    -ArgumentList '-avd','Medium_Phone_API_36.1','-no-snapshot-load','-no-boot-anim' -WindowStyle Minimized
  ```
  Wait for boot with
  `until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 5; done`.
  adb is at `~/AppData/Local/Android/Sdk/platform-tools/adb.exe`. The debug package is
  `com.dacs.attendance.debug`, and the activity is `com.dacs.attendance.MainActivity`.

## File map

| File | Status | Responsibility |
|---|---|---|
| `domain/WidgetState.kt` | create | `WidgetState` + `widgetStateFor(...)`, pure |
| `data/local/AttendanceDao.kt` | modify | add `PendingSubmissionDao.hasPendingFor(workerId)` |
| `ui/StartFlowRequest.kt` | create | `EXTRA_START_FLOW`, `startFlowFromExtra`, `StartFlowDecision`, `resolveStartFlow`, pure |
| `widget/WidgetRefresher.kt` | create | `WidgetRefresher` interface + `refreshQuietly()` |
| `widget/WidgetAwareRepositories.kt` | create | decorators that refresh after state-changing calls |
| `widget/WidgetStateLoader.kt` | create | reads Room + worker id → `WidgetState` |
| `widget/WidgetEntryPoint.kt` | create | Hilt entry point for the Glance widget |
| `widget/DacsTimeWidget.kt` | create | Glance widget UI + launch intents + `RefreshTick` |
| `widget/DacsTimeWidgetReceiver.kt` | create | `GlanceAppWidgetReceiver` |
| `widget/GlanceWidgetRefresher.kt` | create | the real `WidgetRefresher` |
| `di/WidgetModule.kt` | create | provides the decorated repositories |
| `di/RepositoryModule.kt` | modify | drop the two plain binds; bind `WidgetRefresher` |
| `work/SubmissionWorker.kt` | modify | refresh after every run |
| `data/repo/SupabaseAuthRepository.kt` | modify | sign-out forgets the cached id even offline |
| `MainActivity.kt` | modify | read the widget extra in `onCreate`/`onNewIntent` |
| `ui/RootViewModel.kt` | modify | hold the one-shot start-flow request |
| `ui/AttendanceRoot.kt` | modify | drop/route the request |
| `ui/dashboard/DashboardScreen.kt` | modify | resolve the request against a fresh read of today |
| `AndroidManifest.xml` | modify | `singleTop`, widget `<receiver>` |
| `res/xml/dacs_time_widget_info.xml` | create | widget provider info |
| `res/values/strings.xml` | modify | widget copy |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | modify | Glance dependency |

---

### Task 1: `WidgetState` and `widgetStateFor`

**Files:**
- Create: `app/src/main/java/com/dacs/attendance/domain/WidgetState.kt`
- Test: `app/src/test/java/com/dacs/attendance/domain/WidgetStateTest.kt`

**Interfaces:**
- Consumes: `AttendanceRecord`, `AttendanceStatus`, `TimeDirection`, `WorkDate` (all existing, `domain/`)
- Produces:
  - `sealed interface WidgetState { val notSentYet: Boolean; val action: TimeDirection? }` with
    `SignedOut`, `NotTimedIn(notSentYet)`, `Working(timeInAt: Instant?, notSentYet)`,
    `Complete(timeInAt: Instant?, timeOutAt: Instant?, notSentYet)`, `Abandoned(notSentYet)`
    and `Unknown(notSentYet)`. Every `notSentYet` defaults to `false`.
  - `fun widgetStateFor(workerId: String?, record: AttendanceRecord?, today: WorkDate, hasPending: Boolean): WidgetState`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dacs/attendance/domain/WidgetStateTest.kt`:

```kotlin
package com.dacs.attendance.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The widget is a glance at the home screen, so what it claims has to be
 * exactly what the app would say -- and nothing about anyone else.
 */
class WidgetStateTest {

    private val today = WorkDate(LocalDate.of(2026, 9, 11))
    /** 8:52 AM in Manila. */
    private val timeIn = Instant.parse("2026-09-11T00:52:00Z")
    /** 5:10 PM in Manila. */
    private val timeOut = Instant.parse("2026-09-11T09:10:00Z")

    private fun record(
        status: AttendanceStatus,
        workDate: String = "2026-09-11",
        timeInAt: Instant? = timeIn,
        timeOutAt: Instant? = null
    ) = AttendanceRecord(
        id = "rec-1",
        workDate = workDate,
        status = status,
        timeInAt = timeInAt,
        timeOutAt = timeOutAt,
        timeInProjectName = "ABC Building Project",
        timeOutProjectName = null,
        totalMinutes = null
    )

    @Test
    fun `nobody signed in shows the sign-in state even when a record is lying around`() {
        val state = widgetStateFor(null, record(AttendanceStatus.WORKING), today, hasPending = true)

        assertEquals(WidgetState.SignedOut, state)
        assertNull(state.action)
    }

    @Test
    fun `a blank worker id is nobody`() {
        assertEquals(WidgetState.SignedOut, widgetStateFor("", null, today, hasPending = false))
    }

    @Test
    fun `no record today offers Time In`() {
        val state = widgetStateFor("w1", null, today, hasPending = false)

        assertEquals(WidgetState.NotTimedIn(notSentYet = false), state)
        assertEquals(TimeDirection.IN, state.action)
    }

    @Test
    fun `yesterday's record is not today's`() {
        // The mirror keeps past days for History. Showing yesterday's
        // "Done for today" on this morning's home screen would hide the
        // Time In button from a worker who has not timed in.
        val state = widgetStateFor(
            "w1",
            record(AttendanceStatus.COMPLETE, workDate = "2026-09-10", timeOutAt = timeOut),
            today,
            hasPending = false
        )

        assertEquals(WidgetState.NotTimedIn(notSentYet = false), state)
    }

    @Test
    fun `an open day offers Time Out`() {
        val state = widgetStateFor("w1", record(AttendanceStatus.WORKING), today, hasPending = false)

        assertEquals(WidgetState.Working(timeInAt = timeIn, notSentYet = false), state)
        assertEquals(TimeDirection.OUT, state.action)
    }

    @Test
    fun `a finished day offers nothing`() {
        val state = widgetStateFor(
            "w1",
            record(AttendanceStatus.COMPLETE, timeOutAt = timeOut),
            today,
            hasPending = false
        )

        assertEquals(WidgetState.Complete(timeIn, timeOut, notSentYet = false), state)
        assertNull(state.action)
    }

    @Test
    fun `an abandoned day offers nothing`() {
        val state = widgetStateFor("w1", record(AttendanceStatus.ABANDONED), today, hasPending = false)

        assertEquals(WidgetState.Abandoned(notSentYet = false), state)
        assertNull(state.action)
    }

    @Test
    fun `a status this build does not know offers nothing rather than guessing`() {
        val state = widgetStateFor("w1", record(AttendanceStatus.UNKNOWN), today, hasPending = false)

        assertEquals(WidgetState.Unknown(notSentYet = false), state)
        assertNull(state.action)
    }

    @Test
    fun `a queued submission is flagged not sent yet`() {
        val state = widgetStateFor("w1", record(AttendanceStatus.WORKING), today, hasPending = true)

        assertEquals(WidgetState.Working(timeInAt = timeIn, notSentYet = true), state)
    }

    @Test
    fun `a queued row with no record today is still flagged`() {
        assertEquals(
            WidgetState.NotTimedIn(notSentYet = true),
            widgetStateFor("w1", null, today, hasPending = true)
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `$G :app:testDebugUnitTest --tests "com.dacs.attendance.domain.WidgetStateTest"`
Expected: the build FAILS with `Unresolved reference 'widgetStateFor'` and `'WidgetState'`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/dacs/attendance/domain/WidgetState.kt`:

```kotlin
package com.dacs.attendance.domain

import java.time.Instant

/**
 * What the home-screen widget shows.
 *
 * Deliberately says nothing about WHO: no name, no project, no photo. A
 * site phone lies on a table in front of everyone, and the widget is
 * readable without unlocking it.
 */
sealed interface WidgetState {

    /** This worker still has a submission queued on the phone. */
    val notSentYet: Boolean

    data object SignedOut : WidgetState {
        override val notSentYet: Boolean = false
    }

    data class NotTimedIn(override val notSentYet: Boolean = false) : WidgetState

    /** [timeInAt] is null only for a mirror row that lost it; the line then omits the time. */
    data class Working(
        val timeInAt: Instant?,
        override val notSentYet: Boolean = false
    ) : WidgetState

    data class Complete(
        val timeInAt: Instant?,
        val timeOutAt: Instant?,
        override val notSentYet: Boolean = false
    ) : WidgetState

    data class Abandoned(override val notSentYet: Boolean = false) : WidgetState

    /**
     * A status this build cannot name, or a read that failed. No button:
     * guessing IN or OUT here is how a worker ends up with a photo the
     * server refuses.
     */
    data class Unknown(override val notSentYet: Boolean = false) : WidgetState

    /** The one button the widget offers, or null for none. Same rule as Home's. */
    val action: TimeDirection?
        get() = when (this) {
            is NotTimedIn -> TimeDirection.IN
            is Working -> TimeDirection.OUT
            else -> null
        }
}

/**
 * Today's widget, from what the phone already knows.
 *
 * [record] must already be scoped to [workerId] -- the caller reads it
 * with that id. The date is checked here because the mirror keeps past
 * days, and yesterday's "done" must not hide this morning's Time In.
 */
fun widgetStateFor(
    workerId: String?,
    record: AttendanceRecord?,
    today: WorkDate,
    hasPending: Boolean
): WidgetState {
    if (workerId.isNullOrBlank()) return WidgetState.SignedOut

    val todays = record?.takeIf { it.workDate == today.toString() }
        ?: return WidgetState.NotTimedIn(hasPending)

    return when (todays.status) {
        AttendanceStatus.WORKING -> WidgetState.Working(todays.timeInAt, hasPending)
        AttendanceStatus.COMPLETE ->
            WidgetState.Complete(todays.timeInAt, todays.timeOutAt, hasPending)
        AttendanceStatus.ABANDONED -> WidgetState.Abandoned(hasPending)
        AttendanceStatus.UNKNOWN -> WidgetState.Unknown(hasPending)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `$G :app:testDebugUnitTest --tests "com.dacs.attendance.domain.WidgetStateTest"`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dacs/attendance/domain/WidgetState.kt app/src/test/java/com/dacs/attendance/domain/WidgetStateTest.kt
git commit -m "feat(widget): WidgetState, the widget's view of today"
```

---

### Task 2: `hasPendingFor`, the worker-scoped queue check

**Files:**
- Modify: `app/src/main/java/com/dacs/attendance/data/local/AttendanceDao.kt:29-31` (add the query after `observeAll`)
- Test: `app/src/androidTest/java/com/dacs/attendance/data/local/PendingSubmissionDaoTest.kt`

**Interfaces:**
- Consumes: `PendingSubmissionEntity`, `AttendanceDatabase` (existing)
- Produces: `suspend fun PendingSubmissionDao.hasPendingFor(workerId: String): Boolean`. It is
  true when that worker has a row with `failedPermanently = 0`.

This is a Room query, so the test is instrumented and needs the emulator running (see Global
Constraints). No schema change is needed: a new query doesn't change the schema, so no
migration and no schema JSON update.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/dacs/attendance/data/local/PendingSubmissionDaoTest.kt`:

```kotlin
package com.dacs.attendance.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Not sent yet" on the home screen. Phones are shared on site, so it must
 * only ever speak about the worker it is showing.
 */
@RunWith(AndroidJUnit4::class)
class PendingSubmissionDaoTest {

    private lateinit var db: AttendanceDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AttendanceDatabase::class.java
        ).build()
    }

    @After
    fun close() = db.close()

    private fun row(eventId: String, workerId: String, failed: Boolean = false) =
        PendingSubmissionEntity(
            eventId = eventId,
            workerId = workerId,
            direction = "IN",
            projectSystem = "pc",
            projectId = "p1",
            projectName = "Site",
            capturedAt = 0L,
            photoLocalPath = "/nowhere.jpg",
            description = null,
            latitude = null,
            longitude = null,
            accuracyMetres = null,
            wasOffline = true,
            failedPermanently = failed,
            createdAt = 0L
        )

    @Test
    fun anEmptyQueueHasNothingPending() = runBlocking {
        assertFalse(db.pendingSubmissions().hasPendingFor("w1"))
    }

    @Test
    fun theWorkersOwnRowCounts() = runBlocking {
        db.pendingSubmissions().insert(row("e1", "w1"))

        assertTrue(db.pendingSubmissions().hasPendingFor("w1"))
    }

    @Test
    fun anotherWorkersRowDoesNotCount() = runBlocking {
        db.pendingSubmissions().insert(row("e1", "w2"))

        assertFalse(db.pendingSubmissions().hasPendingFor("w1"))
    }

    @Test
    fun aPermanentlyFailedRowDoesNotCount() = runBlocking {
        // It will never be sent, so "not sent YET" would promise something
        // the queue has already given up on.
        db.pendingSubmissions().insert(row("e1", "w1", failed = true))

        assertFalse(db.pendingSubmissions().hasPendingFor("w1"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `$G :app:compileDebugAndroidTestKotlin`
Expected: the build FAILS with `Unresolved reference 'hasPendingFor'`.

- [ ] **Step 3: Add the query**

In `app/src/main/java/com/dacs/attendance/data/local/AttendanceDao.kt`, directly after
`fun observeAll(): Flow<List<PendingSubmissionEntity>>` (line 31), add:

```kotlin

    /**
     * Drives the widget's "Not sent yet". Scoped to ONE worker, for the
     * same reason [sendable] is. Permanently failed rows are left out: they
     * will never be sent, and "not sent YET" would promise that they will.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM pending_submission " +
            "WHERE workerId = :workerId AND failedPermanently = 0)"
    )
    suspend fun hasPendingFor(workerId: String): Boolean
```

- [ ] **Step 4: Run the test on the emulator to verify it passes**

Start the emulator detached and wait for boot (see Global Constraints), then run:
`$G :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dacs.attendance.data.local.PendingSubmissionDaoTest`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dacs/attendance/data/local/AttendanceDao.kt app/src/androidTest/java/com/dacs/attendance/data/local/PendingSubmissionDaoTest.kt
git commit -m "feat(widget): worker-scoped hasPendingFor for the not-sent marker"
```

---

### Task 3: The start-flow request decision

**Files:**
- Create: `app/src/main/java/com/dacs/attendance/ui/StartFlowRequest.kt`
- Test: `app/src/test/java/com/dacs/attendance/ui/StartFlowRequestTest.kt`

**Interfaces:**
- Consumes: `AppState` (`ui/RootViewModel.kt`), `DashboardUiState` with its `loading` and
  `nextAction` (`ui/dashboard/DashboardViewModel.kt`), and `TimeDirection`
- Produces:
  - `const val EXTRA_START_FLOW = "com.dacs.attendance.extra.START_FLOW"`
  - `fun startFlowFromExtra(raw: String?): TimeDirection?`
  - `sealed interface StartFlowDecision { data class Open(val direction: TimeDirection); data object StayOnHome; data object Drop; data object Wait }`
  - `fun resolveStartFlow(request: TimeDirection, appState: AppState, flowOpen: Boolean, home: DashboardUiState?): StartFlowDecision`

This lives in `ui/`, not `domain/`, because it decides over `AppState` and `DashboardUiState`,
which are UI types. This is a small deviation from the spec's file table.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dacs/attendance/ui/StartFlowRequestTest.kt`:

```kotlin
package com.dacs.attendance.ui

import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.dashboard.DashboardUiState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A widget tap is a request, not a command. The widget may be showing a
 * day that has moved on, and the worker may be halfway through a capture.
 */
class StartFlowRequestTest {

    private val worker = WorkerProfile(
        id = "w1",
        email = "w1@example.com",
        displayName = "Juan",
        position = null,
        workerNo = 42,
        role = "worker",
        status = "active"
    )
    private val signedIn = AppState.SignedIn(worker)

    private fun working() = AttendanceRecord(
        id = "rec-1",
        workDate = "2026-09-11",
        status = AttendanceStatus.WORKING,
        timeInAt = Instant.parse("2026-09-11T00:52:00Z"),
        timeOutAt = null,
        timeInProjectName = "ABC Building Project",
        timeOutProjectName = null,
        totalMinutes = null
    )

    private fun complete() = working().copy(
        status = AttendanceStatus.COMPLETE,
        timeOutAt = Instant.parse("2026-09-11T09:10:00Z")
    )

    private val homeNoRecord = DashboardUiState(loading = false, record = null)

    // ── the extra ──────────────────────────────────────────────────

    @Test
    fun `the extra names a direction exactly`() {
        assertEquals(TimeDirection.IN, startFlowFromExtra("IN"))
        assertEquals(TimeDirection.OUT, startFlowFromExtra("OUT"))
    }

    @Test
    fun `anything else is no request`() {
        assertNull(startFlowFromExtra(null))
        assertNull(startFlowFromExtra(""))
        assertNull(startFlowFromExtra("in"))
        assertNull(startFlowFromExtra("SIDEWAYS"))
    }

    // ── the gate ───────────────────────────────────────────────────

    @Test
    fun `still loading waits`() {
        assertEquals(
            StartFlowDecision.Wait,
            resolveStartFlow(TimeDirection.IN, AppState.Loading, flowOpen = false, home = null)
        )
    }

    @Test
    fun `signed out, terms, or the offline gate drop the request`() {
        listOf(
            AppState.SignedOut,
            AppState.NeedsTerms(worker),
            AppState.GateUnavailable(worker)
        ).forEach { state ->
            assertEquals(
                StartFlowDecision.Drop,
                resolveStartFlow(TimeDirection.IN, state, flowOpen = false, home = homeNoRecord)
            )
        }
    }

    @Test
    fun `a flow already open is never replaced`() {
        assertEquals(
            StartFlowDecision.Drop,
            resolveStartFlow(TimeDirection.OUT, signedIn, flowOpen = true, home = homeNoRecord)
        )
    }

    // ── Home decides ───────────────────────────────────────────────

    @Test
    fun `Home not read yet waits`() {
        assertEquals(
            StartFlowDecision.Wait,
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = null)
        )
        assertEquals(
            StartFlowDecision.Wait,
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = DashboardUiState(loading = true))
        )
    }

    @Test
    fun `Time In on a fresh day opens the flow`() {
        assertEquals(
            StartFlowDecision.Open(TimeDirection.IN),
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = homeNoRecord)
        )
    }

    @Test
    fun `Time Out on an open day opens the flow`() {
        val home = DashboardUiState(loading = false, record = working())

        assertEquals(
            StartFlowDecision.Open(TimeDirection.OUT),
            resolveStartFlow(TimeDirection.OUT, signedIn, flowOpen = false, home = home)
        )
    }

    @Test
    fun `a stale Time In on an open day stays on Home`() {
        // The widget still said Time In; the day has already started. A
        // photo taken now would only be refused as ALREADY_TIMED_IN.
        val home = DashboardUiState(loading = false, record = working())

        assertEquals(
            StartFlowDecision.StayOnHome,
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = home)
        )
    }

    @Test
    fun `a stale Time Out on a fresh day stays on Home`() {
        assertEquals(
            StartFlowDecision.StayOnHome,
            resolveStartFlow(TimeDirection.OUT, signedIn, flowOpen = false, home = homeNoRecord)
        )
    }

    @Test
    fun `a closed day stays on Home whatever was asked`() {
        val home = DashboardUiState(loading = false, record = complete())

        TimeDirection.entries.forEach { request ->
            assertEquals(
                StartFlowDecision.StayOnHome,
                resolveStartFlow(request, signedIn, flowOpen = false, home = home)
            )
        }
    }

    @Test
    fun `offline with nothing mirrored still opens Time In, as Home does`() {
        val home = DashboardUiState(loading = false, record = null, failure = AttendanceFailure.NoConnection)

        assertEquals(
            StartFlowDecision.Open(TimeDirection.IN),
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = home)
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `$G :app:testDebugUnitTest --tests "com.dacs.attendance.ui.StartFlowRequestTest"`
Expected: the build FAILS with `Unresolved reference 'startFlowFromExtra'` and `'resolveStartFlow'`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/dacs/attendance/ui/StartFlowRequest.kt`:

```kotlin
package com.dacs.attendance.ui

import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.ui.dashboard.DashboardUiState

/** The intent extra the home-screen widget sets: "IN" or "OUT". */
const val EXTRA_START_FLOW = "com.dacs.attendance.extra.START_FLOW"

/** Exact names only. Anything else is treated as no request, never as a guess. */
fun startFlowFromExtra(raw: String?): TimeDirection? =
    TimeDirection.entries.firstOrNull { it.name == raw }

/** What to do with a Time In / Time Out asked for from outside the app. */
sealed interface StartFlowDecision {
    data class Open(val direction: TimeDirection) : StartFlowDecision

    /** Home already shows the true state; the widget was behind. */
    data object StayOnHome : StartFlowDecision

    /** Not now, and not later either: forget it. */
    data object Drop : StartFlowDecision

    /** Not enough known yet to decide. */
    data object Wait : StartFlowDecision
}

/**
 * A widget tap, resolved against what the app knows NOW.
 *
 * The widget can be behind the truth -- a Time In made moments ago, an
 * admin correction -- so it never gets to open the flow on its own say-so.
 * Home's [DashboardUiState.nextAction] is the same decision the big button
 * on Home makes, and the flow opens only when the two agree. Otherwise the
 * worker lands on Home, which is already showing the right thing, instead
 * of taking a photo the server will refuse.
 *
 * [home] is null wherever Home has not been read yet.
 */
fun resolveStartFlow(
    request: TimeDirection,
    appState: AppState,
    flowOpen: Boolean,
    home: DashboardUiState?
): StartFlowDecision = when {
    appState is AppState.Loading -> StartFlowDecision.Wait
    // Login, Terms, or the offline gate. Dropped rather than held: after
    // signing in the worker should arrive on Home, not in a camera.
    appState !is AppState.SignedIn -> StartFlowDecision.Drop
    // The capture in progress is the worker's. A tap on the home screen
    // must not throw away a photo they already took.
    flowOpen -> StartFlowDecision.Drop
    home == null || home.loading -> StartFlowDecision.Wait
    home.nextAction == request -> StartFlowDecision.Open(request)
    else -> StartFlowDecision.StayOnHome
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `$G :app:testDebugUnitTest --tests "com.dacs.attendance.ui.StartFlowRequestTest"`
Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dacs/attendance/ui/StartFlowRequest.kt app/src/test/java/com/dacs/attendance/ui/StartFlowRequestTest.kt
git commit -m "feat(widget): resolve a widget's start-flow request against today"
```

---

### Task 4: `WidgetRefresher` and the refreshing repository decorators

**Files:**
- Create: `app/src/main/java/com/dacs/attendance/widget/WidgetRefresher.kt`
- Create: `app/src/main/java/com/dacs/attendance/widget/WidgetAwareRepositories.kt`
- Test: `app/src/test/java/com/dacs/attendance/widget/WidgetAwareRepositoriesTest.kt`

**Interfaces:**
- Consumes: `AttendanceRepository`, `SubmissionRequest`, `AuthRepository` (`data/repo/`),
  `AttendanceRecord`, `WorkerProfile`
- Produces:
  - `interface WidgetRefresher { suspend fun refresh() }`
  - `internal suspend fun WidgetRefresher.refreshQuietly()`: swallows every non-cancellation
    exception
  - `class WidgetAwareAttendanceRepository(inner: AttendanceRepository, widgets: WidgetRefresher) : AttendanceRepository`:
    refreshes after `submit` and `today`
  - `class WidgetAwareAuthRepository(inner: AuthRepository, widgets: WidgetRefresher) : AuthRepository`:
    refreshes after `signIn` and `signOut`

**Why decorators:** they replace spec §2's call sites 1, 3 and 4. `OfflineAttendanceRepository`
and `SupabaseAuthRepository` need a live `SupabaseClient` and Room, so they can't be JVM-tested.
A decorator can, and it covers the same moments:
- `submit` covers call site 1
- `signIn`/`signOut` cover call site 3
- `today()` covers call site 4, because `DashboardViewModel.refresh()` reconciles through it

Nothing is wired into Hilt yet; that happens in Task 6.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/dacs/attendance/widget/WidgetAwareRepositoriesTest.kt`:

```kotlin
package com.dacs.attendance.widget

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.ProjectSystem
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WorkerProfile
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget follows the app; it must never lead it. A refresh that throws
 * is a widget that lags, and that is acceptable. A submission that fails
 * because of it is not.
 */
class WidgetAwareRepositoriesTest {

    private class CountingRefresher(private val fails: Boolean = false) : WidgetRefresher {
        var refreshes = 0
            private set

        override suspend fun refresh() {
            refreshes++
            if (fails) throw IllegalStateException("no widget host")
        }
    }

    private val record = AttendanceRecord(
        id = "rec-1",
        workDate = "2026-09-11",
        status = AttendanceStatus.WORKING,
        timeInAt = Instant.parse("2026-09-11T00:52:00Z"),
        timeOutAt = null,
        timeInProjectName = "ABC Building Project",
        timeOutProjectName = null,
        totalMinutes = null,
        pending = true
    )

    private val request = SubmissionRequest(
        direction = TimeDirection.IN,
        projectSystem = ProjectSystem.PC,
        projectId = "p1",
        capturedAt = Instant.parse("2026-09-11T00:52:00Z"),
        photo = File("unused.jpg"),
        description = null,
        eventId = "e1"
    )

    private inner class FakeAttendance : AttendanceRepository {
        val submitResult = Result.success(record)
        val todayResult = Result.success<AttendanceRecord?>(record)

        override suspend fun submit(request: SubmissionRequest) = submitResult
        override suspend fun today() = todayResult
        override suspend fun history(fromWorkDate: String, toWorkDate: String) =
            Result.success(listOf(record))
    }

    private class FakeAuth : AuthRepository {
        val worker = WorkerProfile("w1", "w1@example.com", "Juan", null, 42, "worker", "active")
        var signOuts = 0
            private set

        override suspend fun signIn(email: String, password: String) = Result.success(worker)
        override suspend fun signOut() { signOuts++ }
        override suspend fun currentWorker(): WorkerProfile? = worker
        override suspend fun changePassword(newPassword: String) = Result.success(Unit)
    }

    // ── attendance ─────────────────────────────────────────────────

    @Test
    fun `a submission refreshes the widget and returns the inner result`() = runTest {
        val inner = FakeAttendance()
        val widgets = CountingRefresher()

        val result = WidgetAwareAttendanceRepository(inner, widgets).submit(request)

        assertSame(inner.submitResult, result)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `a failing widget never fails the submission`() = runTest {
        val widgets = CountingRefresher(fails = true)

        val result = WidgetAwareAttendanceRepository(FakeAttendance(), widgets).submit(request)

        assertTrue(result.isSuccess)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `reading today refreshes the widget, so a server correction reaches it`() = runTest {
        val inner = FakeAttendance()
        val widgets = CountingRefresher()

        val result = WidgetAwareAttendanceRepository(inner, widgets).today()

        assertSame(inner.todayResult, result)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `history does not touch the widget`() = runTest {
        val widgets = CountingRefresher()

        WidgetAwareAttendanceRepository(FakeAttendance(), widgets).history("2026-09-01", "2026-09-11")

        assertEquals(0, widgets.refreshes)
    }

    // ── auth ───────────────────────────────────────────────────────

    @Test
    fun `signing in refreshes the widget`() = runTest {
        val widgets = CountingRefresher()

        val result = WidgetAwareAuthRepository(FakeAuth(), widgets).signIn("a@b.c", "pw")

        assertTrue(result.isSuccess)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `signing out refreshes the widget so no times outlive the session`() = runTest {
        val inner = FakeAuth()
        val widgets = CountingRefresher()

        WidgetAwareAuthRepository(inner, widgets).signOut()

        assertEquals(1, inner.signOuts)
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun `a failing widget never fails a sign-out`() = runTest {
        val inner = FakeAuth()
        val widgets = CountingRefresher(fails = true)

        WidgetAwareAuthRepository(inner, widgets).signOut()

        assertEquals(1, inner.signOuts)
    }

    @Test
    fun `reading the current worker does not touch the widget`() = runTest {
        val widgets = CountingRefresher()

        WidgetAwareAuthRepository(FakeAuth(), widgets).currentWorker()

        assertEquals(0, widgets.refreshes)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `$G :app:testDebugUnitTest --tests "com.dacs.attendance.widget.WidgetAwareRepositoriesTest"`
Expected: the build FAILS with `Unresolved reference 'WidgetRefresher'`,
`'WidgetAwareAttendanceRepository'` and `'WidgetAwareAuthRepository'`.

- [ ] **Step 3: Write `WidgetRefresher.kt`**

Create `app/src/main/java/com/dacs/attendance/widget/WidgetRefresher.kt`:

```kotlin
package com.dacs.attendance.widget

import kotlinx.coroutines.CancellationException

/**
 * Asks the home-screen widget to re-read today.
 *
 * Pushed by the app rather than pulled on a timer: Android lets a widget
 * wake itself at most every 30 minutes, and a widget that still says "Not
 * timed in" half an hour after the worker timed in is worse than none.
 *
 * An interface so the callers can be tested without a widget host.
 */
interface WidgetRefresher {
    suspend fun refresh()
}

/**
 * [WidgetRefresher.refresh], with every failure swallowed.
 *
 * The widget is a convenience riding on top of the queue, the mirror and
 * the session. None of those may ever fail because the widget did.
 * Cancellation still propagates -- swallowing it would break structured
 * concurrency for the caller, not just for the widget.
 */
internal suspend fun WidgetRefresher.refreshQuietly() {
    try {
        refresh()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (ignored: Exception) {
        // A widget that lags until its next refresh is the whole cost.
    }
}
```

- [ ] **Step 4: Write `WidgetAwareRepositories.kt`**

Create `app/src/main/java/com/dacs/attendance/widget/WidgetAwareRepositories.kt`:

```kotlin
package com.dacs.attendance.widget

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.SubmissionRequest
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.WorkerProfile

/**
 * The attendance repository, telling the widget whenever today may have
 * changed.
 *
 * A decorator rather than calls sprinkled through the offline repository:
 * that class needs a live Supabase client and Room, and this rule -- the
 * widget follows every write -- deserves a test.
 *
 * [submit] is the optimistic write, so the widget flips to "Timed in" the
 * moment the worker submits, signal or not. [today] is where Home
 * reconciles with the server, so a correction made there reaches the
 * widget too.
 */
class WidgetAwareAttendanceRepository(
    private val inner: AttendanceRepository,
    private val widgets: WidgetRefresher
) : AttendanceRepository by inner {

    override suspend fun submit(request: SubmissionRequest): Result<AttendanceRecord> =
        inner.submit(request).also { widgets.refreshQuietly() }

    override suspend fun today(): Result<AttendanceRecord?> =
        inner.today().also { widgets.refreshQuietly() }
}

/**
 * The auth repository, telling the widget when the worker changes.
 *
 * Sign-out matters most: site phones are shared, and a widget still
 * showing the last worker's times after they signed out is exactly the
 * leak the worker cache is careful to avoid.
 */
class WidgetAwareAuthRepository(
    private val inner: AuthRepository,
    private val widgets: WidgetRefresher
) : AuthRepository by inner {

    override suspend fun signIn(email: String, password: String): Result<WorkerProfile> =
        inner.signIn(email, password).also { widgets.refreshQuietly() }

    override suspend fun signOut() {
        try {
            inner.signOut()
        } finally {
            widgets.refreshQuietly()
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `$G :app:testDebugUnitTest --tests "com.dacs.attendance.widget.WidgetAwareRepositoriesTest"`
Expected: PASS, 8 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dacs/attendance/widget/WidgetRefresher.kt app/src/main/java/com/dacs/attendance/widget/WidgetAwareRepositories.kt app/src/test/java/com/dacs/attendance/widget/WidgetAwareRepositoriesTest.kt
git commit -m "feat(widget): refresh the widget after every write that changes today"
```

---

### Task 5: The Glance widget renders today

**Files:**
- Modify: `gradle/libs.versions.toml` (version + library entry)
- Modify: `app/build.gradle.kts` (dependency)
- Create: `app/src/main/java/com/dacs/attendance/widget/WidgetStateLoader.kt`
- Create: `app/src/main/java/com/dacs/attendance/widget/WidgetEntryPoint.kt`
- Create: `app/src/main/java/com/dacs/attendance/widget/DacsTimeWidget.kt`
- Create: `app/src/main/java/com/dacs/attendance/widget/DacsTimeWidgetReceiver.kt`
- Create: `app/src/main/res/xml/dacs_time_widget_info.xml`
- Modify: `app/src/main/res/values/strings.xml` (widget copy)
- Modify: `app/src/main/AndroidManifest.xml` (`<receiver>`)

**Interfaces:**
- Consumes:
  - `widgetStateFor`, `WidgetState` (Task 1)
  - `hasPendingFor` (Task 2)
  - `EXTRA_START_FLOW` (Task 3)
  - `CachedRecordDao.forDate`, `WorkerCache.lastSignedInId`
  - `internal fun CachedRecordEntity.toDomain()` (`data/repo/OfflineAttendanceRepository.kt:331`)
  - theme colors `Surface`, `TextPrimary`, `TextMuted`, `TextDisabled`, `Green`, `Brown` (`ui/theme/Color.kt`)
  - strings `action_time_in` and `action_time_out` (existing)
- Produces:
  - `class WidgetStateLoader @Inject constructor(...)` with `suspend fun load(now: Instant = Instant.now()): WidgetState`.
    It never throws; a failed read gives `WidgetState.Unknown()`.
  - `interface WidgetEntryPoint { fun widgetStateLoader(): WidgetStateLoader }`
  - `class DacsTimeWidget : GlanceAppWidget`
  - `internal val RefreshTick: Preferences.Key<Long>`
  - `class DacsTimeWidgetReceiver : GlanceAppWidgetReceiver`

At the end of this task, the widget can be placed and shows the correct state. Its button opens
the app; reading the extra comes in Task 7. Push refresh comes in Task 6.

- [ ] **Step 1: Add the Glance dependency**

In `gradle/libs.versions.toml`, under `[versions]` directly after `playServicesLocation = "21.3.0"`,
add:

```toml

# The home-screen widget. Glance rather than hand-written RemoteViews:
# it is Compose-shaped, like the rest of the UI.
glance = "1.1.1"
```

Under `[libraries]`, directly after the `play-services-location` line, add:

```toml
androidx-glance-appwidget   = { group = "androidx.glance",           name = "glance-appwidget",         version.ref = "glance" }
```

In `app/build.gradle.kts`, directly after `implementation(libs.play.services.location)`, add:

```kotlin
    // The home-screen widget: today's status, and a way into the flow.
    implementation(libs.androidx.glance.appwidget)
```

- [ ] **Step 2: Add the widget copy**

In `app/src/main/res/values/strings.xml`, add this block just before the closing `</resources>`:

```xml

    <!-- Home-screen widget. English only: it shows no refusals, so none
         of it belongs to the bilingual failure copy. -->
    <string name="widget_label">Time In / Out</string>
    <string name="widget_description">Today\'s status, and Time In or Time Out in one tap.</string>
    <string name="widget_signed_out">Sign in to DACs Attendance</string>
    <string name="widget_not_timed_in">Not timed in</string>
    <string name="widget_timed_in">Timed in %1$s</string>
    <string name="widget_timed_in_no_time">Timed in</string>
    <string name="widget_done">Done for today · %1$s – %2$s</string>
    <string name="widget_done_no_times">Done for today</string>
    <string name="widget_abandoned">Day closed without Time Out</string>
    <string name="widget_unknown">Open the app to see today</string>
    <string name="widget_not_sent">Not sent yet</string>
    <string name="widget_action_open">Open app</string>
```

- [ ] **Step 3: Write `WidgetStateLoader.kt`**

Create `app/src/main/java/com/dacs/attendance/widget/WidgetStateLoader.kt`:

```kotlin
package com.dacs.attendance.widget

import android.util.Log
import com.dacs.attendance.data.local.AttendanceDatabase
import com.dacs.attendance.data.local.WorkerCache
import com.dacs.attendance.data.repo.toDomain
import com.dacs.attendance.domain.WidgetState
import com.dacs.attendance.domain.WorkDate
import com.dacs.attendance.domain.widgetStateFor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

private const val TAG = "WidgetStateLoader"

/**
 * Today, as the widget shows it, from the phone alone.
 *
 * Never the network. The widget is looked at on sites with no signal, and
 * the mirror is already the app's own answer to "have I timed in" there.
 */
@Singleton
class WidgetStateLoader @Inject constructor(
    private val database: AttendanceDatabase,
    private val workerCache: WorkerCache,
    private val client: SupabaseClient
) {

    suspend fun load(now: Instant = Instant.now()): WidgetState = try {
        // Same identity rule as the offline repository: offline, the auth
        // client reports nobody for a session it cannot refresh, and the
        // sign-in we witnessed stands in.
        client.auth.awaitInitialization()
        val workerId = client.auth.currentUserOrNull()?.id ?: workerCache.lastSignedInId
        val today = WorkDate.today(now)

        if (workerId.isNullOrBlank()) {
            WidgetState.SignedOut
        } else {
            // Both reads are scoped to this worker. That is what keeps the
            // last worker's day off a shared phone's home screen.
            widgetStateFor(
                workerId = workerId,
                record = database.cachedRecords().forDate(workerId, today.toString())?.toDomain(),
                today = today,
                hasPending = database.pendingSubmissions().hasPendingFor(workerId)
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        // No button is the safe failure: a guessed IN or OUT sends the
        // worker to a photo the server may refuse.
        Log.w(TAG, "could not read today for the widget", error)
        WidgetState.Unknown()
    }
}
```

- [ ] **Step 4: Write `WidgetEntryPoint.kt`**

Create `app/src/main/java/com/dacs/attendance/widget/WidgetEntryPoint.kt`:

```kotlin
package com.dacs.attendance.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * How the widget reaches the Hilt graph. Glance creates the widget itself,
 * so it cannot be constructor-injected like everything else.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetStateLoader(): WidgetStateLoader
}
```

- [ ] **Step 5: Write `DacsTimeWidget.kt`**

Create `app/src/main/java/com/dacs/attendance/widget/DacsTimeWidget.kt`:

```kotlin
package com.dacs.attendance.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dacs.attendance.MainActivity
import com.dacs.attendance.R
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WidgetState
import com.dacs.attendance.ui.EXTRA_START_FLOW
import com.dacs.attendance.ui.theme.Brown
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextDisabled
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextPrimary
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.util.Date
import java.util.TimeZone

/**
 * Bumped by [GlanceWidgetRefresher]. A widget whose session is still
 * running does not re-run [DacsTimeWidget.provideGlance] on update, only
 * recomposes -- so the re-read is keyed on this, or it would never happen.
 */
internal val RefreshTick = longPreferencesKey("refresh_tick")

/**
 * The home-screen widget: today's status, and the one button Home would
 * show. It opens the same four-step flow; it records nothing itself.
 */
class DacsTimeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loader = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .widgetStateLoader()
        // Read before the first frame, so the widget never flashes an
        // empty state that could be mistaken for "Not timed in".
        val initial = loader.load()

        provideContent {
            val tick = currentState(RefreshTick) ?: 0L
            val state by produceState(initial, tick) { value = loader.load() }
            WidgetContent(context, state)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, state: WidgetState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.surface)
            .cornerRadius(16.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity(openAppIntent(context))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = statusLine(context, state),
                style = TextStyle(
                    color = WidgetColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2
            )
            if (state.notSentYet) {
                Text(
                    text = context.getString(R.string.widget_not_sent),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp)
                )
            }
        }

        val action = state.action
        when {
            action != null -> {
                Spacer(GlanceModifier.width(10.dp))
                Button(
                    text = context.getString(
                        if (action == TimeDirection.IN) R.string.action_time_in else R.string.action_time_out
                    ),
                    onClick = actionStartActivity(startFlowIntent(context, action)),
                    // Green arrives, brown leaves -- the same meaning the
                    // colours carry everywhere else in the app.
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (action == TimeDirection.IN) WidgetColors.timeIn else WidgetColors.timeOut,
                        contentColor = WidgetColors.onAction
                    )
                )
            }

            state is WidgetState.SignedOut -> {
                Spacer(GlanceModifier.width(10.dp))
                Button(
                    text = context.getString(R.string.widget_action_open),
                    onClick = actionStartActivity(openAppIntent(context)),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = WidgetColors.timeIn,
                        contentColor = WidgetColors.onAction
                    )
                )
            }
        }
    }
}

private fun statusLine(context: Context, state: WidgetState): String = when (state) {
    WidgetState.SignedOut -> context.getString(R.string.widget_signed_out)
    is WidgetState.NotTimedIn -> context.getString(R.string.widget_not_timed_in)
    is WidgetState.Working ->
        state.timeInAt?.let { context.getString(R.string.widget_timed_in, clock(context, it)) }
            ?: context.getString(R.string.widget_timed_in_no_time)
    is WidgetState.Complete ->
        if (state.timeInAt != null && state.timeOutAt != null) {
            context.getString(
                R.string.widget_done,
                clock(context, state.timeInAt),
                clock(context, state.timeOutAt)
            )
        } else {
            context.getString(R.string.widget_done_no_times)
        }
    is WidgetState.Abandoned -> context.getString(R.string.widget_abandoned)
    is WidgetState.Unknown -> context.getString(R.string.widget_unknown)
}

/**
 * Manila time, in the phone's own 12- or 24-hour style. Manila because
 * the work day is Manila's whatever zone the phone is set to -- the same
 * rule as WorkDate.
 */
private fun clock(context: Context, instant: Instant): String {
    val format = DateFormat.getTimeFormat(context)
    format.timeZone = TimeZone.getTimeZone("Asia/Manila")
    return format.format(Date(instant.toEpochMilli()))
}

internal fun openAppIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

/**
 * A distinct ACTION per direction, not just a distinct extra: two intents
 * that differ only in extras are the same PendingIntent to Android, and
 * the Time Out button would be handed the Time In one.
 */
internal fun startFlowIntent(context: Context, direction: TimeDirection): Intent =
    Intent(context, MainActivity::class.java)
        .setAction("com.dacs.attendance.action.START_FLOW_${direction.name}")
        .putExtra(EXTRA_START_FLOW, direction.name)

/** The app's palette, with a dark variant that follows the system theme. */
private object WidgetColors {
    val surface = ColorProvider(day = Surface, night = Color(0xFF1E201E))
    val text = ColorProvider(day = TextPrimary, night = Color(0xFFF0F0EB))
    val muted = ColorProvider(day = TextMuted, night = TextDisabled)
    val timeIn = ColorProvider(day = Green, night = Green)
    val timeOut = ColorProvider(day = Brown, night = Brown)
    val onAction = ColorProvider(day = Color.White, night = Color.White)
}
```

- [ ] **Step 6: Write `DacsTimeWidgetReceiver.kt`**

Create `app/src/main/java/com/dacs/attendance/widget/DacsTimeWidgetReceiver.kt`:

```kotlin
package com.dacs.attendance.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Registered in the manifest; Android talks to the widget through this. */
class DacsTimeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DacsTimeWidget()
}
```

- [ ] **Step 7: Write the provider info**

Create `app/src/main/res/xml/dacs_time_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  3x1 by default, resizable. updatePeriodMillis is only the BACKSTOP: the
  app pushes a refresh on every write (see WidgetRefresher). This is what
  rolls "Done for today" over to "Not timed in" after Manila midnight on a
  phone with no signal, when nothing else runs.
-->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="40dp"
    android:minResizeWidth="180dp"
    android:minResizeHeight="40dp"
    android:targetCellWidth="3"
    android:targetCellHeight="1"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:description="@string/widget_description"
    android:widgetCategory="home_screen" />
```

- [ ] **Step 8: Register the receiver**

In `app/src/main/AndroidManifest.xml`, directly after the closing `</activity>` of `.MainActivity`
(before the `<!-- The app supplies WorkManager's Configuration ...` comment), add:

```xml

        <!-- The home-screen widget. It opens the flow; it records nothing. -->
        <receiver
            android:name=".widget.DacsTimeWidgetReceiver"
            android:exported="true"
            android:label="@string/widget_label">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/dacs_time_widget_info" />
        </receiver>
```

- [ ] **Step 9: Build**

Run: `$G :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.
If `longPreferencesKey` is unresolved, Glance isn't exposing DataStore on this classpath. Add
the following to `[versions]` and `[libraries]` in `libs.versions.toml`, plus
`implementation(libs.androidx.datastore.preferences.core)` in `app/build.gradle.kts`, then rebuild:
- `datastore = "1.1.1"`
- `androidx-datastore-preferences-core = { group = "androidx.datastore", name = "datastore-preferences-core", version.ref = "datastore" }`

- [ ] **Step 10: Run the unit suite**

Run: `$G :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (there are no widget unit tests in this task, so
this only guards against regressions).

- [ ] **Step 11: Place the widget on the emulator**

With the emulator booted, install with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`. Long-press empty home-screen space,
then choose Widgets → DACs Attendance → "Time In / Out" and drag it onto the home screen. If
you're driving the device through adb, use `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png`
to see the screen and `adb shell input tap X Y` / `adb shell input swipe X Y X Y 1500` for a
long-press.
Expected: the widget shows "Sign in to DACs Attendance" with **Open app** when signed out. After
signing in, it shows "Not timed in" with **Time In**, or today's real state. At this point it
updates on placement, on its 30-minute timer, or when re-placed.

- [ ] **Step 12: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/dacs/attendance/widget/WidgetStateLoader.kt app/src/main/java/com/dacs/attendance/widget/WidgetEntryPoint.kt app/src/main/java/com/dacs/attendance/widget/DacsTimeWidget.kt app/src/main/java/com/dacs/attendance/widget/DacsTimeWidgetReceiver.kt app/src/main/res/xml/dacs_time_widget_info.xml app/src/main/res/values/strings.xml app/src/main/AndroidManifest.xml
git commit -m "feat(widget): Glance home-screen widget showing today's status"
```

---

### Task 6: Push refresh wiring

**Files:**
- Create: `app/src/main/java/com/dacs/attendance/widget/GlanceWidgetRefresher.kt`
- Create: `app/src/main/java/com/dacs/attendance/di/WidgetModule.kt`
- Modify: `app/src/main/java/com/dacs/attendance/di/RepositoryModule.kt:27-41`
- Modify: `app/src/main/java/com/dacs/attendance/work/SubmissionWorker.kt:44-95`
- Modify: `app/src/main/java/com/dacs/attendance/data/repo/SupabaseAuthRepository.kt:77-83`

**Interfaces:**
- Consumes:
  - `WidgetRefresher`, `refreshQuietly`, `WidgetAwareAttendanceRepository`, `WidgetAwareAuthRepository` (Task 4)
  - `DacsTimeWidget`, `RefreshTick` (Task 5)
- Produces:
  - `class GlanceWidgetRefresher @Inject constructor(@ApplicationContext context) : WidgetRefresher`
  - Hilt now resolves `AttendanceRepository` → `WidgetAwareAttendanceRepository(OfflineAttendanceRepository, …)`
    and `AuthRepository` → `WidgetAwareAuthRepository(SupabaseAuthRepository, …)`

- [ ] **Step 1: Write `GlanceWidgetRefresher.kt`**

Create `app/src/main/java/com/dacs/attendance/widget/GlanceWidgetRefresher.kt`:

```kotlin
package com.dacs.attendance.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

private const val TAG = "GlanceWidgetRefresher"

/**
 * Re-reads today into every placed widget.
 *
 * The tick is written first, then the widget updated: a session that is
 * still running only recomposes on update, and the widget re-reads when
 * the tick it is keyed on changes (see DacsTimeWidget).
 */
@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetRefresher {

    override suspend fun refresh() {
        try {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(DacsTimeWidget::class.java)
            // Most phones will never have the widget placed. Nothing to do.
            if (ids.isEmpty()) return

            val tick = System.currentTimeMillis()
            ids.forEach { id ->
                updateAppWidgetState(context, id) { prefs -> prefs[RefreshTick] = tick }
            }
            DacsTimeWidget().updateAll(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Logged, never thrown: see WidgetRefresher.
            Log.w(TAG, "widget refresh failed", error)
        }
    }
}
```

- [ ] **Step 2: Provide the decorated repositories**

Create `app/src/main/java/com/dacs/attendance/di/WidgetModule.kt`:

```kotlin
package com.dacs.attendance.di

import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.data.repo.AuthRepository
import com.dacs.attendance.data.repo.OfflineAttendanceRepository
import com.dacs.attendance.data.repo.SupabaseAuthRepository
import com.dacs.attendance.widget.WidgetAwareAttendanceRepository
import com.dacs.attendance.widget.WidgetAwareAuthRepository
import com.dacs.attendance.widget.WidgetRefresher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The repositories the app sees, wrapped so the home-screen widget follows
 * every change to today. Replaces the plain binds that used to live in
 * RepositoryModule -- there must be exactly one binding for each.
 */
@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {

    @Provides
    @Singleton
    fun provideAttendanceRepository(
        offline: OfflineAttendanceRepository,
        widgets: WidgetRefresher
    ): AttendanceRepository = WidgetAwareAttendanceRepository(offline, widgets)

    @Provides
    @Singleton
    fun provideAuthRepository(
        supabase: SupabaseAuthRepository,
        widgets: WidgetRefresher
    ): AuthRepository = WidgetAwareAuthRepository(supabase, widgets)
}
```

- [ ] **Step 3: Update `RepositoryModule`**

In `app/src/main/java/com/dacs/attendance/di/RepositoryModule.kt`:

1. Delete these two bindings and their imports (`OfflineAttendanceRepository` and
   `SupabaseAuthRepository`, lines 9 and 11). Leave the comment above `bindAttendanceRepository`
   out too, because its point now lives in `WidgetModule`.

```kotlin
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository
```

```kotlin
    // The OFFLINE implementations are what the app sees. They own the
    // queue and the mirrors, and call the Supabase ones underneath --
    // which is why those stay concrete classes rather than being bound
    // to these interfaces themselves.
    @Binds
    @Singleton
    abstract fun bindAttendanceRepository(impl: OfflineAttendanceRepository): AttendanceRepository
```

2. Remove the now-unused imports `com.dacs.attendance.data.repo.AttendanceRepository` and
   `com.dacs.attendance.data.repo.AuthRepository`.

3. Add imports `com.dacs.attendance.widget.GlanceWidgetRefresher` and
   `com.dacs.attendance.widget.WidgetRefresher`, and add this binding at the end of the class:

```kotlin

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher
```

4. Directly above `bindProjectRepository`, add this comment so the remaining offline bind keeps
   its explanation:

```kotlin
    // The OFFLINE implementation is what the app sees -- it owns the
    // project cache and calls the Supabase one underneath. Attendance and
    // auth are provided in WidgetModule, wrapped so the widget follows them.
```

- [ ] **Step 4: Refresh after every upload run**

In `app/src/main/java/com/dacs/attendance/work/SubmissionWorker.kt`:

1. Add the import `com.dacs.attendance.widget.WidgetRefresher` and the import
   `com.dacs.attendance.widget.refreshQuietly`.
2. Add a constructor parameter after `client`:

```kotlin
    private val client: io.github.jan.supabase.SupabaseClient,
    private val widgets: WidgetRefresher
) : CoroutineWorker(context, params) {
```

3. Rename the existing `override suspend fun doWork(): Result {` to
   `private suspend fun drain(): Result {`, leaving its body unchanged. Then add above it:

```kotlin
    /**
     * One drain, then the widget. The queue is what "Not sent yet" on the
     * home screen reports, and this is the only place it empties.
     *
     * Also the periodic sweeper's run, so a phone with signal re-reads the
     * widget at least every 15 minutes -- which is what rolls a finished
     * day over after Manila midnight. In `finally`, so a retry or an early
     * return still leaves the widget telling the truth about the queue.
     */
    override suspend fun doWork(): Result = try {
        drain()
    } finally {
        widgets.refreshQuietly()
    }
```

- [ ] **Step 5: Make sign-out forget the worker even offline**

In `app/src/main/java/com/dacs/attendance/data/repo/SupabaseAuthRepository.kt`, replace the
body of `signOut()` (lines 77-83):

```kotlin
    override suspend fun signOut() {
        // Cleared BEFORE the session goes, while the id is still readable.
        // Site phones are shared, and a cached profile outliving its
        // session would greet the next worker with the last one's name.
        client.auth.currentUserOrNull()?.id?.let(workerCache::forget)
        runCatchingExceptCancellation { client.auth.signOut() }
    }
```

with:

```kotlin
    override suspend fun signOut() {
        // Cleared BEFORE the session goes, while the id is still readable.
        // Site phones are shared, and a cached profile outliving its
        // session would greet the next worker with the last one's name.
        //
        // The cached id is the fallback because offline the client can
        // report nobody for a session it holds. Without it, a sign-out
        // with no signal left lastSignedInId standing -- and currentWorker()
        // and the home-screen widget both kept resolving the worker who
        // had just signed out.
        (client.auth.currentUserOrNull()?.id ?: workerCache.lastSignedInId)
            ?.let(workerCache::forget)
        runCatchingExceptCancellation { client.auth.signOut() }
    }
```

- [ ] **Step 6: Build and run the unit suite**

Run: `$G :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` and all tests pass. A Hilt `[Dagger/DuplicateBindings]` error means
one of the two old binds in Step 3 is still there. A `[Dagger/MissingBinding]` error for
`WidgetRefresher` means Step 3.3 is missing.

- [ ] **Step 7: Verify on the emulator that the widget follows the app**

Install the new APK with the widget placed and a worker signed in who hasn't timed in today.
1. Open the app, tap **Time In** on Home, and complete the four steps. Go to the home screen.
   Expected: the widget shows "Timed in HH:MM", with "Not sent yet" until the upload lands, and
   then that line clears.
2. Enable airplane mode (`adb shell cmd connectivity airplane-mode enable`), open the app, and
   complete **Time Out**. Go home.
   Expected: "Done for today · HH:MM – HH:MM" plus "Not sent yet".
3. Disable airplane mode (`adb shell cmd connectivity airplane-mode disable`) and open the app,
   so Home triggers `sendNow()`. Wait for the upload.
   Expected: "Not sent yet" clears.
4. In Profile, tap **Log out**. Go home.
   Expected: "Sign in to DACs Attendance" with no times.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dacs/attendance/widget/GlanceWidgetRefresher.kt app/src/main/java/com/dacs/attendance/di/WidgetModule.kt app/src/main/java/com/dacs/attendance/di/RepositoryModule.kt app/src/main/java/com/dacs/attendance/work/SubmissionWorker.kt app/src/main/java/com/dacs/attendance/data/repo/SupabaseAuthRepository.kt
git commit -m "feat(widget): push a widget refresh on submit, sync, sign-in and sign-out"
```

---

### Task 7: The widget's button opens the flow

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (the `.MainActivity` element)
- Modify: `app/src/main/java/com/dacs/attendance/ui/RootViewModel.kt`
- Modify: `app/src/main/java/com/dacs/attendance/MainActivity.kt`
- Modify: `app/src/main/java/com/dacs/attendance/ui/AttendanceRoot.kt:59-92,152-234`
- Modify: `app/src/main/java/com/dacs/attendance/ui/dashboard/DashboardScreen.kt:109-128`

**Interfaces:**
- Consumes: `EXTRA_START_FLOW`, `startFlowFromExtra`, `resolveStartFlow`, `StartFlowDecision`
  (Task 3), and the widget's `startFlowIntent` (Task 5)
- Produces:
  - `RootViewModel.startFlowRequest: StateFlow<TimeDirection?>`
  - `RootViewModel.requestStartFlow(direction: TimeDirection)`
  - `RootViewModel.consumeStartFlow()`
  - `DashboardScreen(…, startFlowRequest: TimeDirection? = null, onStartFlowRequestHandled: () -> Unit = {}, viewModel = …)`

The decision logic is already unit-tested (Task 3). This task only wires it in, so it's verified
with a build plus the emulator, including `adb shell am start --es`, which sends exactly the
widget's extra.

- [ ] **Step 1: Make `MainActivity` single-top**

In `app/src/main/AndroidManifest.xml`, on the `.MainActivity` `<activity>` element, add the
attribute directly after `android:exported="true"`:

```xml
            android:launchMode="singleTop"
```

Also add this comment directly above that `<activity` element:

```xml
        <!-- singleTop: a widget tap while the app is open must reach this
             activity through onNewIntent. Without it, Android stacks a
             second copy of the app on top of the first. -->
```

- [ ] **Step 2: Hold the request in `RootViewModel`**

In `app/src/main/java/com/dacs/attendance/ui/RootViewModel.kt`, add the import
`com.dacs.attendance.domain.TimeDirection`. Then, directly after
`val state: StateFlow<AppState> = _state.asStateFlow()`, add:

```kotlin

    private val _startFlowRequest = MutableStateFlow<TimeDirection?>(null)

    /**
     * A Time In / Time Out asked for from the home-screen widget, not yet
     * acted on. One-shot: whoever acts on it calls [consumeStartFlow].
     *
     * Held here, not in the Activity, so it survives a configuration
     * change between the tap and the moment Home can decide.
     */
    val startFlowRequest: StateFlow<TimeDirection?> = _startFlowRequest.asStateFlow()

    fun requestStartFlow(direction: TimeDirection) {
        _startFlowRequest.value = direction
    }

    fun consumeStartFlow() {
        _startFlowRequest.value = null
    }
```

- [ ] **Step 3: Read the extra in `MainActivity`**

Replace the whole content of `app/src/main/java/com/dacs/attendance/MainActivity.kt` with:

```kotlin
package com.dacs.attendance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.dacs.attendance.ui.AttendanceRoot
import com.dacs.attendance.ui.EXTRA_START_FLOW
import com.dacs.attendance.ui.RootViewModel
import com.dacs.attendance.ui.startFlowFromExtra
import com.dacs.attendance.ui.theme.AttendanceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val root: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a FRESH start. A recreated activity is handed its
        // original intent again -- after a process death, extra and all --
        // and reading it then would reopen a flow the worker already
        // finished or walked away from.
        if (savedInstanceState == null) takeStartFlowRequest(intent)
        setContent {
            AttendanceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    AttendanceRoot(modifier = Modifier.padding(inner), viewModel = root)
                }
            }
        }
    }

    /** A widget tap while the app is already running (launchMode singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeStartFlowRequest(intent)
    }

    private fun takeStartFlowRequest(intent: Intent?) {
        val direction = startFlowFromExtra(intent?.getStringExtra(EXTRA_START_FLOW)) ?: return
        // Removed as it is read, so nothing that looks at this intent later
        // can act on the same tap twice.
        intent?.removeExtra(EXTRA_START_FLOW)
        root.requestStartFlow(direction)
    }
}
```

- [ ] **Step 4: Route the request in `AttendanceRoot`**

In `app/src/main/java/com/dacs/attendance/ui/AttendanceRoot.kt`:

1. Add the import `androidx.compose.runtime.LaunchedEffect`.

2. In `AttendanceRoot`, replace:

```kotlin
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
```

with:

```kotlin
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startFlowRequest by viewModel.startFlowRequest.collectAsStateWithLifecycle()

    // A widget tap that lands on Login, Terms or the offline gate is
    // forgotten rather than held: after signing in the worker should reach
    // Home, not be dropped into a camera they asked for minutes ago.
    LaunchedEffect(startFlowRequest, state) {
        val request = startFlowRequest ?: return@LaunchedEffect
        if (resolveStartFlow(request, state, flowOpen = false, home = null) == StartFlowDecision.Drop) {
            viewModel.consumeStartFlow()
        }
    }

    when (val current = state) {
```

3. Replace the `SignedIn` branch:

```kotlin
        is AppState.SignedIn -> SignedInArea(
            worker = current.worker,
            onSignOut = viewModel::onSignOut,
            modifier = modifier
        )
```

with:

```kotlin
        is AppState.SignedIn -> SignedInArea(
            worker = current.worker,
            onSignOut = viewModel::onSignOut,
            startFlowRequest = startFlowRequest,
            onStartFlowRequestHandled = viewModel::consumeStartFlow,
            modifier = modifier
        )
```

4. Change the `SignedInArea` signature to:

```kotlin
@Composable
private fun SignedInArea(
    worker: WorkerProfile,
    onSignOut: () -> Unit,
    startFlowRequest: TimeDirection?,
    onStartFlowRequestHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
```

5. In `SignedInArea`, directly after
   `var exitNotice by rememberSaveable { mutableStateOf<FlowExit?>(null) }` and BEFORE
   `val direction = flow`, add the following. It must come before the early `return` that
   renders `TimeFlowScreen`, or it won't run while a flow is open.

```kotlin

    // A widget tap. Mid-flow it is dropped -- the capture in progress is
    // the worker's, and a tap on the home screen must not throw away a
    // photo they already took. Otherwise Home is brought forward, and
    // decides (see DashboardScreen).
    LaunchedEffect(startFlowRequest) {
        val request = startFlowRequest ?: return@LaunchedEffect
        when (resolveStartFlow(request, AppState.SignedIn(worker), flowOpen = flow != null, home = null)) {
            StartFlowDecision.Drop -> onStartFlowRequestHandled()
            else -> tab = WorkerTab.HOME
        }
    }
```

6. In the `WorkerTab.HOME -> DashboardScreen(` call, add these two arguments directly after
   `refreshKey = reloadKey`. Put a comma after `reloadKey`.

```kotlin
                    refreshKey = reloadKey,
                    startFlowRequest = startFlowRequest,
                    onStartFlowRequestHandled = onStartFlowRequestHandled
```

- [ ] **Step 5: Decide on Home, against a fresh read of today**

In `app/src/main/java/com/dacs/attendance/ui/dashboard/DashboardScreen.kt`:

1. Add these imports:
   - `com.dacs.attendance.ui.AppState`
   - `com.dacs.attendance.ui.StartFlowDecision`
   - `com.dacs.attendance.ui.resolveStartFlow`
   - `kotlinx.coroutines.flow.first`

2. In the `DashboardScreen` parameter list, directly after `refreshKey: Int = 0,` and before
   `viewModel: DashboardViewModel = hiltViewModel()`, add:

```kotlin
    /**
     * A Time In / Time Out asked for from the home-screen widget. Opened
     * only if it matches what this screen would offer after re-reading
     * today; otherwise the worker simply stays here, looking at the truth.
     */
    startFlowRequest: TimeDirection? = null,
    onStartFlowRequestHandled: () -> Unit = {},
```

3. Directly after the existing block:

```kotlin
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.refresh()
    }
```

add:

```kotlin

    LaunchedEffect(startFlowRequest) {
        val request = startFlowRequest ?: return@LaunchedEffect
        // The widget may be behind, and so may this ViewModel -- it
        // outlives the screen. Re-read today before deciding, unless a read
        // is already under way.
        if (!viewModel.uiState.value.loading) viewModel.refresh()
        val settled = viewModel.uiState.first { !it.loading }

        when (val decision = resolveStartFlow(request, AppState.SignedIn(worker), flowOpen = false, home = settled)) {
            is StartFlowDecision.Open -> {
                onStartFlowRequestHandled()
                onStartFlow(decision.direction)
            }
            StartFlowDecision.StayOnHome, StartFlowDecision.Drop -> onStartFlowRequestHandled()
            // Unreachable: settled is never loading. Left explicit so a
            // new decision cannot be added without being handled here.
            StartFlowDecision.Wait -> Unit
        }
    }
```

- [ ] **Step 6: Build and run the unit suite**

Run: `$G :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Verify the tap paths on the emulator**

Install the APK. `S` below is
`adb shell am start -n com.dacs.attendance.debug/com.dacs.attendance.MainActivity --es com.dacs.attendance.extra.START_FLOW`,
which sends exactly the widget's extra.

1. Signed in with no record today, app closed: tap **Time In** on the widget.
   Expected: the app opens straight on Pick Project (step 1 of 4) for Time In.
2. Back out of the flow to Home, then run `S OUT`.
   Expected: the worker stays on Home, because the widget request doesn't match (stale guard).
3. Complete a Time In. On Home, start **Time Out** and take the photo (step 3, Check Photo).
   Press the Home key, tap the widget's **Time Out** button, or run `S OUT`.
   Expected: the app returns to Check Photo with the same photo and the flow is not reset.
4. Sign out, then tap **Open app** on the widget, or run `S IN`.
   Expected: the Login screen. After signing in the worker lands on Home, not in the camera.
5. Rotate or change dark mode while on Pick Project after step 1 (`adb shell cmd uimode night yes`,
   then `no`).
   Expected: the flow isn't reopened or duplicated.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/dacs/attendance/ui/RootViewModel.kt app/src/main/java/com/dacs/attendance/MainActivity.kt app/src/main/java/com/dacs/attendance/ui/AttendanceRoot.kt app/src/main/java/com/dacs/attendance/ui/dashboard/DashboardScreen.kt
git commit -m "feat(widget): widget buttons open the four-step flow, guarded by Home's state"
```

---

### Task 8: Full verification

**Files:** none are modified unless a check fails.

- [ ] **Step 1: Run the full unit suite**

Run: `$G :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` with 0 failures. The count is the previous total plus 30
(10 + 12 + 8).

- [ ] **Step 2: Run the instrumented tests**

Run: `$G :app:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`. `PendingSubmissionDaoTest` (4) and the existing
`PhotoStoreOrientationTest` pass.

- [ ] **Step 3: Build a release**

Run: `$G :app:assembleRelease`
Expected: `BUILD SUCCESSFUL`. This confirms R8/minify keeps the receiver, the Glance classes and
the Hilt entry point. If R8 strips `DacsTimeWidgetReceiver` or `WidgetEntryPoint`, add
`-keep class com.dacs.attendance.widget.** { *; }` to `app/proguard-rules.pro` and rebuild.

- [ ] **Step 4: Run the spec's emulator checklist end to end**

Run every check in spec §5 "On the emulator" (checks 1–6) on the debug build. Take a screenshot
of the widget in each state with `adb shell screencap`, and confirm the dark variant with
`adb shell cmd uimode night yes`.
Expected: all six pass. Any failure goes back to the task that owns that behavior.

- [ ] **Step 5: Commit any fixes**

If Steps 1–4 needed fixes, commit them with a message naming what was wrong. If nothing needed
fixing, there is nothing to commit.
