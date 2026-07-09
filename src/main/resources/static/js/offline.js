(function () {
    'use strict';

    var assumedOnline = navigator.onLine;
    var probeTimer = null;
    var PROBE_INTERVAL_MS = 30000;

    function isOffline() {
        return !assumedOnline;
    }

    function isOfflineShellPage() {
        var body = document.getElementById('project-script-body');
        return !!(body && body.getAttribute('data-offline-shell') === 'true');
    }

    function setOnlineState(online) {
        var next = !!online;
        if (assumedOnline === next) {
            updateOfflineUI();
            return;
        }
        assumedOnline = next;
        updateOfflineUI();
        if (assumedOnline && window.scriptySyncPendingEdits) {
            window.scriptySyncPendingEdits();
        }
    }

    function probeConnectivity() {
        if (!navigator.onLine) {
            setOnlineState(false);
            return Promise.resolve(false);
        }
        var url = '/manifest.json?scripty-online-probe=' + Date.now();
        return fetch(url, {
            method: 'GET',
            cache: 'no-store',
            credentials: 'same-origin',
            headers: { 'Cache-Control': 'no-cache' }
        }).then(function (response) {
            setOnlineState(response.ok);
            return response.ok;
        }).catch(function () {
            setOnlineState(false);
            return false;
        });
    }

    function scheduleConnectivityProbe() {
        if (probeTimer) clearTimeout(probeTimer);
        probeTimer = setTimeout(function () {
            probeTimer = null;
            probeConnectivity().finally(scheduleConnectivityProbe);
        }, PROBE_INTERVAL_MS);
    }

    function updateOfflineUI() {
        var offline = isOffline();
        document.documentElement.classList.toggle('scripty-offline', offline);
        var banner = document.getElementById('scripty-offline-banner');
        if (banner) {
            var keepVisible = banner.classList.contains('scripty-offline-banner--syncing') ||
                banner.classList.contains('scripty-offline-banner--error');
            banner.hidden = !offline && !keepVisible;
        }
        if (window.scriptyUpdateOfflineEditBanner) {
            window.scriptyUpdateOfflineEditBanner();
        }
    }

    function getProjectIdFromPage() {
        var params = new URLSearchParams(window.location.search);
        var fromQuery = Number(params.get('id'));
        if (fromQuery) return fromQuery;

        if (window.scriptyProjectId) {
            var fromWindow = Number(window.scriptyProjectId);
            if (fromWindow) return fromWindow;
        }

        var body = document.getElementById('project-script-body');
        if (!body) return null;
        var sceneBlocks = body.querySelector('.scene-blocks[id^="table-blocks-"]');
        if (!sceneBlocks) return null;
        var match = sceneBlocks.id.match(/^table-blocks-(\d+)$/);
        return match ? Number(match[1]) : null;
    }

    window.scriptyOfflineGetProjectId = getProjectIdFromPage;

    function scriptBodyHasContent(scriptBody) {
        if (!scriptBody) return false;
        if (scriptBody.getAttribute('data-offline-shell') === 'true') return false;
        return !!(scriptBody.querySelector('.scene-blocks, .block-row'));
    }

    function snapshotProjectIfPresent() {
        if (!window.scriptyOfflineStore) return Promise.resolve();
        var projectId = getProjectIdFromPage();
        if (!projectId) return Promise.resolve();

        var scriptBody = document.getElementById('project-script-body');
        if (!scriptBodyHasContent(scriptBody)) return Promise.resolve();

        var titleEl = document.getElementById('reader-visible-project-title')
            || document.querySelector('.project-breadcrumb-name-display');
        var title = titleEl ? titleEl.textContent.trim() : '';

        return window.scriptyOfflineStore.saveProjectSnapshot({
            id: projectId,
            title: title || 'Untitled Project',
            pageUrl: '/project/show?id=' + encodeURIComponent(projectId),
            scriptHtml: scriptBody.outerHTML,
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

    function applyRestoredScript(snapshot, projectId) {
        if (!snapshot || !snapshot.scriptHtml) return Promise.resolve(false);
        var existing = document.getElementById('project-script-body');
        var mount = document.getElementById('offline-project-mount');
        if (!existing) {
            if (!mount) return Promise.resolve(false);
            mount.innerHTML = snapshot.scriptHtml;
        } else {
            existing.outerHTML = snapshot.scriptHtml;
        }
        var wasShell = isOfflineShellPage() || document.title.indexOf('Offline') !== -1;
        var restored = document.getElementById('project-script-body');
        if (restored) {
            restored.removeAttribute('data-offline-shell');
        }
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
            if (wasShell) {
                document.title = 'Scripty - ' + snapshot.title;
            }
        }
        var missing = document.getElementById('offline-project-missing');
        if (missing) missing.hidden = true;
        if (mount) mount.hidden = false;
        return applyPendingBlockEdits(projectId).then(function () { return true; });
    }

    function restoreScriptFromStore() {
        if (!window.scriptyOfflineStore) return Promise.resolve();

        var projectId = getProjectIdFromPage();
        if (!projectId) return Promise.resolve();

        var existing = document.getElementById('project-script-body');
        var forceRestore = isOfflineShellPage() || (existing && !scriptBodyHasContent(existing));
        if (!forceRestore && !isOffline()) return Promise.resolve();

        return window.scriptyOfflineStore.getProjectSnapshot(projectId).then(function (snapshot) {
            if (!snapshot || !snapshot.scriptHtml) {
                if (forceRestore) {
                    var missing = document.getElementById('offline-project-missing');
                    if (missing) missing.hidden = false;
                    if (existing && existing.getAttribute('data-offline-shell') === 'true') {
                        var mount = document.getElementById('offline-project-mount');
                        if (mount) mount.hidden = true;
                    }
                }
                return;
            }
            return applyRestoredScript(snapshot, projectId);
        }).catch(function () {});
    }

    function applyPendingBlockEdits(projectId) {
        if (!window.scriptyOfflineStore || !window.scriptyGetBlockEditContext || !window.scriptyRenderBlockShowInline) {
            return Promise.resolve();
        }
        return window.scriptyOfflineStore.listPendingOperations(projectId).then(function (edits) {
            if (!edits || edits.length === 0) return;
            edits.forEach(function (edit) {
                if (edit.type === 'blockCreateBelow') {
                    applyPendingCreate(edit);
                    return;
                }
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

    function applyPendingCreate(edit) {
        if (!edit || !edit.tempBlockId || !window.scriptyRenderOptimisticBlockRow) return;
        var content = (edit.payload && edit.payload.content) || '';
        var existingRow = document.querySelector('[data-block-id="' + edit.tempBlockId + '"]');
        if (existingRow) {
            var blockContent = existingRow.querySelector('.block-content');
            if (blockContent && window.scriptyGetBlockEditContext && window.scriptyRenderBlockShowInline) {
                var ctx = window.scriptyGetBlockEditContext(blockContent);
                ctx.content = content;
                blockContent.innerHTML = window.scriptyRenderBlockShowInline(ctx);
                if (typeof htmx !== 'undefined') {
                    htmx.process(blockContent);
                }
            }
            return;
        }

        var anchorId = edit.anchorBlockId || (edit.payload && edit.payload.id);
        var anchor = anchorId ? document.querySelector('[data-block-id="' + anchorId + '"]') : null;
        var html = window.scriptyRenderOptimisticBlockRow({
            tempBlockId: edit.tempBlockId,
            content: content
        });
        var template = document.createElement('template');
        template.innerHTML = html.trim();
        var newRow = template.content.firstElementChild;
        if (!newRow) return;

        if (anchor && anchor.parentNode) {
            // Insert after the latest sibling that belongs to this create chain.
            var insertAfter = anchor;
            var next = anchor.nextElementSibling;
            while (next && next.hasAttribute('data-block-id') &&
                String(next.getAttribute('data-block-id')).indexOf('offline-') === 0) {
                insertAfter = next;
                next = next.nextElementSibling;
            }
            insertAfter.insertAdjacentElement('afterend', newRow);
        } else {
            var body = document.getElementById('project-script-body');
            var sceneBlocks = body ? body.querySelector('.scene-blocks') : null;
            if (!sceneBlocks) return;
            sceneBlocks.appendChild(newRow);
        }
        if (typeof htmx !== 'undefined') {
            htmx.process(newRow);
        }
    }

    function initOfflineSupport() {
        updateOfflineUI();
        var startedAsShell = isOfflineShellPage();
        restoreScriptFromStore().then(function () {
            // Avoid overwriting IndexedDB with an empty offline shell.
            if (!startedAsShell) {
                snapshotProjectIfPresent();
            }
        });
        probeConnectivity().finally(scheduleConnectivityProbe);
    }

    window.addEventListener('online', function () {
        probeConnectivity();
    });
    window.addEventListener('offline', function () {
        setOnlineState(false);
    });

    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) {
            probeConnectivity();
        }
    });

    // Defer until after sibling scripts (offline-edit.js) finish loading.
    function whenReady(fn) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', fn);
        } else {
            setTimeout(fn, 0);
        }
    }
    whenReady(initOfflineSupport);

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
    window.scriptyProbeConnectivity = probeConnectivity;
    window.scriptyRestoreScriptFromStore = restoreScriptFromStore;
}());
