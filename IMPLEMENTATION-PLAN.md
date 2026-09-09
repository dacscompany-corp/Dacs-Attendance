# Implementation Plan — Weekly Reward & Location Verification

Built from the rulings in [MVP-DECISIONS.md](MVP-DECISIONS.md). Read that first; this file assumes
every decision in it.

> **DELIVERED — 2026-09-09.** Both phases are built, applied and verified on a physical handset.
> This file is kept as the record of what was intended and in what order; §6 of MVP-DECISIONS.md
> records what actually happened, including three bugs this plan did not anticipate.
>
> Migrations **0065–0071** are applied and tracked. Two numbers moved during the work: what this
> plan calls `0067_attendance_geofence.sql` shipped as **0068**, because 0067 was spent on a
> security fix — `attendance_week_days` had been left callable by every signed-in worker, since
> `revoke ... from public` does not remove what Supabase's default privileges grant directly to
> `anon` and `authenticated`. Two further migrations were not foreseen here at all: **0070**,
> rewriting the schedule writes as RPCs after direct inserts failed silently, and **0071**, the
> geofence editor's own RPC.

Next migration number was **0065** (highest on disk was `0064_folder_completion.sql`).

---

## Sequencing: two phases, and they are not equal

**Phase 1 — Weekly reward.** A *read* over attendance records that already exist, plus a small
per-project config table. No change to how attendance is recorded, no new permission, no Room
migration, nothing that can stop a worker clocking in. If it is wrong, a report is wrong.

**Phase 2 — Location verification.** Rewrites the write path, adds a permission the app has never
requested, and turns GPS from unused plumbing into a gate that can refuse attendance. If it is
wrong, workers cannot clock in and lose ₱500.

**Ship Phase 1 first.** *(Done, and it was the right call: the reward was in use and verified
before anything began refusing attendance.)* It delivers the reward — the thing the requirements document is mostly
about — at a fraction of the risk, and it lets the reward rule be validated against real weeks
before anything starts blocking Time Ins.

---

## Phase 1 — Weekly reward

### 0065_attendance_project_config.sql

The per-project attendance settings table (decision 12). Geofence columns land in Phase 2; this
migration creates the table and the non-location settings.

- **`attendance_project_config`** — keyed `(project_system, project_id)`, `project_system` checked
  against `('pc','pm')`, plus `owner_id` for tenancy.
  - `working_days smallint[]` default `{1,2,3,4,5}` — ISO weekday numbers, the standing pattern
    (decision 13). A condo that bans weekend fit-out work sets this once instead of entering
    closure dates forever.
  - `start_time_override time` — null means fall back to the company default (decision 10).
  - `attendance_enabled boolean`.
- **`attendance_project_closure`** — `(project_system, project_id, closed_on date, reason text)`,
  PK on the first three. The exception dates on top of the standing pattern.
- **`attendance_config`** — per owner: `default_start_time time` default `09:00`,
  `reward_amount numeric` default `500`, `evaluation_grace_hours int`.
- RLS: admin full access via the existing `is_owner()` / `is_staff()` / `can_access()` helpers;
  workers get select on their own owner's rows so the app can show the cutoff.
- `attendance_touch_updated_at()` trigger, as every other attendance table uses.

### 0066_attendance_weekly_reward.sql

- **`attendance_weekly_rewards`** — `unique (worker_id, week_start)`.
  - `week_start date` (Monday), `week_end date` (Friday)
  - `required_days`, `completed_days`, `on_time_days`, `late_days`, `missing_days` — §42's fields
  - `status text` checked against `('in_progress','qualified','disqualified')`
  - `amount numeric` — **snapshotted**, not read from config at display time (decision 15)
  - `paid boolean default false`, `paid_at`, `paid_by` (decision 22)
  - `evaluated_at timestamptz`
- **`attendance_evaluate_week(p_owner uuid, p_week_start date)`** — computes and upserts. Must be
  **idempotent** and must **refuse to run before the grace period has elapsed** (decision 11).
  That refusal is what makes evaluate-on-read safe.
- **`attendance_reward_progress(p_worker uuid, p_week_start date)`** — live, never writes. Feeds
  §37's in-progress display and §43's `In Progress` status.

**The evaluation logic, precisely:**

1. The worker's project for the week = the distinct projects they timed in at during Mon–Fri.
2. Required days = Mon–Fri, minus days the standing pattern excludes, minus closure dates for
   that project. A worker with no Time Ins at all has no inferable project — they are at ₱0 on
   absence anyway, so nothing breaks.
3. For each required day: is there a `timein_at`, and is its Manila time-of-day at or before the
   effective start time **for the project they timed in at that day**.
