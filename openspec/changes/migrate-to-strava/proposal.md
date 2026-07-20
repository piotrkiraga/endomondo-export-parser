## Why

The parser now reads the frozen Endomondo archive end-to-end, but the archive still lives only on disk. The goal that motivates this whole project is seeing 162 historical workouts (2011–2020) in the Strava ecosystem with correct routes, dates, titles, and sports. Strava accepts TCX uploads but cannot carry Endomondo's names/sports in the file format, and photos cannot be uploaded via API at all — so the migration needs exactly what this app uniquely has: both halves of each workout (TCX + JSON) plus the photo-to-workout mapping present in 29 workout JSONs.

## What Changes

- Parse the `pictures` entries of workout JSONs (created_date, relative URL into `resources/gfx/`, GPS point) — same merge-then-bind normalization.
- New migration service driving the Strava v3 API: OAuth (authorization-code flow hosted by this web app), TCX upload with `external_id` idempotency for tracked workouts, manual-activity creation for INPUT_MANUAL workouts (their TCX trackpoints are synthetic), then name/sport/description update from the paired JSON via an Endomondo→Strava sport mapping table.
- Migration ledger persisted under `data/` (git-ignored, like all personal data) recording workout → Strava activity id, enabling resume and re-run without duplicates.
- **Photo handout report**: a generated HTML file listing, per migrated activity, its photos (embedded from the local archive) next to a direct link to the created Strava activity — so photos can be added manually in the Strava apps with zero searching. Photos are the one thing the API cannot transfer.
- **Location enrichment** (added mid-flight, 2026-07-20): workout names/descriptions and photo captions are enriched with real-world places — reverse-geocoded via OpenStreetMap (Nominatim for locality, Overpass for a nearby notable feature) and cached locally so repeat coordinates cost nothing. An unnamed workout becomes e.g. "Evening Ride along Vistula in Kraków" instead of just "Evening Ride".
- Dry-run mode: full plan (what would upload, as what sport, with which photos flagged) with zero API calls.
- Web UI: one migration page (connect, dry-run preview, execute with progress, link to report) in the existing Thymeleaf style.

## Capabilities

### New Capabilities
- `strava-migration`: upload routing (tracked vs. manual), sport mapping, metadata update, idempotency/resume, rate-limit respect, dry-run.
- `migration-photo-report`: the photo handout report contract — every archived photo appears exactly once, mapped to its activity link or explicitly listed as unmatched.

### Modified Capabilities
- `workout-json-parsing`: ADDED requirement — `pictures` entries are parsed into the workout model.

## Non-goals

- No photo upload to Strava (API does not support it — that is the reason the report exists).
- No transfer of weight history, friends, or comments.
- No general-purpose Strava client — only what migration needs.
- No automatic deletion/cleanup of Strava activities (undo is manual, by design — writes to a personal account deserve human control).

## Preserved vs. changed behavior

- **Preserved**: all existing parsing behavior and specs; upload page untouched.
- **Changed**: parser additionally populates pictures (additive); app gains the migration page and outbound Strava API calls — which run only when explicitly triggered by the user, never on boot.

## Impact

- Parser/DTOs: `pictures` support; regression tests extended.
- New: migration service, sport mapping, Strava API client (Spring `RestClient`), OAuth callback endpoint, ledger, report generator, migration page.
- Config: Strava client id/secret via environment/local properties (never committed); tokens stored under `data/`.
- Verification: unit tests with a mocked Strava API; dry-run over the real archive; a small real-account smoke (1–2 activities) before the full run, which stays a user decision.
