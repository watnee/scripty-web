/**
 * Page setup: paper size, margin preset, and page-number placement.
 *
 * One source of truth for three surfaces that must agree:
 *   - the on-screen page canvas (CSS custom properties)
 *   - browser print output (an injected @page rule)
 *   - server-side PDF export (paper/margins query params on the export link,
 *     parsed by com.scripty.dto.PageSetup)
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyPageSetupInit) return;
    window._scriptyPageSetupInit = true;

    var STORAGE_KEY = 'scripty-page-setup';
    var STYLE_ID = 'scripty-page-setup-print';
    var DIALOG_ID = 'page-setup-dialog';

    var PAPERS = {
        letter: { label: 'US Letter (8.5 × 11 in)', widthIn: 8.5, heightIn: 11, cssSize: 'letter' },
        a4: { label: 'A4 (210 × 297 mm)', widthIn: 8.2677, heightIn: 11.6929, cssSize: 'A4' }
    };

    // Inches. The binding edge keeps the extra width, per screenplay convention.
    var MARGINS = {
        standard: { label: 'Standard (1 in, 1.5 in binding)', top: 1, right: 1, bottom: 1, left: 1.5 },
        narrow: { label: 'Narrow (0.5 in, 1 in binding)', top: 0.5, right: 0.5, bottom: 0.5, left: 1 },
        wide: { label: 'Wide (1.25 in, 1.75 in binding)', top: 1.25, right: 1.25, bottom: 1.25, left: 1.75 }
    };

    var PAGE_NUMBERS = {
        'top-right': 'Top right',
        'top-left': 'Top left',
        'bottom-center': 'Bottom centre',
        'none': 'None'
    };

    var PAGE_NUMBER_CLASSES = Object.keys(PAGE_NUMBERS).map(function (key) {
        return 'scripty-pagenum-' + key;
    });

    var DEFAULTS = { paper: 'letter', margins: 'standard', pageNumbers: 'top-right' };

    function read() {
        var saved = {};
        try {
            saved = JSON.parse(localStorage.getItem(STORAGE_KEY)) || {};
        } catch (err) { /* corrupt or absent — fall back to defaults */ }

        return {
            paper: PAPERS[saved.paper] ? saved.paper : DEFAULTS.paper,
            margins: MARGINS[saved.margins] ? saved.margins : DEFAULTS.margins,
            pageNumbers: PAGE_NUMBERS[saved.pageNumbers] ? saved.pageNumbers : DEFAULTS.pageNumbers
        };
    }

    function write(setup) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(setup));
    }

    /**
     * Percentages are resolved against the page's width in CSS — including
     * padding-top/bottom — so every margin is expressed relative to paper width
     * to keep the physical inches correct at any sheet size.
     */
    function marginPercent(inches, paper) {
        return ((inches / PAPERS[paper].widthIn) * 100).toFixed(4) + '%';
    }

    function applyVars(setup) {
        var root = document.documentElement;
        var paper = PAPERS[setup.paper];
        var margin = MARGINS[setup.margins];

        root.style.setProperty('--scripty-page-aspect', paper.widthIn + ' / ' + paper.heightIn);
        root.style.setProperty('--scripty-page-pad-top', marginPercent(margin.top, setup.paper));
        root.style.setProperty('--scripty-page-pad-right', marginPercent(margin.right, setup.paper));
        root.style.setProperty('--scripty-page-pad-bottom', marginPercent(margin.bottom, setup.paper));
        root.style.setProperty('--scripty-page-pad-left', marginPercent(margin.left, setup.paper));

        PAGE_NUMBER_CLASSES.forEach(function (name) { root.classList.remove(name); });
        root.classList.add('scripty-pagenum-' + setup.pageNumbers);
    }

    /**
     * The stylesheet ships a static @page rule; this one is appended to <head> at
     * runtime so it wins on cascade order without touching the base sheet.
     */
    function applyPrintRule(setup) {
        var paper = PAPERS[setup.paper];
        var margin = MARGINS[setup.margins];
        var style = document.getElementById(STYLE_ID);
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            document.head.appendChild(style);
        }
        style.textContent = '@page { size: ' + paper.cssSize + '; margin: '
            + margin.top + 'in ' + margin.right + 'in ' + margin.bottom + 'in ' + margin.left + 'in; }';
    }

    /** Keep the PDF export link in step so the download matches what's on screen. */
    function applyExportLinks(setup) {
        var links = document.querySelectorAll('a[href*="format=pdf"]');
        for (var i = 0; i < links.length; i++) {
            var link = links[i];
            var url;
            try {
                url = new URL(link.getAttribute('href'), window.location.origin);
            } catch (err) {
                continue;
            }
            url.searchParams.set('paper', setup.paper);
            url.searchParams.set('margins', setup.margins);
            link.setAttribute('href', url.pathname + url.search);
        }
    }

    function apply(options) {
        var setup = read();
        applyVars(setup);
        applyPrintRule(setup);
        applyExportLinks(setup);
        if (options && options.silent) return setup;
        try {
            window.dispatchEvent(new CustomEvent('scripty:page-setup-changed', { detail: setup }));
        } catch (err) { /* ignore */ }
        return setup;
    }

    function radioGroup(name, options, selected) {
        var html = '';
        Object.keys(options).forEach(function (key) {
            var label = typeof options[key] === 'string' ? options[key] : options[key].label;
            html += '<label class="page-setup-option">'
                + '<input type="radio" name="' + name + '" value="' + key + '"'
                + (key === selected ? ' checked' : '') + '>'
                + '<span>' + label + '</span></label>';
        });
        return html;
    }

    function buildDialog() {
        var setup = read();
        var dialog = document.createElement('dialog');
        dialog.id = DIALOG_ID;
        dialog.className = 'page-setup-dialog';
        dialog.setAttribute('aria-label', 'Page setup');

        dialog.innerHTML =
            '<form method="dialog" class="page-setup-form">'
            + '<h2 class="page-setup-title">Page setup</h2>'
            + '<fieldset class="page-setup-group"><legend>Paper size</legend>'
            + radioGroup('paper', PAPERS, setup.paper) + '</fieldset>'
            + '<fieldset class="page-setup-group"><legend>Margins</legend>'
            + radioGroup('margins', MARGINS, setup.margins) + '</fieldset>'
            + '<fieldset class="page-setup-group"><legend>Page numbers</legend>'
            + radioGroup('pageNumbers', PAGE_NUMBERS, setup.pageNumbers) + '</fieldset>'
            + '<p class="page-setup-note">Applies to page view, printing, and PDF export.</p>'
            + '<div class="page-setup-actions">'
            + '<button type="button" class="page-setup-reset" data-page-setup="reset">Reset</button>'
            + '<button type="submit" class="page-setup-done" value="close">Done</button>'
            + '</div></form>';

        document.body.appendChild(dialog);

        // Radios apply live so the canvas behind the dialog previews the choice.
        dialog.addEventListener('change', function (e) {
            var field = e.target;
            if (!field || field.type !== 'radio') return;
            var next = read();
            next[field.name] = field.value;
            write(next);
            apply();
        });

        dialog.addEventListener('click', function (e) {
            if (!e.target || !e.target.closest) return;
            if (!e.target.closest('[data-page-setup="reset"]')) return;
            write(DEFAULTS);
            var applied = apply();
            syncDialogFields(dialog, applied);
        });

        return dialog;
    }

    function syncDialogFields(dialog, setup) {
        ['paper', 'margins', 'pageNumbers'].forEach(function (name) {
            var field = dialog.querySelector('input[name="' + name + '"][value="' + setup[name] + '"]');
            if (field) field.checked = true;
        });
    }

    function openDialog() {
        var dialog = document.getElementById(DIALOG_ID) || buildDialog();
        syncDialogFields(dialog, read());
        if (typeof dialog.showModal === 'function') dialog.showModal();
        else dialog.setAttribute('open', 'open');
    }

    window.scriptyGetPageSetup = read;
    window.scriptyOpenPageSetup = openDialog;
    window.scriptySetPageSetup = function (patch) {
        var next = read();
        if (patch && PAPERS[patch.paper]) next.paper = patch.paper;
        if (patch && MARGINS[patch.margins]) next.margins = patch.margins;
        if (patch && PAGE_NUMBERS[patch.pageNumbers]) next.pageNumbers = patch.pageNumbers;
        write(next);
        return apply();
    };

    document.body.addEventListener('click', function (e) {
        if (e.target && e.target.closest && e.target.closest('#page-setup-open')) {
            e.preventDefault();
            openDialog();
        }
    });

    // Export links are re-rendered by HTMX swaps; re-stamp their params.
    document.body.addEventListener('htmx:afterSettle', function () {
        apply({ silent: true });
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { apply({ silent: true }); });
    } else {
        apply({ silent: true });
    }
})();
