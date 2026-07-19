## 1. View model

- [x] 1.1 Add `WorkoutSummary` view model built from `EndomondoJson`: name, sport, source, start time, formatted duration (`H:mm:ss`), distance, calories, average/maximum speed, altitude min/max, point count, points-with-location count, picture count if the model has pictures at this time; numeric formatting fixed to `Locale.ROOT`, absent source values map to a marker the template can test (verify: unit tests over both fixtures' parsed models)

## 2. Controller and template

- [x] 2.1 `UploadController.process()` builds `WorkoutSummary` on success and adds it to the model; failure paths add nothing (verify: `mvnw test` green, existing message assertions untouched)
- [x] 2.2 Summary section in `upload.html` rendered only when the summary attribute is present; every label and the absent-value marker via new `summary.*` keys added to `messages.properties` and `messages_pl.properties` (verify: no hardcoded label text in the new template block)

## 3. Regression coverage

- [x] 3.1 Extend `UploadFlowTest`: tracked fixture shows sport/source/duration/distance/point counts; manual fixture shows absent-value markers for missing metrics; non-JSON upload renders no summary block (verify: `mvnw test` green)
- [x] 3.2 Manual check in the running app with a real export file from `data/`; then `openspec validate upload-result-summary` and commit (verify: summary renders in the browser, validation passes)
