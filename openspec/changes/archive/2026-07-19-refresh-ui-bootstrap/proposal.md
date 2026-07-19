## Why

The web UI is styled with Bootstrap 4.4.1 (released December 2019) loaded from a CDN link in every template, with pre-Bootstrap-5 markup (`form-group`, `form-control-file`), a plain link list for navigation, and duplicated `<head>` boilerplate across all pages. It works, but it looks and reads like 2019 — and the CDN link means the packaged jar is not self-contained: pages degrade to unstyled HTML without internet access.

## What Changes

- Upgrade to current Bootstrap 5 (latest 5.3.x at apply time), served from the executable jar via a WebJar dependency — no runtime CDN dependency.
- Modern page chrome: a proper Bootstrap navbar (brand, navigation links, language switcher), consistent container/spacing, the upload form and workout summary in cards, styled alerts.
- Automatic light/dark color mode following the browser preference (Bootstrap 5.3 color modes).
- Deduplicate the per-template `<head>` boilerplate into a shared Thymeleaf fragment.
- **Preserved behavior:** all functionality and every piece of user-facing text — the upload flow, messages, workout summary content, localization (English/Polish), security rules. Existing tests assert text content, not markup, and must stay green unmodified.

## Non-goals

- No custom theme/SCSS build, no JavaScript beyond Bootstrap's bundled JS and a minimal color-mode snippet, no new pages, no navigation restructuring beyond presenting the existing links properly.
- No accessibility audit beyond what stock Bootstrap 5 components provide.

## Capabilities

### New Capabilities
- `web-ui-presentation`: how the web UI is presented — framework currency, self-contained serving, shared layout, and color modes.

### Modified Capabilities

(none — behavior of existing capabilities is unchanged)

## Impact

- `pom.xml` — WebJar dependency for Bootstrap 5.3.x.
- All templates (`home.html`, `upload.html`, `error.html`, `fragments/*`) — Bootstrap 5 markup, shared head fragment, navbar.
- Existing tests — expected to pass unmodified (they assert text, not CSS classes); any assertion that does touch markup gets reviewed, not silently adapted.
