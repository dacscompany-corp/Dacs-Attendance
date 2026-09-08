# DACS Attendance — Features, Modules & Architecture

A worker attendance system with two halves that share one Supabase project:

| Half | What it is | Where it lives | Who uses it |
|---|---|---|---|
| **Worker app** | Native Android APK (Kotlin + Jetpack Compose) | `Documents/Dacs Attendance` (this repo) | Construction workers on site |
| **Admin side** | Vanilla-JS section inside the DAC's web portal | `Documents/Dacs Web` → `admin.html`, `js/attendance-admin.js` | Owner + staff |
| **Shared spine** | Postgres tables, RLS, RPCs, Storage, Edge Functions | `Documents/Dacs Web/supabase/` | Both |

**The one invariant that shapes everything:** attendance is **outside the money model**. There is no peso column in any attendance table, hours are never the basis of pay (DAC's labour is *pakyaw* / capped contract pay via `labor_contracts.agreed_amount`), and nothing here feeds Spent / Earned / Profit. Same deliberate isolation as `reimbursements` (0041), `warranty_retentions` (0043) and `quotations` (0045).

---

## 1. System architecture

```
┌─────────────────────────────┐         ┌──────────────────────────────┐
│  ANDROID WORKER APP         │         │  WEB ADMIN (admin.html)      │
│  Compose · Hilt · Room      │         │  vanilla JS · no build step  │
│  WorkManager · CameraX      │         │  attendance-admin.js         │
└──────────────┬──────────────┘         └───────────────┬──────────────┘
               │                                        │
   POST /functions/v1/                     supabase-config.js shim
   attendance-signin                       (Firestore-compat over PostgREST)
               │                                        │
               ▼                                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│  SUPABASE                                                            │
│  Edge Functions   attendance-signin · admin-create-user              │
│  RPC (security definer)  attendance_time_in / _time_out / _abandon   │
│                          attendance_projects_for_worker              │
│                          attendance_project_name · _data_owner       │
│  Tables  attendance_records · attendance_terms_acceptances           │
│          attendance_signin_attempts · profiles                       │
│  Reused  folders (pc) · construction_projects (pm)                   │
│  Storage private bucket `attendance`, prefix-scoped by worker uid    │
│  RLS on every table; every write goes through an RPC                 │
└──────────────────────────────────────────────────────────────────────┘
```

**Write path rule:** the app never `INSERT`s into `attendance_records`. Every write is a security-definer RPC with a client-generated `event_id` as the idempotency key — the same queued row replayed any number of times produces exactly one server record.

---

## 2. Worker app (Android)

### 2.1 Stack

| Concern | Choice | Why |
|---|---|---|
| UI | Jetpack Compose + Material 3 | — |
| DI | Hilt (+ `hilt-work` for the worker) | — |
| Local store | Room v3, `exportSchema = true` | A queue that loses rows loses attendance; migrations are mandatory |
| Background | WorkManager | Survives process death and reboot; a ViewModel coroutine does not |
| Camera | CameraX (`core`, `camera2`, `lifecycle`, `view`) + ExifInterface | Photo is taken **in the app** — no gallery picker anywhere |
| Images | Coil 3 + `coil-network-okhttp` | Coil 3 ships no network fetcher by default; History needs signed URLs |
| Network | Supabase KMP BOM (`auth`, `postgrest`, `storage`) over Ktor/OkHttp | |
| Secrets | `androidx.security.crypto` + `multiplatform-settings` | |
| SDK | `minSdk 24`, `target/compileSdk 36`, core-library desugaring | Workers are on cheap, older phones; desugaring keeps `java.time` |

`applicationId com.dacs.attendance` · release build is minified + resource-shrunk.

**Permissions** (`AndroidManifest.xml`): `CAMERA`, `INTERNET`, `ACCESS_NETWORK_STATE`, and **optional** `ACCESS_COARSE_LOCATION`. Location is corroborating metadata, never a gate — a denial must never block a worker from recording attendance. There is deliberately **no** `READ_MEDIA_IMAGES`: the omission is the cheapest anti-spoofing measure available.

### 2.2 Module map — `app/src/main/java/com/dacs/attendance/`

```
AttendanceApp.kt        Application; supplies WorkManager Configuration itself
MainActivity.kt         single activity, portrait-locked

domain/     ← pure Kotlin, no Android imports, fully unit-tested
data/
  local/    Room, photo files, terms cache, connectivity
  remote/   Supabase client, DTOs, sign-in API
  repo/     repository interfaces + Supabase and offline-first impls
di/         Hilt modules
ui/         Compose screens, one package per screen + shared components/theme
work/       WorkManager queue drain
```

#### `domain/` — the business rules (no Android dependency, 16 test classes)

| File | Responsibility |
|---|---|
| `WorkDate.kt` | `AttendanceZone = Asia/Manila`; work date derived exactly as the RPC does. **Never** the device zone, never a UTC date. Also owns `TimeDirection` and the storage `photoPath()` contract |
| `AttendanceRecord.kt` | `ProjectSystem` (`pc`/`pm`), `AttendanceProject` (identified by the **pair**, never id alone), `AttendanceStatus` (WORKING/COMPLETE/ABANDONED/UNKNOWN), `AttendanceRecord.nextAction` |
| `AttendanceFailure.kt` | Every way a Time In/Out can be refused, mapped from the RPCs' stable codes |
| `LoginFailure.kt` | Maps the Edge Function's error codes to worker-facing copy |
| `SubmissionQueue.kt` | `QueuedSubmission`, `nextToSend()`, `QueueOutcome` — retry vs. give up per failure |
| `TodayReconcile.kt` | Merges the server record with the local queue into one `TodayDecision` |
| `TermsGate.kt` / `StartupGate.kt` | Terms version gate; startup trusts the server, falls back to cache, and says so plainly when neither can decide |
| `AttendanceTerms.kt` | The versioned clause text + `sha256Hex()` for the audit trail |
| `History.kt` | `HistorySpan`, `historyRange()`, `historyDays()`, `historySummary()`, `weekStrip()` |
| `TotalHours.kt`, `Greeting.kt` | Hours formatting; morning/afternoon/evening |
| `PhotoOverlay.kt`, `CaptionFit.kt` | The project + timestamp stamp burned onto the photo, and its auto-fitting text size |
| `DescriptionChips.kt` | Tap-to-toggle work-description chips |
| `WorkerProfile.kt` | `Eligibility` — Allowed / AccountInactive / NotAWorker |
| `PasswordChange.kt` | Password-change validation |

#### `data/local/`

- **`AttendanceDao.kt`** — `AttendanceDatabase` (version **3**) with three DAOs.
- **`Entities.kt`**
  - `pending_submission` — *this table is the worker's day until upload succeeds.* PK is `eventId` (the RPC idempotency key). Carries `workerId` (phones are shared on site — never upload another worker's row), `projectSystem` + `projectId`, snapshotted `projectName`, shutter time, local photo path, description, lat/lng/accuracy, `wasOffline`, `attempts`, `lastError`, `failedPermanently`.
  - `cached_record` — the worker's own rows mirrored locally, keyed `(workerId, workDate)`, so the dashboard is never blank without signal.
  - `cached_project` — last-known active project list, keyed `(workerId, system, id)`; without it the picker is empty offline and the flow is dead at step 1.
