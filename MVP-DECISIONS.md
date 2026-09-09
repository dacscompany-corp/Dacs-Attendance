# MVP Decisions — Weekly Reward & Location Verification

Decisions taken against *ATTENDANCE MANAGEMENT SYSTEM — MVP TERMS & REQUIREMENTS* (§32–45).
Where a decision differs from that document, **this file wins** and the source document needs updating — see §3.

Status: **all policy decided, and both phases built and verified on hardware** (2026-09-09).
See §6 for what shipped and what the plan got wrong.

---

## 1. The reward rule, as decided

> A worker earns **₱500** for a week when **every required day** between Monday and Friday has a
> **Time In recorded at or before that project's start time**.
>
> Any required day with a late Time In, or with no Time In at all, forfeits the whole ₱500.
> There is no partial reward.

Evaluation reads **`timein_at` and nothing else** — never `status`, never `timeout_at`, never
`total_minutes`. This is the single most important property of the rule: it makes the reward
independent of every admin action, so nobody's cleanup work can move anyone's money.

### Required days

Monday–Friday in `Asia/Manila`, **minus** any day the worker's posted project was closed.

A week with one closed day requires four days, and four on-time Time Ins earn the full ₱500.
Workers are never penalised for a day the company chose not to open.

---

## 2. Decisions and why

| # | Decision | Reasoning |
|---|---|---|
| 1 | Required days are **Mon–Fri**, Asia/Manila | Aligns with the existing Monday week start in `historyRange()`, which is already deliberate ("the working week they are standing in", not a rolling 7 days). No new week-boundary logic needed |
| 2 | Qualification reads **`timein_at` only** | Decouples the reward from `status`, from `attendance_abandon`, and from admin timing entirely. Also disposes of the `STILL_TODAY` collision, where a Friday forgotten Time Out cannot be resolved before Saturday |
| 3 | **Time Out is required of workers, but is not a reward input** | The worker demonstrably turned up — they timed in with a timestamped photo. What they failed at is the recording, not the work. A dead battery at 5pm should not cost ₱500 |
| 4 | **Abandoned and open days are irrelevant** to the reward | Follows from #2. An abandoned day and an un-resolved open day evaluate identically |
| 5 | A day with **no Time In is ₱0** | Absence fails the same way lateness does |
| 6 | A closed day **shrinks the week** (4/4 still earns ₱500) | PH regular holidays land on a weekday 8–10 times a year. Any other rule zeroes the reward for every worker in most months, through no fault of theirs |
| 7 | Closures are declared **per project** | Confirmed against real operations: whether a day is a working day depends on the project / condo unit. Sites work holidays on premium pay; local charter days close one city and not another. Company-wide scope would be wrong the moment one crew works a holiday |
| 8 | A worker's project for the week is **inferred from their own Time Ins that week** | Workers are not assigned to projects — they pick one per Time In, and `attendance_projects_for_worker()` returns all of them. Inference needs no roster and no new admin work, and is correct for the ordinary case of someone posted to one site for weeks |
| 9 | Split week: the day is **not required** if the worker's posted site was shut | Even where another project was open. Nobody sent them there |
| 10 | Start time: **9:00 AM company default, per-project override** | Condo fit-out work often has building-mandated hours. Same per-project config as closures |
| 11 | Evaluation uses a **grace period, then freezes** — e.g. Monday for the week just ended | The app is built for sites with no signal. A Friday Time In captured offline and synced Monday must not arrive after the freeze and cost a worker ₱500 for a day they worked on time |
| 12 | Closures, start time and geofence live in **one per-project attendance config table**, keyed `(system, project_id)` | `folders` and `construction_projects` are owned by other modules; attendance must stay isolated from them. Also gives the geofence somewhere to be effective-dated, which §33 requires so that editing a geofence cannot retroactively invalidate old attendance |
| 13 | Project config carries a **standing working pattern plus exception dates** | A condo that bans weekend fit-out work must not be re-entered as closure dates every week |
| 14 | The reward is **status only — no accounting entry, ever** | Payment happens outside the system. Attendance keeps its full isolation: nothing feeds Spent / Earned / Profit, and no bonus can draw down a pakyaw contract. Accepted tradeoff: the reward cost never appears in company overhead |
| 15 | The reward record **snapshots the amount** rather than reading it from config at display time | Same reasoning as `worker_name` and `timein_project_name`. If ₱500 ever changes, historical records must keep what was actually awarded |
| 16 | GPS **refuses only when the location is known-bad**; uncertainty is recorded and flagged | Keeps the anti-spoofing teeth where they matter without punishing a worker whose phone can't get a fix under scaffolding. Also defuses Android 12's approximate-only permission toggle, which degrades to a flag rather than locking that worker out permanently |
| 17 | Admin review of a flagged record **never affects the reward** | Consistent with decision 2. No admin action, on any schedule, can move anyone's money. Accepted tradeoff: a bogus record that slips through still earns the bonus. Review's value is hours, evidence, and spotting patterns — twenty unverified Time Ins is a conversation |
| 18 | The device **pre-checks at capture** against the cached geofence; the server **re-verifies** against the effective-dated geofence | The pre-check is the only way to give an offline worker an immediate, honest answer. Server re-verification is the anti-tamper leg. Effective-dated because §33 requires that editing a geofence cannot retroactively invalidate old attendance |
| 19 | A server-side refusal on a record the device accepted **keeps the record, flagged for admin review** | The server checks the same effective-dated geofence the device used, so a mismatch usually means drift or edited coordinates rather than a worker who wasn't there. Destroying a day of recorded work over it is the worse error |
| 20 | A configured geofence is a **precondition of a project being attendance-enabled** | An unconfigured project never reaches the worker's picker, so it can never lock a crew out or cost them ₱500 for an admin omission. The failure surfaces to the admin as "not ready". Settles the §41 contradiction: no valid geofence means not attendance-enabled |
| 21 | Descriptions stay **optional** | Matches what is already built at every layer. Keeps a text box from standing between a worker and clocking in, and forced text tends to become the same chip tapped every day. **Overrides rule 3** |
| 22 | The reward record carries a **paid marker** | Payment happens off-system, so without it the system knows who qualified but never who was paid, and a disputed payout has no record anywhere. A status marker only — no amount flows anywhere, so isolation holds |

