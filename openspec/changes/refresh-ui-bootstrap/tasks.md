## 1. Bootstrap 5 WebJar

- [ ] 1.1 Check the latest Bootstrap 5.3.x on Maven Central; add `org.webjars:bootstrap` at that version to `pom.xml`; verify the CSS is served (verify: `curl -o /dev/null -w "%{http_code}" http://localhost:8080/webjars/bootstrap/<version>/css/bootstrap.min.css` returns 200 anonymously — if 401/403, add `/webjars/**` to the permit-all matcher first)

## 2. Shared layout

- [ ] 2.1 Create `fragments/head.html` (title parameter, WebJar stylesheet link, color-mode snippet setting `data-bs-theme` from `prefers-color-scheme`); replace the duplicated `<head>` in all five page/fragment templates (verify: `grep -r stackpath src/main/resources/templates` returns nothing; pages render styled)
- [ ] 2.2 Rebuild `fragments/header.html` as a Bootstrap 5 navbar — brand, Home/Upload links, right-aligned language switcher, same message keys and `?lang=` links (verify: navbar renders on home, upload, and error pages in both languages)

## 3. Bootstrap 5 markup migration

- [ ] 3.1 Migrate `upload.html` (form in a card: `form-label`, `form-control`, spacing utilities; workout summary card intact), `home.html`, `error.html`, and `fragments/footer.html`/`messages-errors.html` to Bootstrap 5 idioms (verify: no Bootstrap-4-only classes — `form-group`, `form-control-file` — remain in templates)

## 4. Verification

- [ ] 4.1 Full suite passes unmodified (verify: `mvnw test` green, `git diff` shows no test files touched)
- [ ] 4.2 Rebuild jar, walk all pages in the browser in English and Polish, light and dark; upload a real file from `data/` and check the summary card; then `openspec validate refresh-ui-bootstrap` and commit (verify: all pages styled, no CDN requests in browser dev tools network tab, validation passes)