- **`PhotoStore.kt`** (captured JPEGs), **`TermsCache.kt`**, **`Connectivity.kt`**.
- Migrations `MIGRATION_1_2`, `MIGRATION_2_3` live in `di/DatabaseModule.kt`; schemas are committed under `app/schemas/`. 2→3 flags pre-0059 queued rows as permanently failed rather than letting them retry against a project list that no longer exists.

#### `data/remote/`

`SupabaseModule.kt` (client + auth/postgrest/storage), `SignInApi.kt` (`SignInOutcome`, `SignInRequest/Response`, `SessionDto`, `SignInErrorBody`), `AttendanceDtos.kt` (`ProjectRow`, `AttendanceRecordRow`), `Dtos.kt` (`ProfileRow`, terms + agreement-event rows).

#### `data/repo/` — offline-first by construction

```
AttendanceRepository / ProjectRepository   (interfaces)
        ▲                       ▲
OfflineAttendanceRepository   OfflineProjectRepository   ← what Hilt binds
        │                       │
SupabaseAttendanceRepository  SupabaseProjectRepository  ← the network leg
```

`RepositoryModule` binds the **Offline** implementations; each wraps its Supabase counterpart plus the Room mirrors. `AuthRepository` → `SupabaseAuthRepository`, `TermsRepository` → `SupabaseTermsRepository`. `AttendancePhotos.kt` handles upload + signed-URL retrieval.