### Edge cases already resolved by the above

- **Worker timed in zero days that week** — no project can be inferred, but they are at ₱0 on absence anyway. Nothing breaks.
- **Admin resolves an abandoned day after evaluation** — cannot affect the reward; evaluation never read `status`.
- **Offline record synced days late** — keeps its original `timein_at`. The RPC's clock guard is one-sided (`p_captured_at > now() + 2 min` → `CAPTURED_IN_FUTURE`), so a record synced days later is accepted with its original timestamp intact. §39 is satisfied by the existing design.

### Location verification dispositions

| §33 result | Disposition | Effect on the reward |
|---|---|---|
| `VERIFIED` | Recorded, verified | Day counts |
| `OUTSIDE_RADIUS` | **Refused** | No record → day missing → ₱0 |
| `MOCK_LOCATION` | **Refused** | No record → day missing → ₱0 |
| `PROJECT_GEOFENCE_UNAVAILABLE` | **Refused** — see caveat below | No record → day missing → ₱0 |
| Location permission denied outright | **Refused** | No record → day missing → ₱0 |
| `LOW_ACCURACY` | Recorded, flagged unverified | Day counts |
| `LOCATION_UNAVAILABLE` | Recorded, flagged unverified | Day counts |

So the only way GPS costs a worker the bonus is being demonstrably somewhere else, faking it, or
declining to be located at all.

**Caveat on `PROJECT_GEOFENCE_UNAVAILABLE`** — as decided, an unconfigured project silently locks
out every worker on that site, and each of them loses ₱500 for an admin omission. Recommended
mitigation: make a configured geofence a **precondition of a project being attendance-enabled**,
so an unconfigured project never reaches the worker's picker (`attendance_projects_for_worker()`
filters it out) and the failure surfaces to the admin as "not ready" rather than to the worker as
a locked door. This also settles the §41 contradiction: no valid geofence means not
attendance-enabled. **Pending confirmation.**

**Note on permission denial** — Android's "Don't ask again" means the app cannot re-prompt; it has
to deep-link into system settings. Since this is the one refusal a worker can actually fix
themselves, the copy has to say so plainly.

**Note on mock detection** — client-reported and defeatable on a rooted device. It catches casual
spoofing, not determined spoofing, and the record should say so rather than imply certainty.

---

## 3. Where this overrides the source document

These sections of *MVP TERMS & REQUIREMENTS* no longer describe the agreed behaviour:

| Section | Says | Now |
|---|---|---|
| §35 | "Time In and **Time Out** completed for each required day" | Time Out is not a qualification input |
| §38 | "Incomplete Attendance = ₱0" | A day with a Time In and no Time Out still qualifies |
| Rule 22 | "Each required day must have valid **completed** attendance" | Same — only the Time In is tested |
| §35 / Rule 21 | "**5** required attendance days" | Required days can be fewer than 5 when the site was closed |
| §37 | Progress examples showing a missing day | Still correct for a genuinely absent day; a day with no Time Out is **not** "Missing" |
| Rule 3 | "Attendance records must contain the required photo and **description** information" | Photo is required; description stays optional (decision 21) |

Anyone reading the requirements document as the source of truth will otherwise build the wrong rule.

---

## 4. Still open

### Done since this list was written

- ~~Terms version bump for precise location~~ — done. `VERSION` is `2026-09-v3`, with a
  **Location check** clause and a **Weekly attendance reward** clause. Neither names a peso figure
  or a cutoff time, because both are configuration the Owner can change and this text is hashed
  into `agreement_events` as evidence.
