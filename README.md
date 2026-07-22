# Endomondo Export Parser

A Spring Boot web application that reads the workout files from an Endomondo GDPR data
export and migrates that archive into Strava, one reviewed workout at a time.

Endomondo shut down in 2020 and handed users a ZIP file. This project turns that frozen
archive back into something usable: it parses the export's idiosyncratic JSON, then walks
you through every workout in a browser — exactly what it would send to Strava, before it
sends it — so migrating means clicking through your history, not trusting a bulk import.

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
  HTML report — openable straight from disk, no server required, and it follows your
  OS/browser dark-mode preference even then. Photos not referenced by any workout show up
  too, as real thumbnails rather than a bare path list. Once a workout is migrated, its
  entry links straight to the activity on Strava.
- **Previews the migration before anything uploads.** A generated report shows, per
  workout, exactly what would be sent to Strava — resolved name, sport, description,
  dates, distance, duration, and whether it uploads as a track or gets created manually —
  built entirely offline from the same planning logic the real migration will use, styled
  and dark-mode-aware the same way the photo report is, with the same live Strava link
  once migrated.
- **Names the unnamed.** A workout with no title becomes something like "Evening Ride
  along Vistula in Kraków": time of day, sport, and a reverse-geocoded nearby landmark,
  resolved via OpenStreetMap and cached so the same real-world place is never looked up
  twice.
- **Hosts its own Strava connection.** An OAuth authorization-code flow lets it call
  Strava's API on your behalf, scoped to `activity:write` only — it never reads your
  existing activities.
- **Migrates one workout at a time, by hand.** A review screen shows each workout's real
  name, sport, description, and photo thumbnails (geotagged copies, each with a
  click-to-copy local path, ready to attach manually — the one thing the API can't do),
  with Previous/Next to browse freely regardless of what's decided yet, and Migrate, Skip,
  or Stop to act. Nothing uploads unattended; a Skip is remembered even if you close the
  app, and re-clicking Migrate on an already-migrated workout never re-sends the track
  (Strava would just reject it as a duplicate) — it refreshes the activity's name, sport,
  description, and gear from the archive instead, so it also doubles as "push my latest
  data" for a workout you've since corrected something about.

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

Day-to-day task status, backlog items, and ideas are tracked using
[GitHub Issues](https://github.com/piotrkiraga/endomondo-export-parser/issues) and a linked
[GitHub Project board](https://github.com/users/piotrkiraga/projects/1); OpenSpec remains the
source of truth for requirements and design decisions.

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

191 tests covering the parser against anonymized fixtures, the upload flow, localization
including Polish diacritics, the migration planner, EXIF geotagging, the Strava API client
and OAuth flow (via `MockRestServiceServer` — no test touches the real network), and the
OpenStreetMap reverse-geocoding clients. Tests that need a real Endomondo archive skip
themselves when one is not present, so a fresh clone runs green.

## A note on the data

Personal export data never enters this repository. The `data/` directory is git-ignored,
and the fixtures under `src/test/resources/fixtures/` are anonymized: real coordinates and
photo references are replaced with synthetic ones.

Strava credentials (`STRAVA_CLIENT_ID`/`STRAVA_CLIENT_SECRET`, from your own Strava API
application — create one at [Strava's API Settings page](https://www.strava.com/settings/api))
can be supplied either way Spring reads them, so pick whichever fits your workflow:

- **Environment variables** — no extra file to keep track of; the natural fit for CI or
  containers, and works just as well for everyday local use if you'd rather not maintain a
  properties file.
- **A git-ignored `application-local.properties` file**, copied once from the checked-in
  `application-local.properties.example` template at the project root and filled in — no
  environment variables to re-export in every new terminal session, and nothing ever
  committed.

Spring merges both into the same `Environment`, so nothing else needs to know which one you
used. The same properties file also holds an optional old-bike gear correction (Strava
otherwise assigns whatever gear is currently your default to every migrated Ride, which is
wrong for one recorded before you owned it) — blank and inert unless you set it.

Login is disabled entirely — `WebSecurityConfiguration` permits every request. The app is
built for single-user, localhost-only use; the original in-memory placeholder accounts were
never a real authentication scheme, so removing them removed nothing meaningful. Revisit
this if the app is ever meant to bind beyond `localhost`.

Location enrichment sends workout and photo coordinates to OpenStreetMap's public
Nominatim and Overpass services to resolve place names — the only outbound network calls
this app makes with archive data. Results are cached locally (`data/generated/location-cache.json`,
also git-ignored) so the same real-world coordinate is never looked up twice.

## Status

The parser, web interface, and the entire Strava migration engine are done and tested:
archive scanning, planning, sport mapping, photo geotagging with its browsable report, the
offline workout preview report, location-based naming, the Strava API client with its OAuth
connect flow, the migration ledger that makes a run resumable and duplicate-safe, the
executor that drives uploads/creates through it, and the interactive review page that ties
it all together in the browser. Over a real 162-workout archive the planner produces 137
track uploads and 25 manual activities with nothing skipped.

What's left is entirely the real thing: the executor has not yet touched a real Strava
account, deliberately. Every write is gated behind an explicit user action — a dry run is
the default path everywhere, and even the review page never migrates a workout without a
click — so the two remaining steps are a small supervised smoke run, then the full archive,
both requiring the user's own go-ahead.