#### `work/`

`SubmissionScheduler` enqueues; `SubmissionWorker` (`@AssistedInject`) drains the queue — uploads the photo to Storage, calls the RPC, marks the row sent or failed. A worker who taps SUBMIT and pockets the phone still gets the record uploaded.

### 2.3 Screens & flows

| Screen | Package | State |
|---|---|---|
| **Root / gate** | `ui/AttendanceRoot.kt`, `RootViewModel` | `AppState` — routes to Login / Terms / Dashboard |
| **Login** | `ui/login/` | `LoginUiState`; posts to the Edge Function |
| **Terms** | `ui/terms/` | Versioned acceptance, recorded server-side + cached |
| **Dashboard** | `ui/dashboard/` | `DashboardUiState`, `StepState` (Done / Now / Locked), greeting, today's record, pending badge |
| **Time flow** | `ui/timeflow/` | `FlowStep`: **PickProject → TakePhoto → CheckPhoto → Describe → Confirmed**; `CameraCapture.kt` owns the CameraX preview and the burned-in overlay |
| **History** | `ui/history/` | Week/month spans, day cells, IN/OUT photos, tap-to-fullscreen |
| **Profile** | `ui/profile/` | Worker identity, password change, log out |

Shared UI: `components/` (`WorkerBottomNav` with `WorkerTab.HOME/HISTORY/PROFILE`, `FlowHeader`, `StepProgressBar`, `AppCard`, `StatusPill`, `IconTile`, `LabeledField`, `PrimaryActionButton`, `FailureNotice`, `AttendanceFailureNotice`) and `theme/` (`Color`, `Type` — Barlow / IBM Plex Mono / Playfair — `Dimens`, `Theme`).

**Copy rule:** screens are English-only; **failure notices stay bilingual (English + Tagalog)** on purpose. No `values-fil/`.

### 2.4 Tests

`app/src/test/` — 16 domain test classes plus 3 ViewModel tests (`DashboardViewModelTest`, `LoginViewModelTest`, `TimeFlowViewModelTest`) and `MainDispatcherRule`.

---

## 3. Admin side (web)

Part of the DAC's portal: **plain HTML + vanilla JS, no build step, no imports.** Entry point `admin.html`; the attendance section is `js/attendance-admin.js` (~108 KB) + `css/attendance-admin.css`.

### 3.1 Navigation wiring

`PRIMARY_NAV` in `js/admin.js`:

```js
{ id: 'attendance', label: 'Attendance', sub: 'Workers · Time In / Out',
  defaultView: 'attToday',
  modules: [ attToday | attWorkers | attProjects | attReports ] }
```

`ATT_VIEWS = ['attToday','attWorkers','attWorker','attProjects','attReports']` in `admin.html`; `switchView()` calls `initAttendanceModule(view)`. Owner + staff only — workers never see the section (`_visibleNav()`).

### 3.2 Views

| View | Features |
|---|---|
| **Today** (`attToday`) | Live roster for a work date: KPI tiles, status pills (working / complete / abandoned), IN/OUT thumbnails via signed URLs, clock-skew badge (`captured_at` vs `received_at`), offline-capture badge, search + project filter, CSV export |
| **Workers** (`attWorkers`) | Worker roster with auto-assigned worker numbers; create worker (`attValidateNewWorker` → `admin-create-user`), edit (`attValidateEditWorker`), activate/deactivate, name splitting |
| **Worker detail** (`attWorker`) | One worker, one day: both halves side by side (time, project, description, full-size photo), plus the **Resolve / abandon** dialog for a forgotten Time Out — `attCanAbandon()` gates it, `attCloserName()` resolves who closed it |
| **Projects** (`attProjects`) | The two real project systems side by side (`pc` = folders, `pm` = construction_projects) with head-count per project today |
| **Reports** (`attReports`) | Date-range presets and custom ranges; roll-ups per worker (`attRollUpByWorker`) and per project (`attRollUpByProject`); open vs. abandoned days counted **separately**; CSV download |

### 3.3 The pure report engine

The block between `// ==== ATT REPORT ENGINE START ====` and its END marker holds **pure functions only** — no DOM, no network, no module state — because `tests/attendance.test.js` extracts and runs them directly. Keep them pure or the tests stop loading.

