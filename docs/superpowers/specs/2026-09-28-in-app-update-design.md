# In-App Update — Design

**Date:** 2026-09-28
**Status:** Approved in brainstorming, awaiting spec review
**Repos touched:** `Dacs Attendance` (worker APK) and `Dacs Web` (admin portal + `supabase/migrations`)

## Goal

When the office publishes a new worker APK, every phone running an older
build shows a full-screen, **non-dismissable** "New Update is Available"
dialog, downloads the new APK in-app and hands it to Android's installer —
the pattern real consumer apps use, adapted to sideloaded distribution.

## Decisions (from brainstorming)

| # | Decision | Why |
|---|---|---|
| 1 | **Self-hosted, not Play Store.** APK lives in Supabase Storage; the app checks Supabase. | The app is distributed as a direct APK link. Play In-App Updates API only works for Play-installed apps. |
| 2 | **Every update is required.** No "Later", no close, Back swallowed. | User's choice. Matches the server-side `min_app_version` gate (0077) that already refuses old builds. |
| 3 | **Publish from the admin portal** ("App Updates" section in `admin.html`). | No Supabase dashboard steps to forget; version is read from the APK, not typed. |
| 4 | **Publishing raises `attendance_config.min_app_version`** in the same transaction. | Popup and server refusal can never disagree. |
| 5 | **Never blocks when the check fails offline** — unless a newer release is already cached. | Offline Time In is a core feature; a worker on a no-signal site must still be able to record. Once a newer version is known, the block stays. |
| 6 | **Screen copy English-only; failure copy bilingual (EN + TL).** | Existing v2 copy rule. |
| 7 | **Animated illustration in pure Compose** (no Lottie). | Motion without a new dependency or APK growth; respects system "remove animations". |
| 8 | **DACS green theme**, not the reference's purple. | Consistency; workers recognise it as the real app, not an ad. |
| 9 | **Only the owner (`is_owner()`) may publish.** | Publishing locks every older phone out of attendance — owner-level consequence. |

## Architecture

```
 Owner (admin.html)                Supabase                      Worker phone (APK)
 ─────────────────                 ────────                      ──────────────────
 pick .apk ─ parse versionCode ─┐
          ─ SHA-256 in browser ─┤
                                ├─► Storage: app-releases/<vc>.apk
                                └─► rpc app_publish_release ──► app_releases row
                                                             └► min_app_version = vc
                                     rpc app_latest_release ◄── on start / resume /
                                     (anon)                  ──► APP_UPDATE_REQUIRED
                                                                 │ newer than mine?
                                                                 ▼
                                                              UpdateRequiredDialog
                                     Storage GET  ◄──────────── download + verify SHA-256
                                                                 ▼
                                                              Android package installer
```

## Server — migration `0079_app_releases.sql` (Dacs Web)

**Table `app_releases`**

| column | type | notes |
|---|---|---|
| `version_code` | integer PK | must be strictly greater than every existing row |
| `version_name` | text not null | e.g. `0.4.0` |
| `release_notes` | text | optional "What's new", shown under the fixed body copy |
| `storage_path` | text not null | object path in `app-releases` bucket |
| `size_bytes` | bigint not null | shown in admin; lets the app sanity-check |
| `sha256` | text not null | lowercase hex, 64 chars; app verifies before install |
| `published_at` | timestamptz default now() | |
| `published_by` | uuid → auth.users | |

RLS on, **no direct policies**; all access through the two functions below.
Outside the money model like all attendance tables.

**Storage bucket `app-releases`** — public read (download must work before
login; an APK contains no secrets — the anon key is already public). Upload
restricted by a storage policy to `is_owner()`. MIME `application/vnd.android.package-archive`,
size limit 50 MB.

**`app_latest_release()`** — `security definer`, `stable`, granted to
`anon` and `authenticated`. Returns the single highest-`version_code` row
(`version_code, version_name, release_notes, storage_path, size_bytes, sha256`)
or no row.

**`app_publish_release(p_version_code, p_version_name, p_release_notes, p_storage_path, p_size_bytes, p_sha256)`**
— `security definer`, `authenticated` only.
- Raises unless `is_owner()`.
- Raises `VERSION_NOT_NEWER` unless `p_version_code > max(version_code)` and `> attendance_config.min_app_version`.
- Validates sha256 format and size > 0.
- Inserts the row **and** `update attendance_config set min_app_version = p_version_code` in one transaction.

## Admin portal — "App Updates" (Dacs Web)

New section in `admin.html`, logic in new `js/app-updates-admin.js`,
following `attendance-admin.js` conventions (vanilla JS, no build step).

- File picker (`.apk`). In the browser:
  - Read `versionCode` / `versionName` / package name from the APK's binary
    `AndroidManifest.xml` (APK is a zip; small binary-XML parser, no new
    library). Refuse if package ≠ `com.dacs.attendance` (catches the
    `.debug` build).
  - Compute SHA-256 with `crypto.subtle.digest`.
  - Show: "Version 4 (0.4.0) · 5.0 MB" and refuse Publish if not newer than current.
- Optional "What's new" textarea.
- **Publish**: upload to `app-releases/<versionCode>.apk`, then call
  `app_publish_release`. If the RPC fails, delete the uploaded object.
