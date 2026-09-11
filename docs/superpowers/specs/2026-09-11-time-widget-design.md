# Time In / Time Out home-screen widget — design

**Date:** 2026-09-11
**Status:** Approved; plan at `docs/superpowers/plans/2026-09-11-time-widget.md`
**Branch:** feat/b2-login-terms

## Goal

A home-screen widget that shows the worker's attendance status for today and opens the
Time In / Time Out flow in one tap, so they don't have to open the app and find the Home button.

## What does not change

- **Every Time In / Time Out still runs the full four steps:** Pick Project → Take Photo →
  Check Photo → Describe → Submit. The widget skips nothing and pre-fills nothing.
- **Photo and location rules stay as they are:**
  - The photo is still required (MVP decision 21) and still taken only in the app.
  - The GPS fix and geofence pre-check still run at submit (decisions 16, 18, 19).
  - The server still re-verifies.
- **The widget never records attendance itself and never goes online.**

## 1. Widget states

The widget is about 3×1 cells and resizable. Its state is derived from today's Asia/Manila
work date (`domain/WorkDate.kt`).

| Today's state | Status line | Button |
|---|---|---|
| Signed out / no cached worker | "Sign in to DACs Attendance" | **Open app** |
| Signed in, no record today | "Not timed in" | **Time In** |
| `working` | "Timed in 8:52 AM" | **Time Out** |
| `complete` | "Done for today · 8:52 AM – 5:10 PM" | none |
| `abandoned` | "Day closed without Time Out" | none |
| Unknown status, or the local read failed | "Open the app to see today" | none |
| Any of the above, with that worker's submission still queued | adds "Not sent yet" under the status | same as the row above |

- The copy is English-only, following the v2 redesign rule. The widget shows no failure notices,
  so none of it needs to be bilingual.
- The widget shows no name, photo or project. Anyone who glances at the home screen sees only
  whether the worker is timed in.
- Tapping anywhere outside the button opens the app on Home.
- Times use the device's 12-hour or 24-hour setting and are shown in Manila time.

## 2. Data and refresh

### Reading state

When the widget renders, it reads local data through a Hilt `@EntryPoint`. It makes no network
calls.

1. **Worker id:** `currentUserOrNull()?.id`, falling back to `WorkerCache.lastSignedInId`.
   This is the same fallback `OfflineAttendanceRepository.currentWorkerId()` uses.
2. **Today's record:** `CachedRecordDao.forDate(workerId, todayManila)`.
3. **Pending flag:** a new query, `PendingSubmissionDao.hasPendingFor(workerId): Boolean`. It is
   scoped by worker, because the existing `observeAll()` is not.

A pure function, `widgetStateFor(workerId: String?, record: AttendanceRecord?, today: WorkDate,
hasPending: Boolean): WidgetState`, maps those inputs to the rows in §1.

- `WidgetState` is a small sealed type: `SignedOut`, `NotTimedIn`, `Working(timeInAt)`,
  `Complete(timeInAt, timeOutAt)`, `Abandoned` and `Unknown`. Each case carries
  `notSentYet: Boolean`.
- The mapping has these defensive rules:
  - a record whose `workDate != today` is treated as no record
  - a record belonging to another worker can't reach the function at all, because both reads
    are scoped by `workerId`
- All of the widget's logic lives in this function. The Glance composable only renders a
  `WidgetState`.

### Refreshing

The app pushes refreshes through a `WidgetRefresher` interface, injected by Hilt. Its real
implementation calls Glance's `DacsTimeWidget().updateAll(context)`, and tests use a fake.

`refresh()` is called at these moments. Moments 1, 3 and 4 go through decorators,
`WidgetAwareAttendanceRepository` and `WidgetAwareAuthRepository`, which Hilt wraps around the
real repositories. That way they can be JVM-tested without Supabase or Room.

1. **After `AttendanceRepository.submit()`**, which includes the optimistic `cached_record`
   upsert. The widget then shows the new state and "Not sent yet" straight away, even offline.
2. **After every `SubmissionWorker` run** (in `finally`), whether the run drained the queue,
   wrote the server's row back, or is in retry. This clears "Not sent yet".
3. **After sign-in and sign-out.** After a sign-out the widget shows `SignedOut` and no times from
   the previous worker.
   - `SupabaseAuthRepository.signOut()` also now forgets `lastSignedInId` when
     `currentUserOrNull()` is null offline. Previously that case left the id standing, so both
     `currentWorker()` and the widget kept resolving the worker who had signed out.
4. **After `AttendanceRepository.today()`**, the server reconcile that `DashboardViewModel.refresh()`
   runs, so corrections made on the server reach the widget.
5. **The existing 15-minute periodic sweeper**, which runs `SubmissionWorker` and so is covered
   by moment 2. When online, it covers the Manila-midnight rollover: "Done for today" becomes
   "Not timed in" within about 15 minutes.

The widget provider's `updatePeriodMillis` is set to 30 minutes as a backstop. It is also what
rolls the day over offline, because the sweeper only runs with a connection.

A running Glance session only recomposes on update; it doesn't re-run `provideGlance`. So the
refresher also bumps a `RefreshTick` in the widget's Glance state, and the content re-reads
whenever that tick changes.

**Failure policy:** `refresh()` never throws to its caller. A widget update failure is logged and
swallowed, so it can never fail a submission, a sync or a sign-out.

## 3. Tap → app