Key rules encoded there: `daysWorked` counts rows that **exist**; hours sum only what the **server** computed. An open day is a day worked with zero hours — inventing a figure from the clock would put a number in a report the database never agreed to. Open days and abandoned days are counted separately, so closing a record visibly changes the report.

### 3.4 Verification

`npm test` (runs `tests/attendance.test.js` among others), `node --check js/attendance-admin.js`, then the browser as owner. CI: `.github/workflows/ci.yml`.

---

## 4. Shared backend (Supabase)

### 4.1 Migration timeline

| Migration | What it added |
|---|---|
| `0050_attendance.sql` | Core: `attendance_records`, `attendance_terms_acceptances`, the private `attendance` Storage bucket, RLS, and the `attendance_time_in` / `attendance_time_out` RPCs |
| `0051_attendance_worker_owner.sql` | `attendance_data_owner()` — resolves a worker's owner without granting the worker the owner's expenses/payroll/etc.; RPCs re-issued |
| `0052_attendance_signin_throttle.sql` | `attendance_signin_attempts` + `attendance_signin_is_throttled()` / `attendance_signin_record()` — the brute-force throttle that replaces the captcha for the native app. Never stores passwords; rows expire after 24 h; **not** an audit log |
| `0059_attendance_real_projects.sql` | Attendance stops keeping its own project list. Records repoint at `folders` (`pc`) and `construction_projects` (`pm`) via `timein_project_system` + `timein_folder_id` / `timein_pm_project_id` (and the `timeout_*` mirror). Adds `attendance_projects_for_worker()` and `attendance_project_name()`; **drops `attendance_projects`**. FKs are `on delete set null`, never cascade — deleting a project must never erase the fact that someone worked that day |
| `0061_attendance_abandon.sql` | `attendance_abandon(uuid, text)` plus the who/when/why trail. Closes a forgotten Time Out **without inventing hours** — `timeout_at` and `total_minutes` stay NULL, so the day still reports `—` |

### 4.2 `attendance_records` — the shape that matters

- `id uuid` PK · `owner_id` · `worker_id`
- **Snapshots** `worker_name`, `worker_position` — a later rename must not rewrite history
- `work_date date`, `session_seq smallint` (reserved for split shifts; always 1 in the MVP), `status text`
- Per half (`timein_*` / `timeout_*`): project system + folder/pm id, snapshotted `project_name`, `at`, `photo_path` (a Storage path, **never a URL**), `description`, `lat` / `lng` / `accuracy_m`, `event_id`, `was_offline`, `received_at`
- `total_minutes`, `created_at`, `updated_at` (touch trigger)

**Constraints & indexes**

- `status in ('working','complete','abandoned')` — `no_record` is the *absence* of a row, never a stored value
- `complete` requires a `timeout_at`; `timeout_at >= timein_at`
- `unique (worker_id, work_date, session_seq)` — the one-record-per-day guarantee
- `unique (timein_event_id)` and partial `unique (timeout_event_id)` — the idempotency guarantees
- `(owner_id, work_date desc)`, `(worker_id, work_date desc)`, plus 0059's folder / pm-project indexes

### 4.3 RPCs (all security definer, granted to `authenticated`)

| Function | Called by | Purpose |
|---|---|---|
| `attendance_time_in(system, id, captured_at, …)` | app (via queue) | Opens the day; validates project, clock skew, eligibility |
| `attendance_time_out(system, id, captured_at, …)` | app (via queue) | Closes the day; computes `total_minutes` |
| `attendance_abandon(record_id, reason)` | admin | Sets `abandoned` plus the closer trail |
| `attendance_projects_for_worker()` | app | The worker's owner's active `pc` + `pm` projects |
| `attendance_project_name(system, id, owner)` | both | Name resolution across the two id spaces |
| `attendance_data_owner()` | RLS / RPC internals | Which owner's data this worker belongs to |

Refusals come back as **stable codes**, mapped in the app by `AttendanceFailure`: `ALREADY_TIMED_IN`, `NOT_TIMED_IN`, `ALREADY_COMPLETE`, `TIMEOUT_BEFORE_TIMEIN`, `DEVICE_CLOCK_WRONG` (captured more than 2 minutes ahead of the server), `SHIFT_TOO_LONG`, `PROJECT_UNAVAILABLE`, `NO_OWNER_ASSIGNED`, `ACCOUNT_INACTIVE`, `NOT_A_WORKER`. An unmapped code lands on `Unexpected` and fails the test that enumerates the surface — which is the point.