- Confirmation text before publishing: *"Every phone on an older version will
  be blocked from Time In / Time Out until it updates."*
- Standing warning: *"Must be a release build signed with the same key as
  before, or phones cannot install it."*
- History table of past releases, current one marked; shows size as the
  egress reminder.

## Worker app (Dacs Attendance)

**New units**

| Unit | Layer | Responsibility |
|---|---|---|
| `AppRelease` | domain | data class mirroring `app_latest_release()` |
| `AppUpdateChecker` | domain | pure: `requiredUpdate(installed: Int, latest: AppRelease?): AppRelease?` |
| `AppUpdateRepository` | data | calls the RPC; caches last-seen newer release (DataStore/prefs); downloads APK to `cacheDir/updates/` with progress; verifies size + SHA-256; deletes on mismatch |
| `AppUpdateViewModel` | ui | state machine (below); triggers checks |
| `UpdateRequiredDialog` | ui/components | full-screen blocking card with animated illustration |
| `ApkInstaller` | data | `canRequestPackageInstalls()` check, opens "Install unknown apps" settings, launches install intent via `FileProvider` |

**State machine**

```
Hidden ──(newer release known)──► Available
Available ──UPDATE NOW──► NeedsInstallPermission? ──granted──► Downloading(pct)
Downloading ──ok+hash ok──► ReadyToInstall ──tap──► (Android installer)
Downloading ──io error / hash mismatch──► Failed(reason) ──Try again──► Downloading
ReadyToInstall ──installer cancelled, user returns──► ReadyToInstall (no re-download)
```

**When it checks**
- `MainActivity` start and every `ON_RESUME`.
- Immediately when any submission returns `AttendanceFailure.AppUpdateRequired`.
- Network failure → keep current state (cached release still blocks; nothing cached → Hidden).
- The cache is cleared when installed `versionCode >= cached.version_code`.

**Placement** — rendered in `MainActivity` above `AttendanceRoot`, so it
covers login, terms, home, history, profile and the Time In/Out flow. Back
is consumed while visible. Widget taps open the app, landing behind the dialog.

**Manifest** — add `android.permission.REQUEST_INSTALL_PACKAGES` and an
`androidx.core.content.FileProvider` (`${applicationId}.fileprovider`,
`res/xml/update_file_paths.xml` exposing only `cache/updates/`).

**Dialog UI** (DACS theme)
- Header: `GreenDeep` → `Green` gradient, twinkling star dots, animated rocket
  (gentle vertical bob, flickering flame) over drifting white clouds —
  drawn with Compose `Canvas` / vectors and `rememberInfiniteTransition`;
  static when system animator scale is 0.
- Title: "New Update is Available"
- Body: "A new version is released, please update to get new features" + release notes (if any) + "Version 0.4.0".
- Primary button (`PrimaryActionButton`): `UPDATE NOW!` → `Downloading… 45%` (disabled) → `Install` → on failure `Try again`.
- Install-permission step: "Allow DACS Attendance to install updates" + button to the settings toggle.
- Failure notice (bilingual, via existing `FailureNotice` pattern):
  - Download: "Download failed. Check your internet and try again." / TL twin
  - Hash: "The update file was damaged. Try again." / TL twin

## Error handling summary

| Case | Behaviour |
|---|---|
| Check fails, nothing cached | App works normally |
| Check fails, newer release cached | Dialog stays |
| Download interrupted | `Failed`, partial file deleted, Try again |
| SHA-256 / size mismatch | File deleted, `Failed`, Try again |
| Install permission denied | Stays on permission step |
| Installer cancelled | Back to `ReadyToInstall`, no re-download |
| Signed with different key | Android refuses install — prevented upstream by admin warning; not detectable in-app |
| Queued offline Time In from old build | Server refuses (0077) → kept as RETRY → resent by new build with original shutter time |

## Testing

- **Unit (JVM):** `AppUpdateChecker` comparisons; view-model transitions incl. cancel-install and retry; SHA-256 verify + delete on mismatch; cache survive-offline and clear-after-update.
- **SQL:** `app_publish_release` refuses non-owner, refuses non-newer version, raises `min_app_version`; `app_latest_release` callable as anon.
- **E2E (emulator):** install v(N) → publish v(N+1) from admin → dialog appears, Back ignored → permission step → download → installer → relaunch on v(N+1), no dialog. Plus: queue an offline Time In on v(N), update, confirm it syncs.

## Rollout

1. Apply migration 0079 (does **not** change `min_app_version` on its own).
2. Ship the admin section.
3. Bump app to `versionCode = 4`, `versionName = "0.4.0"`; build signed release.
4. **Bootstrap:** v4 is the first build that contains the dialog, so v3 phones
   cannot see it. Distribute v4 the old way one last time. **Only after**
   v4 is on every phone, publish it from the admin page so the release
   history starts at 4 — publishing raises `min_app_version` to 4, so doing
   it earlier would refuse Time In on v3 phones that have no dialog to
   explain why (they would only see the existing update-required notice).
5. From v5 on, publishing from the admin page is the whole release process.

## Out of scope

- Optional/"Later" updates, staged rollouts, per-worker targeting.
- Play Store distribution.
- Delta/partial updates.
- Rollback (publishing a *lower* version is refused by design; ship a fix as a higher version).
