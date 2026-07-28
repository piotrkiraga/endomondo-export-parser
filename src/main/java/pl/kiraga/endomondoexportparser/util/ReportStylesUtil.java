package pl.kiraga.endomondoexportparser.util;

/**
 * Shared inline CSS for the standalone Photo/Workout reports (tasks 5.1/9.1): both are
 * self-contained HTML files that must open straight from disk, so neither can link to
 * the app's own Bootstrap/webjar assets or its localStorage-backed theme toggle. This
 * duplicates just enough of that visual language (card look, spacing, palette) inline
 * so the two reports look like part of the same app, plus a {@code prefers-color-scheme}
 * dark variant so they respect the browser/OS dark preference even opened offline —
 * the one signal available to a file with no access to the app's own theme state.
 */
public final class ReportStylesUtil {

    private ReportStylesUtil() {
    }

    public static final String CSS = """
            :root{--bg:#f8f9fa;--card-bg:#fff;--border:#dee2e6;--text:#212529;--muted:#6c757d;--link:#0d6efd}
            @media (prefers-color-scheme:dark){:root{--bg:#1a1d20;--card-bg:#25292d;--border:#495057;--text:#dee2e6;--muted:#adb5bd;--link:#6ea8fe}}
            body{font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;margin:0;padding:2em;background:var(--bg);color:var(--text)}
            h1{font-size:1.4em;margin:0 0 .2em}
            a{color:var(--link)}
            .summary{color:var(--muted);margin-bottom:1.5em}
            .workout{margin-bottom:1.25em;padding:1.1em 1.4em;background:var(--card-bg);border:1px solid var(--border);border-radius:.5rem;box-shadow:0 1px 2px rgba(0,0,0,.06)}
            .workout.skip{border-color:#c99a4a}
            .workout h2{font-size:1.05em;margin:0 0 .4em}
            .meta{color:var(--muted);font-size:.85em;margin-bottom:.5em}
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