### 4.4 Storage

Private bucket `attendance`. Object path is fixed by 0050 §7:

```
{worker_id}/{work_date}/{in|out}-{event_id}.jpg
```

The bucket's RLS policy compares the **first path segment** to `auth.uid()`, so this shape is a permission check, not a naming convention. Workers insert and read their own prefix; admins read all. The admin UI fetches signed URLs (`attSignedPhoto`, 1 h expiry) — never public URLs.

### 4.5 Edge Functions

**`attendance-signin`** — signs a worker into the Android app.

- The project enforces Cloudflare Turnstile on auth; Turnstile has no native Android SDK, and the app deliberately refuses to embed a WebView. `service_role` callers skip the captcha, so sign-in happens server-side here and the app makes one ordinary HTTPS call.
- 0052's throttle replaces the captcha — keyed on **email + forwarded IP**, because GoTrue's per-IP limit would see this function's IP for every worker in the country.
- **Eligibility is decided here**, not in the app: a deactivated or non-worker account never receives tokens, and any minted along the way are revoked before responding.
- **No CORS headers, deliberately** — a browser cannot read the response; the web portals have their own captcha-guarded login.
- `→ 200 { session, worker }` · `→ 4xx { error: INVALID_CREDENTIALS | NOT_A_WORKER | ACCOUNT_INACTIVE | TOO_MANY_ATTEMPTS | BAD_REQUEST }`

**`admin-create-user`** — owner/staff create an auth user (including workers) with the `service_role` key without disturbing their own session. Origin-allowlisted; returns `409` when the email already has an auth user.

---

## 5. Feature inventory

### Worker app

- Native sign-in through the Edge Function (no WebView, no captcha in the app)
- Versioned Terms gate with an offline-safe startup decision
- Dashboard: greeting, today's status, next action, step states, pending-sync badge
- Time In / Time Out flow: project picker → in-app camera → review → description chips → confirm
- Photo overlay burned in at capture: project name + Manila timestamp, auto-fitted
- Optional coarse location captured as metadata; never a gate
- Full offline capture: queued to Room, drained by WorkManager across process death and reboot
- Offline mirrors of today's record and the project list so nothing is blank without signal
- History: week/month spans, per-day hours, IN and OUT photos, tap for full size
- Profile: identity, password change, log out
- Bilingual failure notices; English-only screens

### Admin

- Today board with KPIs, status pills, photo thumbnails, search / filter, CSV export
- Clock-skew and offline-capture badges on every record
- Worker roster: create (auto worker number), edit, activate / deactivate
- Per-worker day detail with both halves and full-size photos
- Resolve a forgotten Time Out (abandon) with a who/when/why trail — never invents hours
- Projects view across both project systems with head-count today
- Range reports rolled up per worker and per project, open vs. abandoned counted separately, CSV download

### Platform

- One record per worker per day, enforced by a unique index
- End-to-end idempotency via client-generated `event_id`s
- RLS on every table; every write through a security-definer RPC
- Private, prefix-scoped photo storage with signed-URL reads
- Brute-force throttle on the native sign-in path
- Attendance fully isolated from the money model

---

## 6. Working on this

| Task | Command / rule |
|---|---|
| Build the APK | `./gradlew :app:assembleDebug` (set `JAVA_HOME`; the Metaspace jvmargs override in `gradle.properties` is required or KSP dies) |
| Run app tests | `./gradlew :app:testDebugUnitTest` |
| Run admin tests | `npm test` in `Dacs Web` |
| Syntax-check admin JS | `node --check js/attendance-admin.js` |
| New migration | next number = highest + 1, never reuse, never SQL-editor changes — see `supabase/migrations/README.md` |
| New DB field | a real column is mandatory; the shim maps camelCase → snake_case and a missing column fails the save **silently** |
| New admin view | `PRIMARY_NAV` **and** `_FOCUS_SUBVIEWS` in `js/admin.js`, the `*_VIEWS` group in `admin.html`, the `_visibleNav()` role filter, markup + `<script>` tag, RLS in the same migration |
| Room schema change | bump `@Database(version = …)`, write the migration in `DatabaseModule.kt`, commit the new `app/schemas/*.json` |

**Never** add a rate or peso field to attendance "to make reports useful", never derive the work date from the device zone or a UTC date, and never let the app write `attendance_records` directly.