- **Widget intent:**
  - The widget's button launches `MainActivity` with the extra
    `EXTRA_START_FLOW = "IN" | "OUT"`.
  - The "Open app" button and taps on the widget body launch it with no extra.
  - Time In and Time Out use distinct intent actions, so Android doesn't merge their
    PendingIntents.
- **Manifest:** `MainActivity` gets `android:launchMode="singleTop"`. Without it, a tap while the
  app is open would stack a second `MainActivity` instead of calling `onNewIntent`.
- **Reading the request:** `MainActivity` reads the extra in `onNewIntent`, and in `onCreate`
  only when `savedInstanceState == null`, then removes it from the intent. A process restore or
  recreation therefore can't replay it.
- **Handing it on:** the request is passed to the root as a one-shot `pendingStartFlow:
  TimeDirection?`, and `AttendanceRoot` consumes it:
  - **Not `SignedIn`** (Loading resolves to SignedOut, NeedsTerms or GateUnavailable): the request
    is dropped, and the worker sees that screen as normal. After signing in they land on Home,
    not in the camera.
  - **Flow already open:** the request is ignored and the in-progress capture is kept.
  - **`SignedIn`, no flow open:** the root switches to the HOME tab and waits for the
    Dashboard's `nextAction` to resolve.
    - If `nextAction` equals the requested direction, it opens `TimeFlowScreen` for that
      direction.
    - Otherwise the worker stays on Home, which already shows the correct state. This guards
      against a stale widget, so the worker never takes a photo only for the server to refuse it
      with `ALREADY_TIMED_IN` or `NOT_TIMED_IN`.
- **The flow itself:** it is the same `TimeFlowScreen` and `TimeFlowViewModel` the Home button
  uses, and submit triggers the §2 refresh.

The decision is a pure function, `resolveStartFlow(request, appState, flowOpen, home:
DashboardUiState?): StartFlowDecision` (`Open(direction)` / `StayOnHome` / `Drop` / `Wait`), so it
can be unit-tested. Before deciding, Home re-reads today, because its ViewModel outlives the
screen and may be stale too.

## 4. Components and files

| Unit | Where | Purpose |
|---|---|---|
| `glance-appwidget` dependency | `gradle/libs.versions.toml`, `app/build.gradle.kts` | Glance runtime |
| `WidgetState`, `widgetStateFor` | `domain/WidgetState.kt` | Pure mapping from local data to state |
| `resolveStartFlow` | `ui/StartFlowRequest.kt` (decides over UI types) | Pure launch-request decision |
| `WidgetAwareAttendanceRepository`, `WidgetAwareAuthRepository` | `widget/`, provided in `di/WidgetModule.kt` | Refresh after state-changing calls |
| `DacsTimeWidget` (GlanceAppWidget), `DacsTimeWidgetReceiver` | `widget/` | Renders a `WidgetState`; builds the launch intents |
| `WidgetEntryPoint` | `widget/` | Hilt entry point for the DAOs, `WorkerCache` and auth |
| `WidgetRefresher` + `GlanceWidgetRefresher` | `widget/`, bound in `di/` | Push refresh; never throws |
| `hasPendingFor(workerId)` | `data/local/AttendanceDao.kt` | New query, no schema change |
| Widget provider XML + manifest `<receiver>` | `res/xml/`, `AndroidManifest.xml` | Registration, preview, 30-min backstop |
| `singleTop` + intent handling | `MainActivity.kt`, `AttendanceRoot.kt` | One-shot start-flow request |
| Refresh call sites | the two decorators, `SubmissionWorker` (also the sweeper) | Keep the widget current |
| Offline sign-out fix | `SupabaseAuthRepository.signOut()` | Forget `lastSignedInId` even when the client reports nobody |

The widget's colors come from the existing theme tokens. It has a light and a dark variant that
follow the system theme.

## 5. Testing

### Unit tests (JVM, written test-first)

- **`widgetStateFor`:**
  - every row in §1
  - yesterday's record → `NotTimedIn`
  - another worker's record is ignored
  - pending rows from another worker don't set `notSentYet`
  - a null worker → `SignedOut`, even when a record exists
- **`resolveStartFlow`:**
  - match → `Open`
  - mismatch → `StayOnHome`
  - not signed in → `Drop`
  - flow open → ignored
  - `nextAction` not resolved yet → `WaitForNextAction`
  - a closed day with a request → `StayOnHome`
- **Refresh wiring** (using a fake `WidgetRefresher`):
  - it's called after `submit()`
  - it's called after the worker writes back
  - it's called on sign-in and on sign-out
  - a throwing refresher doesn't fail `submit()` or sign-out
- **DAO:** `hasPendingFor` returns true only for the matching worker. This test sits alongside the
  existing DAO tests.

### On the emulator

1. Add the widget. It shows "Sign in…" when signed out, or "Not timed in" when signed in.
2. Tap Time In and complete the four steps. The widget shows "Timed in HH:MM".
3. Switch on airplane mode, then Time Out. The widget shows "Done for today … · Not sent yet".
   Back online, "Not sent yet" clears.
4. Sign out. The widget shows "Sign in…" with no times.
5. Open the flow, go to the home screen and tap the widget. The capture in progress isn't reset.
6. Force a stale widget (a Time In that the widget hasn't caught up with yet), then tap Time In.
   The worker lands on Home without taking a photo.

## Out of scope

- Time In with one tap and no photo.
- Pre-picking the last-used project.
- Lock-screen widget, Quick Settings tile, app shortcuts.
- Showing the reward, project or worker name on the widget.
