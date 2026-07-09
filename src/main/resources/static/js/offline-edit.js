(function () {
    'use strict';

    var syncing = false;
    var syncFailed = false;
    var snapshotTimer = null;

    function escText(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function escAttr(value) {
        return escText(value).replace(/"/g, '&quot;');
    }

    function isTempBlockId(id) {
        return String(id || '').indexOf('offline-') === 0;
    }

    function nextTempBlockId() {
        return 'offline-' + Date.now() + '-' + Math.floor(Math.random() * 10000);
    }

    function getProjectIdFromPage() {
        if (window.scriptyOfflineGetProjectId) {
            return window.scriptyOfflineGetProjectId();
        }
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

    function renderBlockTextHtml(content) {
        var text = escText(content || '');
        return '<p class="script-block-text">' + (text || '&#160;') + '</p>';
    }

    function renderMirrorTextHtml(content) {
        var text = escText(content || '');
        return '<span class="script-block-text reader-visible-text">' + (text || '&#160;') + '</span>';
    }

    function getCharacterHtml(blockContent) {
        var charEl = blockContent.querySelector('.script-character-name');
        return charEl ? charEl.outerHTML : '';
    }

    function renderTagsHtml(tags) {
        if (!tags) return '';
        var parts = String(tags).split(',').map(function (tag) {
            return tag.trim();
        }).filter(Boolean);
        if (parts.length === 0) return '';
        return '<div class="block-tags-list hide-in-reader-view sidebar menu">' +
            parts.map(function (tag) {
                return '<span class="block-tag clickable" title="Search: ' + escAttr(tag) + '">' +
                    escText(tag) + '</span>';
            }).join('') +
            '</div>';
    }

    function getBlockEditContext(blockContent) {
        var row = blockContent.closest('[data-block-id]');
        var blockId = row ? row.getAttribute('data-block-id') : '';
        var form = blockContent.querySelector('form[hx-post*="/block/editInline"], form[hx-post*="/block/createBelowInline"]');
        var textarea = form ? form.querySelector('textarea[name="content"]') : null;
        var contentP = blockContent.querySelector('.script-block-text');
        var content = textarea ? textarea.value : (contentP ? contentP.textContent : '');
        var tags = row ? (row.getAttribute('data-tags') || '') : '';
        var personInput = blockContent.querySelector('input[name="personId"]');
        var personId = personInput ? personInput.value : '';
        if (form) {
            var tagsInput = form.querySelector('input[name="tags"]');
            if (tagsInput) tags = tagsInput.value || tags;
            var idInput = form.querySelector('input[name="id"]');
            if (idInput && idInput.value) blockId = idInput.value;
        }
        return {
            blockId: blockId,
            content: content,
            tags: tags,
            personId: personId,
            characterHtml: getCharacterHtml(blockContent)
        };
    }

    function renderEditForm(ctx) {
        var enterHandler = "if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();event.stopPropagation;" +
            "if(window.scriptyCreateBlockFromSavedEdit){window.scriptyCreateBlockFromSavedEdit(this);}return false;}";
        return (ctx.characterHtml || '') +
            '<form class="hide-in-reader-view block-inline-edit-form" hx-post="/block/editInline" ' +
            'hx-target="closest .block-content" hx-swap="innerHTML" ' +
            'hx-trigger="change from:find select, focusout[target.value.trim()!=\'\'] from:find textarea">' +
            '<input type="hidden" name="id" value="' + escAttr(ctx.blockId) + '" />' +
            '<input type="hidden" name="personId" id="block-person-id-' + escAttr(ctx.blockId) + '" value="' + escAttr(ctx.personId) + '" />' +
            '<input type="hidden" name="tags" value="' + escAttr(ctx.tags) + '" />' +
            renderMirrorTextHtml(ctx.content) +
            '<textarea spellcheck="true" autocomplete="off" autocorrect="off" autocapitalize="off" rows="1" class="script-block-text block-input-textarea" name="content" ' +
            'onkeydown="' + enterHandler + '">' + escText(ctx.content) + '</textarea>' +
            renderTagsHtml(ctx.tags) +
            '</form>';
    }

    function renderShowInline(ctx) {
        return (ctx.characterHtml || '') +
            renderBlockTextHtml(ctx.content) +
            renderTagsHtml(ctx.tags);
    }

    function renderCreateBelowRow(anchorBlockId) {
        return '<div class="block-row" data-block-type="ACTION">' +
            '<span class="block-element-label hide-in-reader-view sidebar menu" data-block-type="ACTION" title="Fountain element type">Action</span>' +
            '<aside class="block-left-controls hide-in-reader-view sidebar menu">' +
            '<div class="block-left-controls-inner">' +
            '<input type="checkbox" class="block-select-checkbox" title="Selection available after saving block" aria-label="Selection available after saving block" />' +
            '<a href="#" role="button" class="create-below" title="Add block below" aria-label="Add block below">+</a>' +
            '</div></aside>' +
            '<div class="block-content script-block block-type-action script-block--action">' +
            '<form hx-post="/block/createBelowInline" hx-target="closest .block-row" hx-swap="outerHTML">' +
            '<input type="hidden" name="id" value="' + escAttr(anchorBlockId) + '" />' +
            '<input type="hidden" name="surface" value="project" />' +
            '<input type="hidden" name="type" value="ACTION" />' +
            '<textarea spellcheck="true" autocomplete="off" autocorrect="off" autocapitalize="off" rows="1" class="script-block-text block-input-textarea" name="content" autofocus ' +
            'onkeydown="if(event.key===\'Enter\'&&!event.shiftKey){event.preventDefault();event.stopPropagation;' +
            'if(window.scriptyCreateBlockFromCreateRow){window.scriptyCreateBlockFromCreateRow(this);}return false;}"></textarea>' +
            '</form></div>' +
            '<aside class="block-actions hide-in-reader-view sidebar menu"></aside>' +
            '</div>';
    }

    function renderOptimisticBlockRow(opts) {
        var contentHtml = renderBlockTextHtml(opts.content);
        return '<div class="block-row block-offline-pending" data-block-id="' + escAttr(opts.tempBlockId) + '" ' +
            'data-offline-pending="create" data-block-type="ACTION" data-tags="" data-bookmarked="false" data-pinned="false">' +
            '<span class="block-element-label hide-in-reader-view sidebar menu" data-block-type="ACTION" title="Fountain element type">Action</span>' +
            '<aside class="block-left-controls hide-in-reader-view sidebar menu">' +
            '<div class="block-left-controls-inner">' +
            '<input type="checkbox" class="block-select-checkbox" value="' + escAttr(opts.tempBlockId) + '" />' +
            '<a href="#" role="button" class="create-below" title="Add block below" aria-label="Add block below">+</a>' +
            '</div></aside>' +
            '<div class="block-content script-block block-type-action script-block--action" ' +
            'hx-get="/block/editInline?id=' + escAttr(opts.tempBlockId) + '" ' +
            'hx-trigger="click[!event.target.closest(\'a\')&amp;&amp;!event.target.closest(\'form\')&amp;&amp;!window.scriptySuppressBlockEditClick&amp;&amp;!window.scriptyBlockEditLocked]" hx-swap="innerHTML">' +
            contentHtml +
            '</div>' +
            '<aside class="block-actions hide-in-reader-view sidebar menu"></aside>' +
            '</div>';
    }

    function processNodes(nodes) {
        if (!nodes || !nodes.length || typeof htmx === 'undefined') return;
        nodes.forEach(function (node) {
            htmx.process(node);
        });
    }

    function scheduleSnapshot() {
        if (!window.scriptySaveProjectSnapshot) return;
        if (snapshotTimer) clearTimeout(snapshotTimer);
        snapshotTimer = setTimeout(function () {
            snapshotTimer = null;
            window.scriptySaveProjectSnapshot();
        }, 400);
    }

    function getBannerElements() {
        return {
            banner: document.getElementById('scripty-offline-banner'),
            text: document.getElementById('scripty-offline-banner-text'),
            retry: document.getElementById('scripty-offline-sync-retry')
        };
    }

    function setBannerMessage(message, options) {
        var els = getBannerElements();
        if (!els.banner) return;
        var target = els.text || els.banner;
        target.textContent = message;
        els.banner.classList.toggle('scripty-offline-banner--error', !!(options && options.error));
        els.banner.classList.toggle('scripty-offline-banner--syncing', !!(options && options.syncing));
        if (els.retry) {
            els.retry.hidden = !(options && options.showRetry);
        }
    }

    function updateOfflineBanner() {
        if (!window.scriptyIsOffline || !window.scriptyIsOffline()) return;
        if (syncing) return;

        var projectId = getProjectIdFromPage();
        var baseText = "You're offline — edits save locally and sync when you're back online.";
        if (!window.scriptyOfflineStore || !projectId) {
            setBannerMessage(baseText);
            return;
        }

        window.scriptyOfflineStore.countPendingEdits(projectId).then(function (count) {
            if (syncFailed) {
                setBannerMessage('Some edits failed to sync. Check your connection and retry.', { error: true, showRetry: true });
                return;
            }
            if (count > 0) {
                setBannerMessage(baseText + ' (' + count + ' unsynced change' + (count === 1 ? '' : 's') + ')');
            } else {
                setBannerMessage(baseText);
            }
        }).catch(function () {
            setBannerMessage(baseText);
        });
    }

    function flashSavedLocally() {
        var els = getBannerElements();
        if (!els.banner || !window.scriptyIsOffline || !window.scriptyIsOffline()) return;
        els.banner.classList.add('scripty-offline-banner--saved');
        setTimeout(function () {
            els.banner.classList.remove('scripty-offline-banner--saved');
            updateOfflineBanner();
        }, 1200);
        setBannerMessage('Saved locally');
    }

    function refreshPendingMarkers(projectId) {
        document.querySelectorAll('.block-row.block-offline-pending, .block-row[data-offline-pending]').forEach(function (row) {
            row.classList.remove('block-offline-pending');
            row.removeAttribute('data-offline-pending');
        });
        if (!window.scriptyOfflineStore) return Promise.resolve();
        return window.scriptyOfflineStore.listPendingOperations(projectId).then(function (ops) {
            ops.forEach(function (op) {
                var blockId = op.blockId || op.tempBlockId;
                if (!blockId) return;
                var row = document.querySelector('[data-block-id="' + blockId + '"]');
                if (!row) return;
                row.classList.add('block-offline-pending');
                row.setAttribute('data-offline-pending', op.type === 'blockCreateBelow' ? 'create' : 'edit');
            });
        }).catch(function () {});
    }

    function focusTextareaEnd(textarea) {
        if (!textarea) return;
        textarea.focus({ preventScroll: true });
        var len = textarea.value.length;
        textarea.setSelectionRange(len, len);
    }

    function openBlockEditOffline(blockContent, caretOffset) {
        if (!blockContent) return null;
        var ctx = getBlockEditContext(blockContent);
        if (!ctx.blockId) return null;
        blockContent.dataset.originalContent = ctx.content;
        blockContent.innerHTML = renderEditForm(ctx);
        processNodes([blockContent]);
        var textarea = blockContent.querySelector('textarea[name="content"]');
        if (textarea) {
            if (caretOffset != null) {
                var pos = Math.max(0, Math.min(caretOffset, textarea.value.length));
                textarea.focus({ preventScroll: true });
                textarea.setSelectionRange(pos, pos);
            } else {
                focusTextareaEnd(textarea);
            }
            textarea.dispatchEvent(new Event('input', { bubbles: true }));
        }
        return textarea;
    }

    function openCreateBelowOffline(row) {
        if (!row || !row.hasAttribute('data-block-id')) return null;
        var anchorId = row.getAttribute('data-block-id');
        var nextRow = row.nextElementSibling;
        if (nextRow && nextRow.classList.contains('block-row') && !nextRow.hasAttribute('data-block-id')) {
            focusTextareaEnd(nextRow.querySelector('textarea[name="content"]'));
            return nextRow;
        }
        var template = document.createElement('template');
        template.innerHTML = renderCreateBelowRow(anchorId).trim();
        var createRow = template.content.firstElementChild;
        if (!createRow || !row.parentNode) return null;
        row.insertAdjacentElement('afterend', createRow);
        processNodes([createRow]);
        focusTextareaEnd(createRow.querySelector('textarea[name="content"]'));
        return createRow;
    }

    function replaceRowWithHtml(row, html) {
        var template = document.createElement('template');
        template.innerHTML = html.trim();
        var newRows = Array.from(template.content.children);
        if (newRows.length === 0 || !row.parentNode) return [];
        row.replaceWith.apply(row, newRows);
        processNodes(newRows);
        return newRows;
    }

    function saveBlockEditOffline(form, done) {
        if (!form) {
            if (done) done();
            return Promise.resolve();
        }
        if (form.dataset.committing === 'true') {
            if (done) done();
            return Promise.resolve();
        }

        var textarea = form.querySelector('textarea[name="content"]');
        if (!textarea) {
            if (done) done();
            return Promise.resolve();
        }

        if (textarea.value.trim() === '') {
            return revertBlockEditOffline(form).finally(function () {
                if (done) done();
            });
        }

        var blockContent = form.closest('.block-content');
        var ctx = getBlockEditContext(blockContent);
        ctx.content = textarea.value;
        var original = blockContent ? blockContent.dataset.originalContent : null;
        if (original != null && original === ctx.content) {
            blockContent.innerHTML = renderShowInline(ctx);
            processNodes([blockContent]);
            if (done) done();
            return Promise.resolve();
        }

        form.dataset.committing = 'true';
        var projectId = getProjectIdFromPage();
        var work = Promise.resolve();

        if (window.scriptyOfflineStore && projectId) {
            if (isTempBlockId(ctx.blockId)) {
                work = window.scriptyOfflineStore.updatePendingCreateContent(ctx.blockId, ctx.content);
            } else {
                var payload = {
                    id: ctx.blockId,
                    content: ctx.content,
                    personId: ctx.personId || '',
                    tags: ctx.tags || ''
                };
                work = window.scriptyOfflineStore.clearPendingEditsForBlock(ctx.blockId)
                    .then(function () {
                        return window.scriptyOfflineStore.enqueueBlockEdit({
                            projectId: projectId,
                            blockId: Number(ctx.blockId),
                            payload: payload
                        });
                    });
            }
        }

        return work.then(function () {
            if (blockContent) {
                blockContent.innerHTML = renderShowInline(ctx);
                processNodes([blockContent]);
            }
            scheduleSnapshot();
            flashSavedLocally();
            return refreshPendingMarkers(projectId);
        }).catch(function () {
            /* keep edit form if local save fails */
        }).finally(function () {
            delete form.dataset.committing;
            updateOfflineBanner();
            if (done) done();
        });
    }

    function saveCreateBelowOffline(form, row, done) {
        if (!form || !row) {
            if (done) done();
            return Promise.resolve();
        }
        var textarea = form.querySelector('textarea[name="content"]');
        if (!textarea || textarea.value.trim() === '') {
            row.remove();
            scheduleSnapshot();
            if (done) done();
            return Promise.resolve();
        }

        var projectId = getProjectIdFromPage();
        var anchorId = form.querySelector('input[name="id"]').value;
        var tempBlockId = nextTempBlockId();
        var payload = {
            id: anchorId,
            content: textarea.value,
            surface: 'project'
        };

        var optimistic = renderOptimisticBlockRow({
            tempBlockId: tempBlockId,
            content: textarea.value
        });
        var createRow = renderCreateBelowRow(tempBlockId);
        var template = document.createElement('template');
        template.innerHTML = optimistic + createRow;
        var newRows = Array.from(template.content.children);
        if (newRows.length < 2 || !row.parentNode) {
            if (done) done();
            return Promise.resolve();
        }
        row.replaceWith(newRows[0], newRows[1]);
        processNodes(newRows);

        var work = Promise.resolve();
        if (window.scriptyOfflineStore && projectId) {
            work = window.scriptyOfflineStore.enqueueOperation({
                type: 'blockCreateBelow',
                projectId: projectId,
                tempBlockId: tempBlockId,
                anchorBlockId: anchorId,
                payload: payload
            });
        }

        return work.then(function () {
            scheduleSnapshot();
            flashSavedLocally();
            focusTextareaEnd(newRows[1].querySelector('textarea[name="content"]'));
            return refreshPendingMarkers(projectId);
        }).catch(function () {
        }).finally(function () {
            updateOfflineBanner();
            if (done) done();
        });
    }

    function revertBlockEditOffline(form) {
        if (!form) return Promise.resolve();
        var blockContent = form.closest('.block-content');
        if (!blockContent) return Promise.resolve();
        var ctx = getBlockEditContext(blockContent);
        var original = blockContent.dataset.originalContent;
        if (original != null) {
            ctx.content = original;
        }
        blockContent.innerHTML = renderShowInline(ctx);
        processNodes([blockContent]);
        return Promise.resolve();
    }

    function resolveAnchorId(anchorId, tempIdMap) {
        var current = String(anchorId);
        while (isTempBlockId(current) && tempIdMap[current]) {
            current = String(tempIdMap[current]);
        }
        return current;
    }

    function extractSavedBlockId(html) {
        var template = document.createElement('template');
        template.innerHTML = html.trim();
        var row = template.content.querySelector('[data-block-id]');
        return row ? row.getAttribute('data-block-id') : null;
    }

    async function syncOneOperation(op, tempIdMap) {
        if (op.type === 'blockEdit') {
            var blockId = op.payload.id;
            if (isTempBlockId(blockId)) {
                blockId = resolveAnchorId(blockId, tempIdMap);
                if (isTempBlockId(blockId)) return false;
                op.payload.id = blockId;
            }
            var params = new URLSearchParams(op.payload);
            var response = await fetch('/block/editInline', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                credentials: 'same-origin',
                body: params.toString()
            });
            return response.ok;
        }

        if (op.type === 'blockCreateBelow') {
            var anchorId = resolveAnchorId(op.anchorBlockId || op.payload.id, tempIdMap);
            if (isTempBlockId(anchorId)) return false;
            var createParams = new URLSearchParams(op.payload);
            createParams.set('id', anchorId);
            var createResponse = await fetch('/block/createBelowInline', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                credentials: 'same-origin',
                body: createParams.toString()
            });
            if (!createResponse.ok) return false;
            var html = await createResponse.text();
            var savedId = extractSavedBlockId(html);
            if (savedId && op.tempBlockId) {
                tempIdMap[op.tempBlockId] = savedId;
                var tempRow = document.querySelector('[data-block-id="' + op.tempBlockId + '"]');
                if (tempRow) {
                    replaceRowWithHtml(tempRow, html);
                }
            }
            return true;
        }

        return false;
    }

    async function syncPendingEdits() {
        if (syncing || !navigator.onLine || !window.scriptyOfflineStore) return;
        syncing = true;
        syncFailed = false;

        try {
            var ops = await window.scriptyOfflineStore.listPendingOperations();
            if (ops.length === 0) {
                syncing = false;
                updateOfflineBanner();
                return;
            }

            setBannerMessage('Back online — syncing ' + ops.length + ' change' + (ops.length === 1 ? '' : 's') + '…', { syncing: true });

            var tempIdMap = {};
            var failedAt = null;
            for (var i = 0; i < ops.length; i++) {
                var ok = await syncOneOperation(ops[i], tempIdMap);
                if (!ok) {
                    failedAt = ops[i];
                    syncFailed = true;
                    break;
                }
                await window.scriptyOfflineStore.removePendingOperation(ops[i].id);
            }

            if (!syncFailed) {
                var projectId = getProjectIdFromPage();
                await refreshPendingMarkers(projectId);
                if (window.scriptySaveProjectSnapshot) {
                    await window.scriptySaveProjectSnapshot();
                }
                setBannerMessage('All offline changes synced.');
                setTimeout(updateOfflineBanner, 1500);
            } else {
                setBannerMessage('Could not sync "' + (failedAt.type || 'change') + '". Retry when your connection is stable.', {
                    error: true,
                    showRetry: true
                });
            }
        } catch (err) {
            syncFailed = true;
            setBannerMessage('Sync failed. Retry when your connection is stable.', { error: true, showRetry: true });
        } finally {
            syncing = false;
        }
    }

    function isOfflineBlockPath(path) {
        return path.indexOf('/block/editInline') !== -1 ||
            path.indexOf('/block/createBelowInline') !== -1;
    }

    document.body.addEventListener('htmx:beforeRequest', function (e) {
        if (navigator.onLine) return;
        var path = (e.detail.pathInfo && e.detail.pathInfo.requestPath) || '';
        if (!isOfflineBlockPath(path)) return;

        e.preventDefault();

        if (path.indexOf('/block/editInline') !== -1 && e.detail.verb === 'get') {
            openBlockEditOffline(e.detail.elt);
            return;
        }

        if (path.indexOf('/block/editInline') !== -1 && e.detail.verb === 'post') {
            var editForm = e.detail.elt;
            if (editForm && editForm.tagName === 'FORM') {
                saveBlockEditOffline(editForm);
            }
            return;
        }

        if (path.indexOf('/block/createBelowInline') !== -1 && e.detail.verb === 'get') {
            var anchorRow = e.detail.elt ? e.detail.elt.closest('.block-row[data-block-id]') : null;
            if (anchorRow) {
                openCreateBelowOffline(anchorRow);
            }
            return;
        }

        if (path.indexOf('/block/createBelowInline') !== -1 && e.detail.verb === 'post') {
            var createForm = e.detail.elt;
            var createRow = createForm ? createForm.closest('.block-row') : null;
            if (createForm && createRow) {
                saveCreateBelowOffline(createForm, createRow);
            }
        }
    }, true);

    document.addEventListener('click', function (e) {
        var retry = e.target.closest('#scripty-offline-sync-retry');
        if (retry) {
            e.preventDefault();
            syncFailed = false;
            syncPendingEdits();
            return;
        }

        if (navigator.onLine) return;
        var link = e.target.closest('.create-below');
        if (!link) return;
        var row = link.closest('.block-row[data-block-id]');
        if (!row) return;
        e.preventDefault();
        e.stopPropagation();
        openCreateBelowOffline(row);
    });

    document.addEventListener('DOMContentLoaded', function () {
        updateOfflineBanner();
        var projectId = getProjectIdFromPage();
        if (projectId) {
            refreshPendingMarkers(projectId);
            if (window.scriptyIsOffline && window.scriptyIsOffline() && window.scriptyApplyPendingBlockEdits) {
                window.scriptyApplyPendingBlockEdits(projectId);
            }
        }
    });
    if (document.readyState !== 'loading') {
        updateOfflineBanner();
    }

    function saveBlockContentInline(blockContent, fields) {
        if (!blockContent || !fields || !fields.id) {
            return Promise.resolve();
        }

        var ctx = getBlockEditContext(blockContent);
        ctx.blockId = String(fields.id);
        ctx.content = fields.content == null ? '' : String(fields.content);
        ctx.personId = fields.personId == null ? '' : String(fields.personId);
        ctx.tags = fields.tags == null ? '' : String(fields.tags);
        if (!ctx.personId) {
            ctx.characterHtml = '';
        }

        var projectId = getProjectIdFromPage();
        var work = Promise.resolve();

        if (window.scriptyOfflineStore && projectId && !isTempBlockId(ctx.blockId)) {
            work = window.scriptyOfflineStore.clearPendingEditsForBlock(ctx.blockId)
                .then(function () {
                    return window.scriptyOfflineStore.enqueueBlockEdit({
                        projectId: projectId,
                        blockId: Number(ctx.blockId),
                        payload: {
                            id: ctx.blockId,
                            content: ctx.content,
                            personId: ctx.personId || '',
                            tags: ctx.tags || ''
                        }
                    });
                });
        } else if (window.scriptyOfflineStore && projectId && isTempBlockId(ctx.blockId)) {
            work = window.scriptyOfflineStore.updatePendingCreateContent(ctx.blockId, ctx.content);
        }

        return work.then(function () {
            blockContent.innerHTML = renderShowInline(ctx);
            processNodes([blockContent]);
            scheduleSnapshot();
            flashSavedLocally();
            return refreshPendingMarkers(projectId);
        }).catch(function () {
            /* keep current DOM if local save fails */
        });
    }

    window.scriptySaveBlockContentInline = saveBlockContentInline;
    window.scriptySaveBlockEditOffline = saveBlockEditOffline;
    window.scriptyRevertBlockEditOffline = revertBlockEditOffline;
    window.scriptyOpenBlockEditOffline = openBlockEditOffline;
    window.scriptyOpenCreateBelowOffline = openCreateBelowOffline;
    window.scriptySaveCreateBelowOffline = saveCreateBelowOffline;
    window.scriptySyncPendingEdits = syncPendingEdits;
    window.scriptyUpdateOfflineEditBanner = updateOfflineBanner;
    window.scriptyRenderBlockShowInline = renderShowInline;
    window.scriptyGetBlockEditContext = getBlockEditContext;
    window.scriptyRefreshOfflinePendingMarkers = refreshPendingMarkers;
    window.scriptyScheduleOfflineSnapshot = scheduleSnapshot;
}());