- ~~The six-day strip versus the five-day reward~~ — done. The reward has its own five-cell row
  above the attendance strip, so the strip stays honest about the six-day working week.
- ~~Accuracy threshold and default radius~~ — set to **50 m** and **150 m**, both editable per
  project rather than compiled in.

### Still outstanding

1. **Amend the isolation rule rather than leave it false.** `Dacs Web/CLAUDE.md` and the header of
   `attendance-admin.js` both say flatly that there is no peso column in the attendance tables.
   Decision 15 puts one there. The rule's *purpose* survives untouched — nothing feeds the money
   model — so the wording needs narrowing, not deleting. Something like: *the weekly reward amount
   is a reported figure only; it creates no accounting entry and nothing may make one from it.*
   Left as-is, the next person reads a rule the code visibly breaks and has to guess which is wrong.

2. **`require_geofence` is still off.** Nothing is radius-gated server-side. The refusal observed
   on 2026-09-09 came from the DEVICE's own pre-check, which a modified client could skip. Turn the
   flag on once real sites have coordinates — see the rollout note in 0068's header for why it
   ships off.

3. **The holiday rule has never been demonstrated end to end.** Every part underneath it works, but
   nobody has yet marked a weekday closed and watched a Rewards row drop to `REQUIRED 4`. It is the
   decision this document spends the most words on and the one with no observed proof.

4. **Worker names render as `—` in the reward CSV** when a profile has no `display_name`. The name
   is snapshotted at evaluation, deliberately, so there is nothing to fall back to at render time.
   A fallback would have to be chosen at evaluation (worker number, or the email local part) and
   would only help future weeks.

---

## 5. What this implies for the build

Nothing here needs a worker→project roster, a holiday calendar import, or any change to how
attendance is recorded. The reward is a **read** over existing `attendance_records` plus a small
per-project config table, evaluated on a delay and frozen into a reward record.

The location work is the opposite — it changes the write path, adds a permission the app has
never asked for, and turns GPS from unused plumbing into a gate. It should be planned separately,
after decision 1 and 2 above.

---

## 6. What shipped, and what this plan got wrong

Built across 2026-09-08/09 and verified on a physical handset, not just in tests.

**Delivered:** migrations 0065–0071; the admin Rewards view, schedule editor and geofence editor;
the app's reward strip, location capture, offline queue changes and Terms clauses. 176 app tests
and 404 Dacs Web tests, all passing.

**Verified on hardware**, because none of the following could be settled by unit tests:

| Behaviour | Evidence |
|---|---|
| Offline capture keeps its shutter time | Log `captured=07:18:09.290Z`, DB `in_manila 15:18:09.29` |
| No GPS fix is recorded and flagged, never refused | `timein_location_status = location_unavailable`, record present |
| Inside the fence verifies with a real distance | `verified`, 18.25 m against a 150 m radius |
| Outside the fence is refused at the gate | No row written, nothing queued, no upload attempted |
| Hours come from the server | `total_minutes = 19` for 15:18 → 15:38 |

### Three bugs the plan did not anticipate

All three needed a real phone, a real loss of signal, and somebody watching. None would have been
caught by any test written in advance, and each is worth remembering before the offline layer is
touched again.

1. **The offline lockout.** `currentWorker()` wrapped a network profile read in `runCatching` and
   returned null on failure, so *unreachable* and *signed out* became the same answer. A worker
   with no signal was dropped on a login form they could not complete — with the queue, the
   mirrors and the cached picker all stranded behind it. Fixed by `WorkerCache`, plus a second
   gap where `signIn()` did not seed that cache, so the first offline launch still had nothing to
   recall. Two bugs stacked, which is why the first fix looked like it had failed.

2. **Backoff stranding a synced record.** WorkManager doubles its retry delay on every failure, so
   a morning without signal left the next attempt seven minutes out. The worker stood there
   looking at "Not sent yet" with no way to tell whether it was broken or waiting. Opening Home
   now starts a fresh attempt, discarding the accrued delay.

3. **The queue race.** Work was enqueued uniquely per EVENT while the worker drained the queue
   GLOBALLY, so several requests each took the oldest row and uploaded the same photo. Observed:
   one event sent three times in two seconds. The server was never at risk — the event id is the
   idempotency key — but the photo went to Storage three times, on a project already over its
   egress quota. Fixed with a single work name and a worker that drains in one pass.

### One thing the plan got right, and one it got lucky on

The **phasing was correct**: shipping the reward first meant Phase 1 was in use and verified before
anything started refusing attendance.

The **`was_offline` branch in 0069** — refusing a live `OUTSIDE_RADIUS` but keeping and flagging a
queued one — was written from reasoning alone and has still never fired in anger. It is the least
exercised decision in this document.
