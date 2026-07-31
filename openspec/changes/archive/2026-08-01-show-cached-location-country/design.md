## Context

`NominatimClient.reverseGeocode` already receives a `country_code` field in every Nominatim `jsonv2` response's `address` object (ISO 3166-1 alpha-2, lowercase) — it's just never read today. `LocationCache` is not locale-aware: it stores one `PlaceDescription` per coordinate, shared across however many UI languages the app supports (currently English/Polish via `?lang=`, see `home.html`/`BaseController`).

## Goals / Non-Goals

**Goals:**
- Show each location cache entry as "suburb, locality, country" on the listing page, with the country name matching the active UI language.
- Keep the same cached entry correct in either language, without re-resolving or storing a language-specific copy per locale.

**Non-Goals:**
- Backfilling the ~56 pre-existing cache entries with a country code — see proposal.md.
- Changing `phrase()` or any of its call sites (workout naming/description, photo captions) — this is scoped to the listing page only.
- A maintained country-code → name translation table.

## Decisions

- **Store the ISO alpha-2 country code, not a translated name.** Nominatim already returns it for free (`address.country_code`) — no extra API call. Storing the code instead of a name is what makes the "same entry, different language" scenario work: translation happens at render time, not lookup time, so switching `?lang=` never requires re-resolving anything.
- **Resolve the code to a display name via the JDK's own locale data, not a hand-maintained table**: `new Locale("", countryCode).getDisplayCountry(displayLocale)` (`java.util.Locale`, built into the JDK's CLDR-backed locale service — no new dependency, no properties file to maintain for however many countries eventually show up in someone's archive). This lives as a new `PlaceDescription.summary(Locale)` method, parallel to the existing `phrase()` (which takes no arguments since it never needed locale-awareness). `Locale` here is the JDK class, not a Spring dependency — keeping `PlaceDescription` a plain, dependency-free `model/` record, consistent with the rest of that package.
- **`LocationCacheController` passes `LocaleContextHolder.getLocale()`** (the same source `BaseController.message()` already reads from) into `summary(Locale)` when building the model — the same pattern already used implicitly by every `#{...}` Thymeleaf message on every other page, just made explicit here since `PlaceDescription` isn't itself locale-aware.
- **`countryCode` is nullable on `PlaceDescription`** — Jackson deserializes a missing JSON field as `null` for a record the same as any other type, so the ~56 existing cache entries load without error; `summary()` just omits the trailing country segment when `countryCode` is null or the code doesn't resolve to a display name (defensive: `getDisplayCountry` returns the code itself, unchanged, when it doesn't recognize it — treated the same as "no country").
- **`phrase()` stays untouched.** It's tuned for workout names/descriptions/captions ("along Vistula in Dębniki, Kraków") where a country suffix would be noise — Poland-based workouts don't need "Poland" appended to every activity name. Only the listing page, which exists specifically to let someone eyeball *where in the world* a cluster of coordinates is, needs the country.

## Risks / Trade-offs

- `Locale`'s `getDisplayCountry` country-name coverage/quality depends on the JDK's bundled locale data (COMPAT provider by default on most JDK 17 builds) — well-covered for real countries, so this is a non-issue for genuine ISO codes; no fallback needed beyond the "unrecognized code → omit" handling above.
- Every existing `new PlaceDescription(...)` call site (main + ~8 test files) needs a 4th argument. Mechanical; most test call sites can pass `null` for `countryCode` since they aren't testing country behavior.
