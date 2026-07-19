## Context

Diagnosed live (2026-07-19, running app):

- `curl "http://localhost:8080/home?lang=pl"` → `<title>Strona gÅÃ³wna</title>` — the UTF-8 bytes of `główna` decoded as ISO-8859-1. `messages_pl.properties` is UTF-8 on disk; Spring Framework's `ResourceBundleMessageSource` defaults to ISO-8859-1 when no `defaultEncoding` is set, which is exactly how `GeneralConfiguration.messageSource()` constructs it (2020-era code).
- Locale mechanics verified working: `?lang=pl` sets the `CookieLocaleResolver` cookie, the cookie persists across requests, `?lang=en` switches back, no cookie defaults to English. These must not regress.
- Untranslated text found in templates (e.g. `upload.html` form label and Submit button). A full sweep of all templates is part of the work.
- `lang.html` is included by no template (grep-verified); it references keys absent from every bundle, offers `en`/`fr` options, redirects to `international?lang=` (no such mapping), and loads jQuery from a Google CDN.

## Goals / Non-Goals

**Goals:** correct Polish rendering end-to-end; complete translation coverage of user-facing text; less hand-rolled configuration (Spring Boot autoconfiguration where it suffices).

**Non-Goals:** new languages; changing the switch UX (header links stay); touching `version.properties` filtering.

## Decisions

- **Delete the `messageSource()` bean; configure `spring.messages.basename=messages,version` instead of setting `defaultEncoding` on the hand-rolled bean** — Spring Boot's `MessageSourceAutoConfiguration` backs off only when no `messageSource` bean exists; once ours is gone, Boot provides a UTF-8-default source. One less bean to maintain, and consistent with the platform-upgrade pattern of preferring Spring Boot autoconfiguration (same reasoning that removed `@EnableWebMvc` and `ThymeleafConfiguration`). Alternative rejected: `messageSource.setDefaultEncoding("UTF-8")` — works, but keeps custom code alive for something the platform does by default.
- **Keep `.properties` files in UTF-8** (no `native2ascii`/`\uXXXX` escaping) — files stay human-editable; UTF-8 is the Spring Boot default expectation.
- **Externalize template text with the existing key naming style** (`title.*`, `language.*`, form labels under e.g. `form.upload.*`) — sweep every template; the definition of done is "no user-visible literal text in templates", verified by review rather than tooling.
- **Delete `lang.html` rather than repair it** — the working switcher already exists in `header.html`; repairing a never-included fragment with a French option and CDN jQuery would be resurrecting debris. Its deletion is behavior-invisible.
- **End-to-end Polish assertion in `UploadFlowTest`** — a MockMvc request with `lang=pl` asserting a string containing Polish diacritics (e.g. `Prześlij plik`) pins the encoding fix at the HTTP boundary, where the mojibake was observed.

## Risks / Trade-offs

- [Spring Boot's MessageSource behaves subtly differently from `ResourceBundleMessageSource` (e.g. `fallbackToSystemLocale`)] → the English-default scenario is asserted explicitly; behavior is verified against the running app, not assumed.
- [Missed hardcoded strings in the template sweep] → the spec requires coverage of all five user-facing templates; review each template file in the diff.

## Migration Plan

Config + resource change; rollback = revert. No data, no external calls.

## Open Questions

- None.
