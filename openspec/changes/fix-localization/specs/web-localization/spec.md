# web-localization

## ADDED Requirements

### Requirement: Message bundles render UTF-8 correctly in every supported locale
The application SHALL read message bundles as UTF-8 so that localized text — including Polish diacritics (ą, ć, ę, ł, ń, ó, ś, ź, ż) — reaches the browser byte-correct.

#### Scenario: Polish page shows correct diacritics
- **WHEN** a page is requested with the Polish locale active
- **THEN** the response contains the exact Polish text from `messages_pl.properties` (e.g. `Strona główna`), with no mojibake

### Requirement: All user-facing text is externalized and translated
Every piece of user-visible text in the templates (titles, labels, buttons, messages) SHALL come from the message bundles, with an entry in both English and Polish. Templates SHALL contain no hardcoded user-visible text.

#### Scenario: Upload form translates completely
- **WHEN** the upload page is requested with the Polish locale active
- **THEN** the form label and submit button render Polish text from the bundle, not English literals

### Requirement: Locale selection works via language links and persists
The application SHALL support English (default) and Polish, switchable via `?lang=` links present on every page, with the choice persisted in a cookie across requests.

#### Scenario: Switch persists across requests
- **WHEN** a request with `?lang=pl` is followed by a request without any `lang` parameter
- **THEN** the second response renders Polish

#### Scenario: Default locale is English
- **WHEN** a request arrives with no `lang` parameter and no locale cookie
- **THEN** the response renders English
