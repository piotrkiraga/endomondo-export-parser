## Context

The home page (`HomeController`, `home.html`) currently shows only a Strava connect/connected badge and, once connected, a small athlete greeting sourced from `StravaDictionaryService.current()`. The fuller Strava data view — athlete card, full gear table, manual refresh button — lives on its own page, `StravaDictionaryController`/`migration/strava-dictionary.html` at `/migration/strava-dictionary`. Everything else that describes "where things stand" already exists but lives behind other separate pages: `ArchiveScanner` (archive pairing), `MigrationLedger` (per-workout migration status, `data/generated/strava-migration-ledger.json`), and `LocationCache` (geocoding results, `data/generated/location-cache.json`) — all following the same lock-and-reload-per-call pattern (`StravaTokenStore`'s pattern, documented in `migrate-to-strava`'s design.md) rather than in-memory caching, since a resumed process must see what an earlier run wrote. This change consolidates the dictionary page's content into home and adds the remaining local-data stats alongside it.

## Goals / Non-Goals

**Goals:**
- One glance at the home page answers: how big is the archive, how much of it is migrated (and how), how many locations has geocoding resolved, and shows the Strava athlete profile and gear dictionary (with its own refresh control) — one page instead of three.
- Zero new Strava API calls beyond the dictionary refresh action that already exists today.
- Every stat degrades to an absent/placeholder state rather than throwing, since a fresh install has no archive configured, an empty ledger, and an unrefreshed dictionary.

**Non-Goals:**
- No historical/trend data — current-state snapshot only, matching what the underlying stores already hold (none of them keep history).
- No new persisted data — everything read here is already written by existing features for their own purposes.
- No change to the review page or photo report's own content.
- No change to `StravaDictionaryService.refresh()`'s own behavior (still one manual `GET /athlete` call) — only where the button that triggers it lives.

## Decisions

- **A dedicated `AppStatusResolver` (service), not inline logic in `HomeController`** — assembling one summary from independent sources (`ArchiveScanner`, `MigrationLedger`, `LocationCache`) is exactly the kind of multi-source assembly this codebase already extracts into a resolver rather than fattening a controller (see `WorkoutResolver`, `ConfirmedGearResolver`). It also keeps `HomeController` testable the way the project's other controllers are — by calling the controller directly against a small rig, no `MockMvc`/Spring context — since `AppStatusResolver` itself can be unit-tested against fixture directories and temp-dir-backed stores without touching `HomeController` at all. Alternative rejected: computing everything inline in `HomeController.view()` — would require injecting all three collaborators directly into the controller and duplicating assembly logic that has nothing to do with routing.
- **`AppStatus` scope is archive/ledger/cache counts only — it does NOT include the Strava dictionary** — once the full athlete/gear table is on the page (see below), a separate "gear count" number in `AppStatus` would be redundant with the table sitting right beneath it. The dictionary data flows through `HomeController` the same direct way `StravaDictionaryController` already gets it today (`StravaDictionaryService.current()`, `StravaTokenStore.load()`), not through `AppStatusResolver`.
- **`AppStatus` is a plain `model/` record**, mirroring `ResolvedWorkout`/`WorkoutReport` — a few nullable/`Optional`-backed fields (archive count, ledger counts, cache size), one per stat, each independently absent-able so the template can render "not yet available" per stat rather than all-or-nothing.
- **Archive count reuses `ArchiveScanner.scan(archiveRoot).workouts().size()`, guarded by the same `Files.isDirectory(archiveRoot)` check `MigrationController` already uses** — no new scanning logic, and `ArchiveScanner` only pairs filenames (no JSON parsing), so this is a cheap directory listing, not a repeat of the full parse/plan pipeline. Recomputed on every home-page load rather than cached: for a personal archive (162 workouts in the real one), a directory listing is sub-50ms, and a "status" view whose whole purpose is being current is the wrong place to introduce staleness for a negligible cost saving.
- **`LocationCache` gains a `size()` method, following its existing lock-and-reload pattern** — reads the current on-disk map and returns its size, rather than adding an in-memory counter, since `LocationCache` has no in-memory state today and adding one just for a count would introduce a new class of staleness bug (a second process's writes wouldn't be reflected) for no benefit.
- **Ledger counts come from `MigrationLedger.entries()` grouped by `LedgerStatus`** — no new `MigrationLedger` method needed; `entries()` already returns every `LedgerEntry`, and `LedgerStatus` already has the four states (`PENDING`/`DONE`/`FAILED`/`SKIPPED`) the dashboard wants to show as separate counts.
- **The dictionary page's markup, message keys, and refresh flow move onto `home.html` unchanged, rather than being redesigned** — `StravaDictionaryController.view()`/`refresh()`'s logic (load the snapshot, add `stravaConnected`/`snapshot`/`refreshedAtDisplay` to the model) becomes `HomeController`'s, and the existing `migration.stravaDictionary.*` message keys are reused verbatim. Minimizes the change to "move it," not "redesign it," and keeps the English/Polish translations already in place valid with zero edits.
- **New route: `POST /home/refresh`**, mirroring the dictionary page's existing `/migration/strava-dictionary/refresh` shape (a POST sibling of the GET page's own path) rather than something under `/`, since `/` has no natural sibling path for a POST action.
- **`HomeController` gains a `StravaTokenStore` dependency** alongside its existing `StravaDictionaryService`, for the `stravaConnected` flag the merged-in `strava-status` fragment needs — the same dependency `StravaDictionaryController` already has today, just moved.
- **The existing small athlete greeting (name + picture, the `athlete` model attribute) is removed, not kept alongside the fuller card** — showing a person's name and picture twice on one page (once as a one-line greeting, once inside the full athlete card a few lines below) is redundant, not additive; the fuller card is a strict superset of what the greeting showed.

## Risks / Trade-offs

- [Archive scan on every home-page load] → verified cheap (filename pairing only, no content parsing) at the real archive's scale (162 workouts); revisit only if a future archive is orders of magnitude larger.
- [Multiple independent local reads on one request] → each already degrades independently (missing file → empty/absent, not an exception) per their existing implementations; `AppStatusResolver` doesn't need its own error handling beyond calling them.
- [Stat definitions could drift from the pages that show the detailed version, e.g. the ledger breakdown vs. the review page's summary] → both read the same `MigrationLedger.entries()`, so they can't diverge on the underlying data, only on how it's grouped/labeled for display.
- [The app's landing page becomes more Strava-flavored for every visitor, including someone who hasn't connected yet] → already gated the same way the dictionary page gates it today: the connect/connected badge always shows, the athlete card and gear table only render `th:if="${snapshot != null}"` — a not-yet-connected user sees the same "not connected" state they'd see on the old dictionary page, just on home instead.

## Migration Plan

Additive plus one deletion: new model/service classes, one new `LocationCache` method, `HomeController`/`home.html` changes, and removal of `StravaDictionaryController`, `templates/migration/strava-dictionary.html`, and the navbar's "Strava data" entry. No persisted data migration, no schema change to any stored file — `data/generated/strava-dictionary.json` is read exactly as before, just from a different controller. Rollback is a code revert; no data is lost either direction since nothing this change touches is destructive to `data/`.

## Open Questions

- None blocking. Whether to render counts as plain numbers or add a progress bar for the ledger breakdown is a template-level detail left to implementation.
