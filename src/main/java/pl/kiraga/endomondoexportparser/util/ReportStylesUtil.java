package pl.kiraga.endomondoexportparser.util;

/**
 * Shared inline CSS for the standalone Photo/Workout reports (tasks 5.1/9.1): both are
 * self-contained HTML files that must open straight from disk, so neither can link to
 * the app's own Bootstrap/webjar assets. This duplicates just enough of that visual
 * language (card look, spacing, palette) inline so the two reports look like part of
 * the same app. Light/dark is resolved by a {@code prefers-color-scheme} dark variant
 * (the only signal available to a file opened offline, with no access to the app's own
 * theme state) plus {@code :root[data-theme]} attribute overrides each generator's own
 * script sets, best-effort, when it can read the app's stored explicit choice (same
 * origin only — see the reports-follow-explicit-theme change).
 */
public final class ReportStylesUtil {

    private ReportStylesUtil() {
    }

    public static final String CSS = """
            :root{--bg:#f8f9fa;--card-bg:#fff;--border:#dee2e6;--text:#212529;--muted:#6c757d;--link:#0d6efd;--uploaded-bg:#e0f0e0;--migrated-bg:#ede7f6;--migrated-accent:#6f42c1}
            @media (prefers-color-scheme:dark){:root{--bg:#1a1d20;--card-bg:#25292d;--border:#495057;--text:#dee2e6;--muted:#adb5bd;--link:#6ea8fe;--uploaded-bg:#152015;--migrated-bg:#1e1526;--migrated-accent:#a98eda}}
            /* Higher specificity than the bare :root above/in the media query, so JS
               setting data-theme (from the app's own explicit choice) always wins;
               unset, neither rule matches and the media query above is unaffected. */
            :root[data-theme="light"]{--bg:#f8f9fa;--card-bg:#fff;--border:#dee2e6;--text:#212529;--muted:#6c757d;--link:#0d6efd;--uploaded-bg:#e0f0e0;--migrated-bg:#ede7f6;--migrated-accent:#6f42c1}
            :root[data-theme="dark"]{--bg:#1a1d20;--card-bg:#25292d;--border:#495057;--text:#dee2e6;--muted:#adb5bd;--link:#6ea8fe;--uploaded-bg:#152015;--migrated-bg:#1e1526;--migrated-accent:#a98eda}
            body{font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;margin:0;padding:2em;background:var(--bg);color:var(--text)}
            h1{font-size:1.4em;margin:0 0 .2em}
            a{color:var(--link)}
            .summary{color:var(--muted);margin-bottom:1.5em}
            .workout{margin-bottom:1.25em;padding:1.1em 1.4em;background:var(--card-bg);border:1px solid var(--border);border-radius:.5rem;box-shadow:0 1px 2px rgba(0,0,0,.06)}
            .workout.skip{border-color:#c99a4a}
            .workout.migrated{border-color:var(--migrated-accent);background-color:var(--migrated-bg)}
            /* Declared after .migrated on purpose: both states are independent and can
               apply to the same card, so the later rule's background wins there. */
            .workout.uploaded{border-color:#2a9d5c;background-color:var(--uploaded-bg)}
            .workout h2{font-size:1.05em;margin:0 0 .4em}
            .meta{color:var(--muted);font-size:.85em;margin-bottom:.5em}
            .status-icon{font-weight:700;font-size:1.3em;line-height:1;vertical-align:-.1em}
            /* Mirrors Bootstrap's .btn.btn-sm.btn-outline-secondary look (the app's own
               buttons), duplicated inline for the same self-containment reason as the
               rest of this stylesheet — the reports cannot link the app's webjar CSS. */
            .copy-path,.mark-uploaded{display:inline-block;padding:.3em .75em;margin:0 .4em .6em 0;
              font-size:.78em;font-weight:500;font-family:inherit;color:var(--muted);
              background:transparent;border:1px solid var(--border);border-radius:.35rem;
              cursor:pointer;transition:background-color .15s ease-in-out,color .15s ease-in-out}
            .copy-path:hover,.mark-uploaded:hover{background:var(--muted);color:var(--card-bg);border-color:var(--muted)}
            .workout.uploaded .mark-uploaded{color:#2a9d5c;border-color:#2a9d5c}
            .workout.uploaded .mark-uploaded:hover{background:#2a9d5c;color:#fff}
            .description{font-size:.9em;margin:.5em 0;white-space:pre-wrap}
            .action{display:inline-block;font-size:.72em;padding:.15em .55em;border-radius:1em;color:#fff;font-weight:600}
            .action.upload{background:#2a9d5c}
            .action.manual{background:#3a7fc4}
            .action.skip{background:#b8834a}
            .basename{color:var(--muted)}
            .photos{display:flex;flex-wrap:wrap;gap:.6em}
            .photos figure{margin:0;width:220px}
            .photos img{width:220px;height:auto;display:block;border:1px solid var(--border);border-radius:.35rem;cursor:zoom-in}
            .photos figcaption{font-size:.75em;color:var(--muted);margin-top:.3em}
            .no-location{color:#d9534f}
            figcaption.unmatched{font-family:monospace;font-size:.7em}
            /* Mirrors static/css/app.css's .detail-list/.detail-row (the source of truth) —
               duplicated, not linked, for the same self-containment reason as the rest of
               this stylesheet; keep the two in sync by hand if either changes. */
            .detail-list{display:flex;flex-direction:column;margin:.5em 0}
            .detail-row{display:flex;justify-content:space-between;align-items:baseline;gap:1.5em;padding:.35em 0;border-bottom:1px solid var(--border)}
            .detail-row:last-child{border-bottom:none}
            .detail-label{color:var(--muted);font-size:.85em}
            .detail-value{font-weight:500;text-align:right}
            #lightbox{display:none;position:fixed;inset:0;background:rgba(0,0,0,.85);align-items:center;justify-content:center;z-index:999;cursor:zoom-out}
            #lightbox.open{display:flex}
            #lightbox img{max-width:95vw;max-height:95vh}
            """;

}
