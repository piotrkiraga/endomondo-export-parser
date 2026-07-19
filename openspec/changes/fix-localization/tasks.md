## 1. Fix the encoding

- [x] 1.1 Remove the `messageSource()` bean from `GeneralConfiguration`; add `spring.messages.basename=messages,version` to `application.properties`; confirm Spring Boot's autoconfigured MessageSource serves both basenames (verify: app starts, `curl "http://localhost:8080/home?lang=pl"` returns `Strona główna` byte-correct)

## 2. Complete the translations

- [x] 2.1 Sweep all templates (`home.html`, `upload.html`, `error.html`, `fragments/*`) for hardcoded user-visible text; externalize each to the bundles with English and Polish entries (verify: templates contain no user-visible literals; both bundles define every key used)
- [x] 2.2 Delete the dead `lang.html` template (verify: grep confirms no template references it; app starts and all pages render)

## 3. Regression coverage

- [x] 3.1 Add locale scenarios to `UploadFlowTest` (or a dedicated `LocalizationTest`): Polish request renders correct diacritics end-to-end; locale persists via cookie without `lang` parameter; default is English (verify: `mvnw test` green)
- [x] 3.2 Manual check in the browser: switch to Polish, view home and upload pages, switch back; then `openspec validate fix-localization` and commit (verify: correct diacritics on screen, validation passes)
