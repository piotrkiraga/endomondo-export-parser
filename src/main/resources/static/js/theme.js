(function () {
    'use strict';

    var STORAGE_KEY = 'theme';

    /* Filled monochrome icons inheriting the navbar link color; kept in sync with fragments/header.html. */
    var SUN = '<svg viewBox="0 0 16 16" width="20" height="20" fill="currentColor" aria-hidden="true">'
        + '<circle cx="8" cy="8" r="4"/>'
        + '<g stroke="currentColor" stroke-width="2.2" stroke-linecap="round">'
        + '<line x1="8" y1="2.4" x2="8" y2="1.3"/><line x1="8" y1="13.6" x2="8" y2="14.7"/>'
        + '<line x1="2.4" y1="8" x2="1.3" y2="8"/><line x1="13.6" y1="8" x2="14.7" y2="8"/>'
        + '<line x1="4.04" y1="4.04" x2="3.26" y2="3.26"/><line x1="11.96" y1="11.96" x2="12.74" y2="12.74"/>'
        + '<line x1="11.96" y1="4.04" x2="12.74" y2="3.26"/><line x1="4.04" y1="11.96" x2="3.26" y2="12.74"/>'
        + '</g></svg>';

    /* Crescent: an arc of the r=7 disc closed by an arc of the r=10.1 bite circle offset up-and-right. */
    var MOON = '<svg viewBox="0 0 16 16" width="20" height="20" fill="currentColor" aria-hidden="true">'
        + '<path d="M13.73 12.02A7 7 0 1 1 3.99 2.27A10.1 10.1 0 0 0 13.73 12.02Z"/>'
        + '</svg>';

    function storedTheme() {
        var value = localStorage.getItem(STORAGE_KEY);
        return (value === 'light' || value === 'dark') ? value : null;
    }

    /* The icon shows the mode the click would switch to. */
    function apply(theme) {
        document.documentElement.setAttribute('data-bs-theme', theme);
        var toggle = document.getElementById('theme-toggle');
        if (toggle) {
            toggle.innerHTML = theme === 'dark' ? SUN : MOON;
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        apply(document.documentElement.getAttribute('data-bs-theme'));
        var toggle = document.getElementById('theme-toggle');
        if (toggle) {
            toggle.addEventListener('click', function () {
                var next = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
                localStorage.setItem(STORAGE_KEY, next);
                apply(next);
            });
        }
    });

    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (event) {
        if (storedTheme() === null) {
            apply(event.matches ? 'dark' : 'light');
        }
    });

})();
