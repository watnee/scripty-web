(function () {
    'use strict';

    function isOffline() {
        return !navigator.onLine;
    }

    function updateOfflineUI() {
        var offline = isOffline();
        document.documentElement.classList.toggle('scripty-offline', offline);
        var banner = document.getElementById('scripty-offline-banner');
        if (banner) {
            banner.hidden = !offline;
        }
        if (offline && window.scriptyUpdateOfflineEditBanner) {
            window.scriptyUpdateOfflineEditBanner();
        }
    }

    function getProjectIdFromPage() {
        var params = new URLSearchParams(window.location.search);
        var fromQuery = Number(params.get('id'));
        if (fromQuery) return fromQuery;

        var body = document.getElementById('project-script-body');
        if (!body) return null;
        var sceneBlocks = body.querySelector('.scene-blocks[id^="table-blocks-"]');
        if (!sceneBlocks) return null;
        var match = sceneBlocks.id.match(/^table-blocks-(\d+)$/);
        return match ? Number(match[1]) : null;
    }

    window.scriptyOfflineGetProjectId = getProjectIdFromPage;

    function snapshotProjectIfPresent() {
        if (!window.scriptyOfflineStore) return Promise.resolve();
        var projectId = getProjectIdFromPage();
        if (!projectId) return Promise.resolve();

        var scriptBody = document.getElementById('project-script-body');
        var titleEl = document.getElementById('reader-visible-project-title')
            || document.querySelector('.project-breadcrumb-name-display');
        var title = titleEl ? titleEl.textContent.trim() : '';

        return window.scriptyOfflineStore.saveProjectSnapshot({
            id: projectId,
            title: title || 'Untitled Project',
            pageUrl: window.location.pathname + window.location.search,
            scriptHtml: scriptBody ? scriptBody.outerHTML : '',
            cachedAt: Date.now()
        }).catch(function () {});
    }

    function scheduleSnapshot() {
        if (window.scriptyScheduleOfflineSnapshot) {
            window.scriptyScheduleOfflineSnapshot();
            return;
        }
        snapshotProjectIfPresent();
    }

    function restoreScriptFromStore() {
        if (!isOffline() || !window.scriptyOfflineStore) return Promise.resolve();

        var projectId = getProjectIdFromPage();
        if (!projectId) return Promise.resolve();

        return window.scriptyOfflineStore.getProjectSnapshot(projectId).then(function (snapshot) {
            if (!snapshot || !snapshot.scriptHtml) return;
            var existing = document.getElementById('project-script-body');
            if (!existing) return;
            existing.outerHTML = snapshot.scriptHtml;
            var restored = document.getElementById('project-script-body');
            if (restored && typeof htmx !== 'undefined') {
                htmx.process(restored);
            }
            if (restored && window.scriptyInitBlockDragDrop) {
                restored.querySelectorAll('.scene-blocks').forEach(function (sceneBlocks) {
                    window.scriptyInitBlockDragDrop(sceneBlocks, { projectId: projectId });
                });
            }
            if (snapshot.title) {
                var titleEl = document.getElementById('reader-visible-project-title');
                if (titleEl) titleEl.textContent = snapshot.title;
            }
            return applyPendingBlockEdits(projectId);
        }).catch(function () {});
    }

    function applyPendingBlockEdits(projectId) {
        if (!window.scriptyOfflineStore || !window.scriptyGetBlockEditContext || !window.scriptyRenderBlockShowInline) {
            return Promise.resolve();
        }
        return window.scriptyOfflineStore.listPendingOperations(projectId).then(function (edits) {
            if (!edits || edits.length === 0) return;
            edits.forEach(function (edit) {
                if (edit.type === 'blockCreateBelow') return;
                var row = document.querySelector('[data-block-id="' + edit.blockId + '"]');
                if (!row) return;
                var blockContent = row.querySelector('.block-content');
                if (!blockContent) return;
                var ctx = window.scriptyGetBlockEditContext(blockContent);
                ctx.content = edit.payload.content;
                blockContent.innerHTML = window.scriptyRenderBlockShowInline(ctx);
                if (typeof htmx !== 'undefined') {
                    htmx.process(blockContent);
                }
            });
            if (window.scriptyRefreshOfflinePendingMarkers) {
                return window.scriptyRefreshOfflinePendingMarkers(projectId);
            }
            if (window.scriptyUpdateOfflineEditBanner) {
                window.scriptyUpdateOfflineEditBanner();
            }
        }).catch(function () {});
    }

    function initOfflineSupport() {
        updateOfflineUI();
        restoreScriptFromStore().then(snapshotProjectIfPresent);
    }

    window.addEventListener('online', function () {
        updateOfflineUI();
        if (window.scriptySyncPendingEdits) {
            window.scriptySyncPendingEdits();
        }
    });
    window.addEventListener('offline', updateOfflineUI);

    document.addEventListener('DOMContentLoaded', initOfflineSupport);
    if (document.readyState !== 'loading') {
        initOfflineSupport();
    }

    document.body.addEventListener('htmx:beforeRequest', function (e) {
        var verb = (e.detail.verb || 'get').toLowerCase();
        if (isOffline() && verb !== 'get') {
            var path = (e.detail.pathInfo && e.detail.pathInfo.requestPath) || '';
            if (path.indexOf('/block/editInline') !== -1 ||
                path.indexOf('/block/createBelowInline') !== -1) return;
            e.preventDefault();
        }
    }, true);

    document.body.addEventListener('submit', function (e) {
        if (!isOffline()) return;
        var form = e.target;
        if (!(form instanceof HTMLFormElement)) return;
        if ((form.getAttribute('method') || 'get').toLowerCase() === 'get') return;
        var action = form.getAttribute('hx-post') || form.getAttribute('action') || '';
        if (action.indexOf('/block/editInline') !== -1 ||
            action.indexOf('/block/createBelowInline') !== -1) return;
        e.preventDefault();
    }, true);

    document.body.addEventListener('htmx:afterSwap', function (e) {
        if (isOffline()) return;
        var path = (e.detail.pathInfo && e.detail.pathInfo.requestPath) || '';
        if (path.indexOf('/block/editInline') === -1 &&
            path.indexOf('/block/createBelowInline') === -1 &&
            path.indexOf('/block/createInline') === -1) return;
        scheduleSnapshot();
    });

    window.scriptySaveProjectSnapshot = snapshotProjectIfPresent;
    window.scriptyIsOffline = isOffline;
    window.scriptyApplyPendingBlockEdits = applyPendingBlockEdits;
}());
