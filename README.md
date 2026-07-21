# Endomondo Export Parser

A Spring Boot web application that reads the workout files from an Endomondo GDPR data
export and is growing into a one-button migration of that archive into Strava.

Endomondo shut down in 2020 and handed users a ZIP file. This project turns that frozen
archive back into something usable: it parses the export's idiosyncratic JSON, and — the
work currently in progress — uploads the workouts to Strava with their routes, titles,
dates and sports intact.

## What it does today

- **Parses the Endomondo workout export format.** The export is not ordinary JSON: it is
  an array of single-key objects, with track points nested two levels deeper in the same
  style. The parser normalizes that shape before binding it.
- **Shows a workout summary** after upload — name, sport, start time, duration, distance,
  calories, speed and altitude ranges, and how many GPS points carry a full location.
- **Speaks English and Polish**, switchable at runtime.
- **Follows your system light/dark preference**, with a moon/sun toggle that overrides it
  and remembers the choice per browser.
- **Plans a full Strava migration offline.** Point it at an archive directory and it pairs
  every workout's JSON with its TCX and decides what each one should become — without
  credentials and without touching the network.
- **Hands photos out geotagged and browsable.** Every photo referenced by a workout is
  grouped, stamped with GPS coordinates in its EXIF (from the photo's own point or the
  workout's route, whichever is available), and written into a single click-to-enlarge
  HTML report — openable straight from disk, no server required.
- **Previews the migration before anything uploads.** A generated report shows, per
  workout, exactly what would be sent to Strava — resolved name, sport, description,
  dates, distance, duration, and whether it uploads as a track or gets created manually —
  built entirely offline from the same planning logic the real migration will use.
- **Names the unnamed.** A workout with no title becomes something like "Evening Ride
  along Vistula in Kraków": time of day, sport, and a reverse-geocoded nearby landmark,
  resolved via OpenStreetMap and cached so the same real-world place is never looked up
  twice.
- **Hosts its own Strava connection.** An OAuth authorization-code flow lets it call
  Strava's API on your behalf; the migration run itself — uploading tracks, creating
  activities — is still in progress.

## Why the `openspec/` directory is the interesting part

This is a brownfield revival of a 2020-era codebase, done strictly spec-first. Every
change begins as a proposal with its requirements and design trade-offs written down,
gets implemented against those requirements, and is then archived with its specification
folded into the permanent capability specs.

- [`openspec/specs/`](openspec/specs/) — the current contract: 21 requirements across six
  capabilities, each with concrete scenarios.
- [`openspec/changes/archive/`](openspec/changes/archive/) — eight completed changes, each
  keeping its original proposal, design document, task list, and specification delta.
- [`openspec/changes/migrate-to-strava/`](openspec/changes/migrate-to-strava/) — the change
  in flight.

Day-to-day task status for the change in flight is also tracked on a Jira board (project
`EEP`); OpenSpec remains the source of truth for requirements and design decisions.

If you want to see how decisions were reasoned about rather than just their outcome, the
design documents are the place to look. A representative example is the Strava migration's
[`design.md`](openspec/changes/migrate-to-strava/design.md), which records why tracked and
manually-entered workouts must take different paths, and why photos get geotagged copies
instead of being uploaded.

## Built with

Java 17 · Spring Boot 4.1 (on Spring Framework 7) · Thymeleaf · Spring Security ·
Jackson 3 · Bootstrap 5.3 served from the jar as a WebJar · Apache Commons Imaging ·
OpenStreetMap (Nominatim/Overpass) for reverse geocoding · JUnit 5 with MockMvc and
`MockRestServiceServer` · Maven

## Running it

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8080>. No login is required — see "A note on the data" below.

To build and run the executable jar instead:

```bash
./mvnw package
java -jar target/endomondo-export-parser-0.0.1-SNAPSHOT.jar
```

Java 17 or newer is required.

## Tests

```bash
./mvnw test
```

139 tests covering the parser against anonymized fixtures, the upload flow, localization
including Polish diacritics, the migration planner, EXIF geotagging, the Strava API client
and OAuth flow (via `MockRestServiceServer` — no test touches the real network), and the
OpenStreetMap reverse-geocoding clients. Tests that need a real Endomondo archive skip
themselves when one is not present, so a fresh clone runs green.

## A note on the data

Personal export data never enters this repository. The `data/` directory is git-ignored,
and the fixtures under `src/test/resources/fixtures/` are anonymized: real coordinates and
photo references are replaced with synthetic ones. Strava credentials are supplied through
the `STRAVA_CLIENT_ID` and `STRAVA_CLIENT_SECRET` environment variables and are never
committed.

Login is disabled entirely — `WebSecurityConfiguration` permits every request. The app is
built for single-user, localhost-only use; the original in-memory placeholder accounts were
never a real authentication scheme, so removing them removed nothing meaningful. Revisit
this if the app is ever meant to bind beyond `localhost`.

Location enrichment sends workout and photo coordinates to OpenStreetMap's public
Nominatim and Overpass services to resolve place names — the only outbound network calls
this app makes with archive data. Results are cached locally (`data/location-cache.json`,
also git-ignored) so the same real-world coordinate is never looked up twice.

## Status

The parser, web interface, and most of the Strava migration are done and tested: archive
scanning, planning, sport mapping, photo geotagging with its browsable report, the offline
workout preview report, location-based naming, and the Strava API client with its OAuth
connect flow. Over a real 162-workout archive the planner produces 137 track uploads and
25 manual activities with nothing skipped.

Still to come: the migration ledger and executor that actually drive uploads, and the
migration page tying it together. Every write to a real Strava account is gated behind an
explicit user action, and a dry run is the default path everywhere — the API client itself
has not yet touched a real Strava account, deliberately: that first contact is a gated step.
