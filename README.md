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

If you want to see how decisions were reasoned about rather than just their outcome, the
design documents are the place to look. A representative example is the Strava migration's
[`design.md`](openspec/changes/migrate-to-strava/design.md), which records why tracked and
manually-entered workouts must take different paths, and why photos get geotagged copies
instead of being uploaded.

## Built with

Java 17 · Spring Boot 4.1 (on Spring Framework 7) · Thymeleaf · Spring Security ·
Jackson 3 · Bootstrap 5.3 served from the jar as a WebJar · Apache Commons Imaging ·
JUnit 5 with MockMvc · Maven

## Running it

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8080>. The home and upload pages need no login.

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

46 tests covering the parser against anonymized fixtures, the upload flow, localization
including Polish diacritics, the migration planner, and EXIF geotagging. Tests that need a
real Endomondo archive skip themselves when one is not present, so a fresh clone runs green.

## A note on the data

Personal export data never enters this repository. The `data/` directory is git-ignored,
and the fixtures under `src/test/resources/fixtures/` are anonymized: real coordinates and
photo references are replaced with synthetic ones. Strava credentials are supplied through
the `STRAVA_CLIENT_ID` and `STRAVA_CLIENT_SECRET` environment variables and are never
committed.

The in-memory users in `WebSecurityConfiguration` are development scaffolding from the
original 2020 codebase, not a real authentication scheme.

## Status

The parser, web interface, and the credential-free half of the Strava migration — archive
scanning, planning, sport mapping, and photo geotagging — are done and tested. Over a real
162-workout archive the planner produces 137 track uploads and 25 manual activities with
nothing skipped.

Still to come: the Strava API client and OAuth flow, the migration ledger and executor, the
photo handout report, and the migration page. Every write to a real Strava account is gated
behind an explicit user action, and a dry run is the default path everywhere.