4. All required days on time → `qualified`, amount = config. Otherwise `disqualified`, amount = 0.
5. Never read `status`, `timeout_at` or `total_minutes` (decision 2).

**Scheduling.** Make the function idempotent and grace-gated, then call it from the admin view for
any unevaluated past week. That guarantees evaluation happens without depending on `pg_cron` being
enabled — add cron later as an optimisation, not a prerequisite.

### Admin (`Dacs Web`)

- New view **`attRewards`**. The full six-step checklist from `CLAUDE.md` applies:
  `PRIMARY_NAV` entry, **`_FOCUS_SUBVIEWS`** (the one that always gets missed), the `ATT_VIEWS`
  group in `admin.html`, the `_visibleNav()` role filter, section markup plus `<script>` tag, and
  RLS in the same migration.
- §40's roll-up: Worker, Week, Complete, On-Time, Late, Missing, Status, Reward — plus the paid
  toggle, and **CSV export**, which is now the actual payroll input rather than a convenience.
- Project config UI — working days, start time override, closure dates — extending the existing
  `attProjects` view.
- New pure functions go **inside the `// ==== ATT REPORT ENGINE ====` markers** so
  `tests/attendance.test.js` picks them up. No DOM, no network, no module state.

### App

- `domain/WeeklyReward.kt` — pure, unit-tested, matching the house style of every other domain file.
- Reward progress widget on the Dashboard: **its own five-cell row**, not a modification of the
  six-day `weekStrip()`. The strip stays honest about the six-day working week; the reward owns
  its five required days (open item 3).
- Repository method + DTO for `attendance_reward_progress`.

No Room migration in this phase — reward progress is server-computed and read-only.

---

## Phase 2 — Location verification

### 0068_attendance_geofence.sql

- **`attendance_project_geofence`** — `(project_system, project_id, effective_from timestamptz,
  latitude, longitude, radius_m, enabled)`. A **history table, not columns**, because decision 18
  requires verifying against the geofence in force at `captured_at`. Lookup is the latest row with
  `effective_from <= captured_at`. Editing a geofence appends; it never rewrites, so §33's
  "changing a geofence must not invalidate previously recorded attendance" holds by construction.
- Haversine helper in plpgsql. No PostGIS, no map service (rule 20).
- **`attendance_projects_for_worker()` gains a filter**: a project with no valid enabled geofence
  is not attendance-enabled and never reaches the picker (decision 20).
- Evidence columns on `attendance_records`, both halves: `location_status`, `distance_m`,
  `is_mock`, plus a **snapshot** of the geofence used — `geofence_lat`, `geofence_lng`,
  `geofence_radius_m`. Snapshotted for the same reason `worker_name` is.

### 0069_attendance_location_rpcs.sql

Rewrite `attendance_time_in` / `attendance_time_out` to accept the new parameters, verify, and
raise the new codes. **This is the subtle part:**

> The RPC's behaviour on `OUTSIDE_RADIUS` depends on `p_was_offline`.
>
> - **Online submission** → raise `OUTSIDE_RADIUS`. The worker is standing there and finds out now.
> - **Offline-queued submission** → the device already pre-checked and already told the worker it
>   saved. Record it, flagged for admin review (decision 19). Destroying it is the worse error.

A naive implementation raises in both cases and silently eats days of recorded work.

**Known hole, accepted:** `was_offline` is client-supplied, so a modified client could always claim
offline to downgrade a refusal into a flag. The mitigation is that it lands in front of an admin
rather than passing silently — it is a review queue, not a wall.

### App

- `ACCESS_FINE_LOCATION` in the manifest. Coarse is useless for a site radius (~1–3 km accuracy).
  The existing manifest comment saying location is never a gate is now **wrong and must be
  rewritten** — it is a deliberate decision record, not an incidental comment.
- **Location provider**: add `play-services-location` and use `FusedLocationProviderClient`.
  Framework `LocationManager` avoids the dependency but is materially worse on time-to-fix, which
  is precisely the failure mode that hurts on cheap phones.
- `domain/LocationVerification.kt` — pure: haversine, threshold comparison, result-code
  determination. Unit tested, no Android imports, like every other domain file.
- `AttendanceFailure` gains `OutsideRadius`, `MockLocation`, `LocationPermissionDenied`,
  `ProjectGeofenceUnavailable`. **The enumeration test will fail until each is mapped — that is
  the tripwire working as designed.**
- **Room v3 → v4**: `pending_submission` gains `isMock`, `locationStatus`, `distanceM` and the
  geofence snapshot; `cached_project` gains geofence columns so the device can pre-check offline.
  Migration in `DatabaseModule.kt`, and `app/schemas/4.json` committed.
