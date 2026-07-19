## Why

Switching the app to Polish produces garbled diacritics (`Strona gÅÃ³wna` instead of `Strona główna`) and leaves most of the page in English anyway. Reproduced and diagnosed: (a) the hand-rolled `ResourceBundleMessageSource` in `GeneralConfiguration` reads the UTF-8 `messages_pl.properties` as ISO-8859-1 — textbook mojibake; (b) user-facing text in the templates (upload form label, submit button) is hardcoded English, so it never translates; (c) `lang.html` is dead scaffolding — included nowhere, offering English/French (no Polish), using message keys that do not exist, targeting an unmapped `international?lang=` URL, and loading jQuery from an external CDN.

## What Changes

- Polish renders with correct diacritics: the message infrastructure reads bundles as UTF-8 (replace the hand-rolled `ResourceBundleMessageSource` with Spring Boot's autoconfigured MessageSource, which defaults to UTF-8).
- All user-facing template text is externalized to the message bundles with English and Polish entries — switching language translates the whole page, not just titles and flash messages.
- Dead `lang.html` template is deleted.
- **Preserved behavior:** the working locale mechanics — `?lang=` links in the header, `LocaleChangeInterceptor`, cookie persistence via `CookieLocaleResolver`, English default — and every existing message key and its English text (pinned by the upload-flow regression tests).

## Non-goals

- Adding languages beyond English and Polish.
- Locale-aware number/date formatting changes.
- The upload result summary content (separate `upload-result-summary` change; its `summary.*` keys automatically benefit from the encoding fix).

## Capabilities

### New Capabilities
- `web-localization`: how the web UI is localized — bundle encoding, coverage of user-facing text, supported locales, and the language switch mechanics.

### Modified Capabilities

(none)

## Impact

- `GeneralConfiguration` — remove the `messageSource()` bean (Spring Boot autoconfiguration takes over via `spring.messages.basename`); locale resolver/interceptor beans stay.
- `application.properties` — `spring.messages.basename=messages,version`.
- `messages.properties` / `messages_pl.properties` — new keys for previously hardcoded template text.
- Templates — hardcoded text replaced with `#{...}` expressions; `lang.html` deleted.
- `UploadFlowTest` — gains a Polish-locale assertion proving correct diacritics end-to-end.
