## Why

Right now the home page only shows a small Strava connect/athlete greeting, while the fuller Strava data — athlete profile, full gear list, refresh control — lives on its own separate page (`/migration/strava-dictionary`), and nothing shows migration/archive/cache progress anywhere. Answering "where do things stand?" today means opening the migration review page, the photo report, and the Strava dictionary page separately and mentally combining them. Consolidating everything onto the home page — the app's one landing screen — removes that friction, using data the app already collects: no new Strava API calls beyond the refresh action that already exists today.

## What Changes

- The Strava Dictionary page's content (athlete profile card, full gear table, "Refresh from Strava" button, never-refreshed state) moves onto the home page; `/migration/strava-dictionary` is retired as a separate page. `HomeController` gains a `POST /home/refresh` handler mirroring the dictionary page's existing refresh action, and a `StravaTokenStore` dependency for the connected/not-connected status the dictionary page already shows.
- The home page's existing small athlete greeting (name + picture) is removed — the fuller athlete card arriving from the merge replaces it, so the two don't show duplicate information.
- The home page gains an app-state summary section, computed entirely from local data (the archive scan, the migration ledger, the location cache): total archive workouts; the migration ledger breakdown (done / failed / skipped / pending counts); the location cache's size (distinct coordinates cached).
- `LocationCache` gains a `size()` accessor (it currently only exposes `get`/`put`) so the dashboard can report the cache's size without adding a new persistence format.
- Each stat degrades gracefully rather than erroring when its source is unavailable (no archive configured, ledger never written, dictionary never refreshed) — the home page must keep rendering even on a completely fresh install.
- The navbar's "Strava data" link is removed (its destination is now the home page, already reachable via the existing "Home" nav item).

## Capabilities

### New Capabilities
- `app-status-dashboard`: the home page's at-a-glance summary of archive size, migration progress, local cache state, and the Strava athlete/gear dictionary (merged in from the retired dictionary page).

### Modified Capabilities
(none — `web-ui-presentation` governs layout/styling only, not page content, so this doesn't change its requirements)

## Non-goals

- No change to the Strava dictionary refresh behavior itself (`StravaDictionaryService.refresh()`, its one `GET /athlete` call, its manual-only trigger) — only its page location and route path change.
- No historical/trend view (e.g., "migrations over time") — this is a current-state snapshot only.
- No changes to the migration review page or photo report themselves — this only adds a summary to the home page that references the same underlying data they already read.

## Characterization vs. Change

- Preserved: `StravaDictionaryService`, `StravaDictionaryCache`, `MigrationLedger`, `LocationCache`, and `ArchiveScanner`'s existing read/write behavior is unchanged — this change only relocates where their data is displayed and adds a way to *count* what's already stored.
- Preserved: the Strava connect/connected status badge behavior is unchanged, just now rendered on home instead of two separate pages.
- Changed: `HomeController` gains new model attributes and a `POST /home/refresh` handler; `home.html` absorbs the dictionary page's athlete/gear/refresh markup and gains the new dashboard section, replacing the small athlete greeting; `LocationCache` gains one new read-only accessor method; `StravaDictionaryController` and `templates/migration/strava-dictionary.html` are deleted; the navbar's "Strava data" entry is removed.

## Impact

- `HomeController` (reads `endomondo.archive.root`, same property `MigrationController` already uses, to compute the archive total; gains `StravaTokenStore` and a refresh handler)
- `home.html` template (absorbs the dictionary page's content, gains the new dashboard section)
- `LocationCache` (new `size()` method)
- `StravaDictionaryController` and `templates/migration/strava-dictionary.html` — deleted
- `fragments/header.html` — "Strava data" nav entry removed
- Existing `migration.stravaDictionary.*` message keys are reused as-is (same English/Polish text, now rendered from `home.html`); `title.stravaDictionary` is dropped along with the nav entry
- No changes to `MigrationLedger`, `StravaDictionaryCache`, `ArchiveScanner`, `StravaDictionaryService`, or any Strava-facing code
