## Context

**The archive** — the term used throughout this change — is the unpacked Endomondo GDPR data export dated 2020-11-01, located at `data/endomondo-strava-exports/endomondo-2020-11-01/` (git-ignored personal data; the directory is configurable, this is its actual location). Migration reads exactly two of its subdirectories:

- `Workouts/` — 324 files forming **162 same-basename pairs** named `YYYY-MM-DD HH_mm_ss.0` + `.json`/`.tcx`. The JSON carries metadata the TCX cannot (name, sport, source, calories, `pictures` references); the TCX carries the uploadable track. By `source`: 94 TRACK_MOBILE, 42 TRACK_SAMSUNG_GEAR, 25 INPUT_MANUAL, 1 IMPORT_GPX. Activities span 2011–2020.
- `resources/gfx/` — the exported photo files: **81 JPEGs** (11.7 MB, largest 310 KB), of which 29 workouts reference **80** via `pictures` entries whose relative URLs resolve here. One file is referenced by no workout — the case the report's "unmatched" section exists for. Verified 2026-07-19: the photos carry **no EXIF segment whatsoever** (JPEG marker walk finds only APP0/JFIF, never APP1), so Endomondo stripped all camera metadata on export; and only **11 of the 80** picture entries carry a `point`.

Out of scope: the export's other folders (`Friends/`, `Profile/`, `Routes/`, `Weights/`, `index.html`) and the loose sibling files in `data/endomondo-strava-exports/` (single-workout exports, a Strava GPX) — none feed the migration.

Strava intake: TCX/GPX/FIT uploads (25 MB cap) or API; API cannot set TCX-borne names/sports (TCX `Sport` is only Running|Biking|Other) and cannot upload photos. The app is a Spring Boot 4.1.0 Thymeleaf web app (built on Spring Framework 7) with the parser as its only service.

Target account (verified 2026-07-19 via the claude.ai Strava connector, athlete id 71292278): the account is **active**, with current runs/walks recorded directly in Strava. Consequences: (a) migrated historical activities (2011–2020) coexist with live data, so any verification or reconciliation listing MUST filter by the historical date range, never "list everything"; (b) the read-only connector is the verification channel for the smoke test and post-run reconciliation (task 7.2/7.3) — it can list and inspect activities but cannot upload, so it does not replace the app's own API client and credentials.

## Goals / Non-Goals

**Goals:**
- One-command (well, one-button) migration of the archive to the user's Strava account: correct routes, dates, titles, sports; no duplicates on re-run; resumable.
- Photo handout report that reduces manual photo attachment to open-link-and-add.
- Every Strava write gated behind explicit user action; dry-run as the default path.

**Non-Goals:** photo upload (API cannot), general Strava client, undo automation, weight/social data.

## Decisions

- **Web-app-hosted OAuth (authorization code)** — the app already serves HTTP, so it hosts `/strava/callback`; the user creates a personal API application on strava.com and supplies client id/secret via environment variables (`STRAVA_CLIENT_ID`/`STRAVA_CLIENT_SECRET`); refresh token persists in `data/strava-tokens.json` (git-ignored with the rest of personal data). Alternatives rejected: out-of-band token paste (worse UX, same trust model); committing a properties file (secrets in git).
- **Tracked → TCX upload; manual → manual activity** — tracked TCX carries the route and any sensor data; manual TCX trackpoints are synthetic (observed: a "walk" ending 4:37 AM next day) and would draw false routes, so INPUT_MANUAL workouts are created from JSON metadata only. IMPORT_GPX (1 workout) goes the TCX route.
- **Idempotency: deterministic `external_id` + local ledger** — `external_id` = workout base filename for uploads; Strava's own duplicate rejection is treated as success (existing activity id recorded). Manual creates have no external_id, so the ledger is their only guard: entries are written as `pending` before the API call and finalized after, so a crash leaves a visible reconciliation point instead of a silent duplicate.
- **Sport mapping is a total, reviewed table** — enumerate every distinct sport value in the archive at apply time and map each to a Strava sport type; unmapped values skip that workout with a reason rather than guessing. The dry-run surfaces the whole table for user review before anything uploads.
- **Spring Framework `RestClient` + `MockRestServiceServer`** — no new HTTP or test dependencies: both classes ship with the Spring Framework 7 / `spring-test` artifacts that Spring Boot 4.1 already manages, and Spring Boot auto-configures a `RestClient.Builder` bean. Spring Framework 7 supports binding `MockRestServiceServer` to `RestClient` builders (older versions only bound comfortably to `RestTemplate`). Direction note: the app *consumes* Strava's REST API as an HTTP client; it exposes no REST endpoints of its own.
- **Migration runs in a background thread with a polling status page** — simplest progress UX in the existing Thymeleaf style (auto-refresh); no SSE/websocket machinery for a one-shot personal tool.
- **Report as a single HTML file in `data/`** — sits beside the archive so relative image paths work offline; git-ignored because photos are personal. Generated in dry-run (links "pending migration") and regenerated after runs with live activity links.
- **Photo location: EXIF → picture `point` → workout's first track point** — photos are the one payload the Strava API refuses, so they are handed to the user as geotagged copies instead. Checking the file's own EXIF first is what makes regeneration idempotent (a copy stamped by an earlier run is read back, not re-derived), even though on this archive that source never fires: the exported JPEGs have no EXIF at all, so in practice 11 photos are stamped from their `point` and the remaining 69 from their workout's first track point. Coordinates are written to **copies** under `data/` — the archive is treated as read-only, since it is the irreplaceable original. Alternative rejected: writing EXIF in place (destroys the only copy of the export if the write is wrong).
- **Apache Commons Imaging for EXIF writing** — the archive photos have no APP1 segment, so the library must *create* an EXIF structure, not edit one. Commons Imaging writes EXIF/GPS on JPEGs without native dependencies. Alternative rejected: metadata-extractor (reads metadata but cannot write it).
- **Description stamp** — every migrated activity's description gets "Migrated from Endomondo (recorded {original date})", making migrated items identifiable and manual cleanup feasible.

## Risks / Trade-offs

- [Strava API surface may have shifted post-cutoff] → consult developers.strava.com at apply time before coding the client; the mocked tests encode our assumptions and the 1–2 activity smoke validates them against reality.
- [Upload processing is async and can fail late] → poll each upload to terminal status; late failures land in the ledger as failed-with-reason and in the run summary.
- [429s / daily cap on a 162-activity run] → throttle ~1 req/s with header-driven backoff; the ledger makes an interrupted run resumable next day.
- [Ledger corruption or manual edits] → ledger is human-readable JSON; reconciliation rule: Strava's duplicate rejection by external_id backstops tracked workouts; manual workouts are few (25) and visually checkable.
- [Real-account writes are irreversible in bulk] → smoke task (1–2 activities) and the full run are explicitly user-gated tasks; dry-run is the default entry point everywhere in the UI.

## Migration Plan

Ships dark: nothing runs until the user opens the migration page. Rollback of the code = revert; rollback of migrated activities = manual deletion aided by the description stamp.

## Open Questions

- None blocking. The exact Strava sport-type enum values for the archive's sports are resolved at apply time with the mapping table review.