- TimeFlow: acquire location around the shutter, and handle four refusal states with copy a worker
  can act on. Permanent denial needs a **deep-link into system settings** — Android's "Don't ask
  again" means the app cannot re-prompt, and this is the one refusal the worker can actually fix.
- **Terms version bump.** Requiring precise location as a condition of recording work needs a
  clause. `TermsGate` then forces re-acceptance on next launch — the mechanism already exists.

### Admin

- Geofence config in the project config UI: latitude, longitude, radius, enabled. A "use my current
  location" button is worth it if admins ever visit sites.
- Verification detail on the worker-day view (§40): status, accuracy, distance from geofence, and
  the failure reason where there is one.
- Verification badges on the Today board, alongside the existing clock-skew and offline badges.

---

## Cross-cutting cleanup

| What | Where | Why |
|---|---|---|
| Narrow the isolation rule | `Dacs Web/CLAUDE.md`, `js/attendance-admin.js` header | Decision 15 puts a peso column in an attendance table. The rule's purpose survives; its wording does not |
| Rewrite the location comment | `app/src/main/AndroidManifest.xml` | It currently records the opposite decision |
| Update the architecture doc | `ARCHITECTURE.md` | New tables, RPCs, views, permission, Room version |
| Update the requirements doc | *MVP TERMS & REQUIREMENTS* §35, §38, §37, rules 3, 21, 22 | Listed in §3 of MVP-DECISIONS.md. Also re-save as UTF-8 — it is currently mojibake'd |

---

## Verification

| Layer | Command |
|---|---|
| App domain + ViewModels | `./gradlew :app:testDebugUnitTest` |
| App build | `./gradlew :app:assembleDebug` (set `JAVA_HOME`; the Metaspace override in `gradle.properties` is required or KSP dies) |
| Admin pure functions | `npm test` in `Dacs Web` |
| Admin syntax | `node --check js/attendance-admin.js` |
| Migrations | `supabase/tests/` — follow the `0059_preflight.sql` / `0059_verify.sql` pattern, which exists because a migration once assumed an empty table and failed against live data |

Migrations are **never** applied through the SQL editor, and numbers are never reused — see
`supabase/migrations/README.md`.

---

## Risk register

| Risk | Phase | Note |
|---|---|---|
| Workers locked out by GPS | 2 | Largely mitigated by decisions 16 and 20, but a cheap phone that never gets a fix still degrades to flagged-and-accepted, which is the intended behaviour rather than a bug |
| `was_offline` downgrade hole | 2 | Accepted; review queue rather than a wall |
| Mock detection defeatable | 2 | Catches casual spoofing only. The record must not imply certainty it does not have |
| Play Services absent | 2 | Rare but real on the cheapest handsets. Decide whether to fall back to `LocationManager` or refuse |
| Supabase egress | Both | Already over quota with the grace period expired. §40's verification review pulls more signed photo URLs |
| Requirements doc drift | Both | Six sections now describe behaviour that was decided against. Anyone building from it alone builds the wrong rule |

---

## Outcome

| | |
|---|---|
| Migrations | 0065–0071 applied and tracked on `main` |
| App | 176 tests, `com.dacs.attendance.debug` verified on device |
| Admin | 404 tests across the Dacs Web suite |
| Device test | All nine paths exercised — see MVP-DECISIONS.md §6 |

### Where this plan was wrong

**It assumed the risk was in Phase 2.** The plan says Phase 1 is "a *read* over attendance records
that already exist… if it is wrong, a report is wrong", and treats location as the dangerous half.
That held for the *database* work. But the worst bug of the whole project was in neither phase's
design: the app read a failed network call as a signed-out session and locked workers out of the
entire offline layer — the queue, the mirrors and the cached picker all stranded behind a login
form a worker with no signal could not complete. It had been there before this work started.

**It underestimated what only a device would reveal.** Three bugs — the offline lockout, WorkManager
backoff stranding a synced record for seven minutes, and a queue race uploading one photo three
times — needed a real handset, a real loss of signal, and somebody watching. The verification
section of this plan lists test commands and a browser check; it should have listed a phone.

**Two admin surfaces were missing from it entirely.** The geofence editor was never planned, so
coordinates had to be typed into the SQL editor by hand until it was built. Neither was the fact
that the schedule tables needed RPCs rather than direct inserts, which cost an hour of silent
failures before the pattern already stated in 0050's header was applied.

### Still not done

- `require_geofence` is **off**, so nothing is radius-gated server-side
- The holiday rule has never been demonstrated end to end
- The isolation-rule wording in `Dacs Web/CLAUDE.md` still contradicts decision 15
