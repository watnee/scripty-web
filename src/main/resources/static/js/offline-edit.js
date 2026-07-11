(function () {
    'use strict';

    var syncing = false;
    var syncFailed = false;
    var snapshotTimer = null;
    var syncRetryTimer = null;
    var MAX_SYNC_ATTEMPTS = 5;

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

    function renderBlockTextHtml(content, blockType) {
        var text = escText(content || '');
        var body = text || '&#160;';
        if (blockType === 'SCENE') {
            return '<h2 class="script-block-text scene-header">' + body + '</h2>';
        }
        if (blockType === 'SECTION') {
            return '<h3 class="script-block-text">' + body + '</h3>';
        }
        return '<p class="script-block-text">' + body + '</p>';
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
        var blockType = row ? (row.getAttribute('data-block-type') || '') : '';
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
            blockType: blockType,
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
            renderMirrorTextHtml(ctx.content) +
            '<form class="hide-in-reader-view sidebar menu block-inline-edit-form" hx-post="/block/editInline" ' +
            'hx-target="closest .block-content" hx-swap="innerHTML" ' +
            'hx-trigger="change from:find select, focusout[target.value.trim()!=\'\'] from:find textarea">' +
            '<input type="hidden" name="id" value="' + escAttr(ctx.blockId) + '" />' +
            '<input type="hidden" name="personId" id="block-person-id-' + escAttr(ctx.blockId) + '" value="' + escAttr(ctx.personId) + '" />' +
            '<input type="hidden" name="tags" value="' + escAttr(ctx.tags) + '" />' +
            '<textarea spellcheck="' + spellcheckAttr() + '" autocomplete="off" autocorrect="off" autocapitalize="off" rows="1" class="script-block-text block-input-textarea" name="content" ' +
            'onkeydown="' + enterHandler + '">' + escText(ctx.content) + '</textarea>' +
            renderTagsHtml(ctx.tags) +
            '</form>';
    }

    function renderShowInline(ctx) {
        return (ctx.characterHtml || '') +
            renderBlockTextHtml(ctx.content, ctx.blockType) +
            renderTagsHtml(ctx.tags);
    }

    function renderCreateBelowMenuHtml() {
        return '<div class="nav-dropdown create-below-menu-dropdown">' +
            '<button type="button" class="create-below create-below-menu-toggle nav-dropdown-toggle" ' +
            'aria-haspopup="true" aria-expanded="false" title="Add block below" aria-label="Add block below">+</button>' +
            '<div class="nav-dropdown-menu create-below-menu" role="menu" aria-label="Choose block type">' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="SCENE">Scene</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="ACTION">Action</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="TEXT">Text</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="CHARACTER">Character</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="DIALOGUE">Dialogue</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="DUAL_DIALOGUE">Dual</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="PARENTHETICAL">(Paren)</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="TRANSITION">Transition</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="SHOT">Shot</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="LYRICS">Lyrics</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="CENTERED">Centered</button>' +
            '<hr class="nav-dropdown-divider" />' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="SECTION">Section</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="SYNOPSIS">Synopsis</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="NOTE">Note</button>' +
            '<button type="button" class="nav-dropdown-item" role="menuitem" data-create-type="PAGE_BREAK">Page Break</button>' +
            '<div class="create-below-songs-section" hidden>' +
            '<hr class="nav-dropdown-divider" />' +
            '<div class="create-below-songs-heading" role="presentation">Songs</div>' +
            '<div class="create-below-songs-list" role="group" aria-label="Insert song"></div>' +
            '</div>' +
            '<div class="create-below-drafts-section" hidden>' +
            '<hr class="nav-dropdown-divider" />' +
            '<div class="create-below-drafts-heading" role="presentation">Notes</div>' +
            '<div class="create-below-drafts-list" role="group" aria-label="Insert note"></div>' +
            '</div>' +
            '</div></div>';
    }

    function renderCreateBelowRow(anchorBlockId) {
        return '<div class="block-row" data-block-type="ACTION">' +
            '<span class="block-element-label hide-in-reader-view sidebar menu" data-block-type="ACTION" title="Fountain element type">Action</span>' +
            '<aside class="block-left-controls hide-in-reader-view sidebar menu">' +
            '<div class="block-left-controls-inner">' +
            '<input type="checkbox" class="block-select-checkbox" title="Selection available after saving block" aria-label="Selection available after saving block" />' +
            renderCreateBelowMenuHtml() +
            '</div></aside>' +
            '<div class="block-content script-block block-type-action script-block--action">' +
            '<form hx-post="/block/createBelowInline" hx-target="closest .block-row" hx-swap="outerHTML">' +
            '<input type="hidden" name="id" value="' + escAttr(anchorBlockId) + '" />' +
            '<input type="hidden" name="surface" value="project" />' +
            '<input type="hidden" name="type" value="ACTION" />' +
            '<textarea spellcheck="' + spellcheckAttr() + '" autocomplete="off" autocorrect="off" autocapitalize="off" rows="1" class="script-block-text block-input-textarea" name="content" autofocus ' +
            'onkeydown="if(event.key===\'Enter\'&&!event.shiftKey){event.preventDefault();event.stopPropagation;' +
            'if(window.scriptyCreateBlockFromCreateRow){window.scriptyCreateBlockFromCreateRow(this);}return false;}"></textarea>' +
            '</form></div>' +
            '<aside class="block-actions hide-in-reader-view sidebar menu"></aside>' +
            '</div>';
    }

    function renderCreateInlineRow(projectId) {
        return '<div class="block-row" data-block-type="ACTION">' +
            '<span class="block-element-label hide-in-reader-view sidebar menu" data-block-type="ACTION" title="Fountain element type">Action</span>' +
            '<aside class="block-left-controls hide-in-reader-view sidebar menu">' +
            '<div class="block-left-controls-inner">' +
            '<input type="checkbox" class="block-select-checkbox" title="Selection available after saving block" aria-label="Selection available after saving block" />' +
            renderCreateBelowMenuHtml() +
            '</div></aside>' +
            '<div class="block-content script-block block-type-action script-block--action">' +
            '<form hx-post="/block/createInline?surface=project" hx-target="closest .block-row" hx-swap="outerHTML">' +
            '<input type="hidden" name="projectId" value="' + escAttr(projectId) + '" />' +
            '<input type="hidden" name="surface" value="project" />' +
            '<input type="hidden" name="type" value="ACTION" />' +
            '<textarea spellcheck="' + spellcheckAttr() + '" autocomplete="off" autocorrect="off" autocapitalize="off" rows="1" class="script-block-text block-input-textarea" name="content" autofocus ' +
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
            renderCreateBelowMenuHtml() +
            '</div></aside>' +
            '<div class="block-content script-block block-type-action script-block--action" ' +
            'hx-get="/block/editInline?id=' + escAttr(opts.tempBlockId) + '" ' +
            'hx-trigger="click[!event.target.closest(\'a\')&amp;&amp;!event.target.closest(\'form\')&amp;&amp;!window.scriptySuppressBlockEditClick&amp;&amp;!window.scriptyBlockEditLocked]" hx-swap="innerHTML">' +
            contentHtml +
            '</div>' +
            '<aside class="block-actions hide-in-reader-view sidebar menu"></aside>' +
            '</div>';
    }

    function spellcheckAttr() {
        var enabled = typeof window.scriptyIsSpellcheckEnabled === 'function'
            ? window.scriptyIsSpellcheckEnabled()
            : localStorage.getItem('spellcheck') !== 'false';
        return enabled ? 'true' : 'false';
    }

    function applySpellcheckPreference(root) {
        if (!root) return;
        var enabled = typeof window.scriptyIsSpellcheckEnabled === 'function'
            ? window.scriptyIsSpellcheckEnabled()
            : localStorage.getItem('spellcheck') !== 'false';
        var applyOne = window.scriptyApplySpellcheck;
        var fields = root.querySelectorAll
            ? root.querySelectorAll('textarea, input[type="text"]')
            : [];
        Array.prototype.forEach.call(fields, function (el) {
            if (typeof applyOne === 'function') {
                applyOne(el, enabled);
            } else {
                el.setAttribute('spellcheck', enabled ? 'true' : 'false');
                el.spellcheck = !!enabled;
            }
        });
        if (root.matches && root.matches('textarea, input[type="text"]')) {
            if (typeof applyOne === 'function') {
                applyOne(root, enabled);
            } else {
                root.setAttribute('spellcheck', enabled ? 'true' : 'false');
                root.spellcheck = !!enabled;
            }
        }
    }

    function processNodes(nodes) {
        if (!nodes || !nodes.length) return;
        nodes.forEach(function (node) {
            if (typeof htmx !== 'undefined') {
                htmx.process(node);
            }
            applySpellcheckPreference(node);
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

    function showBanner() {
        var els = getBannerElements();
        if (els.banner) els.banner.hidden = false;
    }

    function hideBannerIfOnline() {
        var els = getBannerElements();
        if (!els.banner) return;
        if (isEffectivelyOnline() && !syncing && !syncFailed) {
            els.banner.hidden = true;
            els.banner.classList.remove('scripty-offline-banner--error', 'scripty-offline-banner--syncing');
        }
    }

    function updateOfflineBanner() {
        if (syncing) return;

        var projectId = getProjectIdFromPage();
        var offline = window.scriptyIsOffline && window.scriptyIsOffline();

        if (!offline) {
            if (syncFailed && window.scriptyOfflineStore) {
                showBanner();
                window.scriptyOfflineStore.countPendingEdits(projectId).then(function (count) {
                    if (count > 0) {
                        setBannerMessage('Some edits are still syncing. Tap retry if this persists.', {
                            error: true,
                            showRetry: true
                        });
                    } else {
                        syncFailed = false;
                        hideBannerIfOnline();
                    }
                }).catch(function () {
                    hideBannerIfOnline();
                });
                return;
            }
            hideBannerIfOnline();
            return;
        }

        showBanner();
        var baseText = navigator.onLine
            ? "Can't reach Scripty right now — edits save locally and sync when it returns."
            : "You're offline — edits save locally and sync when you're back online.";
        if (!window.scriptyOfflineStore || !projectId) {
            setBannerMessage(baseText);
            return;
        }

        window.scriptyOfflineStore.countPendingEdits(projectId).then(function (count) {
            if (syncFailed) {
                setBannerMessage('Some edits are still syncing. Tap retry if this persists.', { error: true, showRetry: true });
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

    function openBlockEditOffline(blockContent, caretOffset, caretEndOffset) {
        if (!blockContent) return null;
        var ctx = getBlockEditContext(blockContent);
        if (!ctx.blockId) return null;
        blockContent.dataset.originalContent = ctx.content;
        blockContent.innerHTML = renderEditForm(ctx);
        processNodes([blockContent]);
        var textarea = blockContent.querySelector('textarea[name="content"]');
        if (textarea) {
            var start = caretOffset;
            var end = caretEndOffset;
            if (window.scriptyPendingBlockCaret && window.scriptyPendingBlockCaret.blockContent === blockContent) {
                if (start == null) start = window.scriptyPendingBlockCaret.offset;
                if (end == null) end = window.scriptyPendingBlockCaret.endOffset;
                window.scriptyPendingBlockCaret = null;
            }
            if (start != null) {
                var pos = Math.max(0, Math.min(start, textarea.value.length));
                var posEnd = Math.max(pos, Math.min(end != null ? end : start, textarea.value.length));
                textarea.focus({ preventScroll: true });
                textarea.setSelectionRange(pos, posEnd);
            } else {
                focusTextareaEnd(textarea);
            }
            textarea.dispatchEvent(new Event('input', { bubbles: true }));

            if (window.scriptyOpenSpellcheckSuggestions && window._pendingSpellcheckClick) {
                var pendingClick = window._pendingSpellcheckClick;
                window._pendingSpellcheckClick = null;
                requestAnimationFrame(function() {
                    window.scriptyOpenSpellcheckSuggestions(textarea, pendingClick);
                });
            }
        }
        return textarea;
    }

    function openCreateBelowOffline(row) {
        if (!row || !row.hasAttribute('data-block-id')) return null;
        var anchorId = row.getAttribute('data-block-id');
        var nextRow = row.nextElementSibling;
        if (nextRow && nextRow.classList.contains('block-row') && !nextRow.hasAttribute('data-block-id')) {
            if (window.scriptyPendingCreateType && window.scriptySetCreateRowType) {
                window.scriptySetCreateRowType(nextRow, window.scriptyPendingCreateType);
                window.scriptyPendingCreateType = null;
            }
            focusTextareaEnd(nextRow.querySelector('textarea[name="content"]'));
            return nextRow;
        }
        var template = document.createElement('template');
        template.innerHTML = renderCreateBelowRow(anchorId).trim();
        var createRow = template.content.firstElementChild;
        if (!createRow || !row.parentNode) return null;
        row.insertAdjacentElement('afterend', createRow);
        processNodes([createRow]);
        if (window.scriptyPendingCreateType && window.scriptySetCreateRowType) {
            window.scriptySetCreateRowType(createRow, window.scriptyPendingCreateType);
            createRow.dataset.scriptyTypedCreate = '1';
            window.scriptyPendingCreateType = null;
        } else if (window.scriptyApplyPendingCreateType) {
            window.scriptyApplyPendingCreateType(createRow);
        }
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

    function isEffectivelyOnline() {
        if (window.scriptyIsOffline) {
            return !window.scriptyIsOffline();
        }
        return navigator.onLine;
    }

    function scheduleSyncRetry(delayMs) {
        if (syncRetryTimer) clearTimeout(syncRetryTimer);
        syncRetryTimer = setTimeout(function () {
            syncRetryTimer = null;
            syncPendingEdits();
        }, delayMs || 4000);
    }

    function registerBackgroundSync() {
        if (!('serviceWorker' in navigator) || !('SyncManager' in window)) return;
        navigator.serviceWorker.ready.then(function (reg) {
            if (!reg.sync) return;
            return reg.sync.register('scripty-sync-outbox');
        }).catch(function () {});
    }

    async function syncOneOperation(op, tempIdMap) {
        if (op.type === 'blockEdit') {
            var blockId = op.payload.id;
            if (isTempBlockId(blockId)) {
                blockId = resolveAnchorId(blockId, tempIdMap);
                if (isTempBlockId(blockId)) {
                    return { ok: false, retryable: true, error: 'Waiting for related create to sync' };
                }
                op.payload.id = blockId;
            }
            var params = new URLSearchParams(op.payload);
            var response = await fetch('/block/editInline', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                credentials: 'same-origin',
                body: params.toString()
            });
            if (response.ok) return { ok: true };
            return {
                ok: false,
                retryable: response.status >= 500 || response.status === 0 || response.status === 429,
                error: 'HTTP ' + response.status
            };
        }

        if (op.type === 'blockCreateBelow') {
            var anchorId = resolveAnchorId(op.anchorBlockId || op.payload.id, tempIdMap);
            if (isTempBlockId(anchorId)) {
                return { ok: false, retryable: true, error: 'Waiting for related create to sync' };
            }
            var createParams = new URLSearchParams(op.payload);
            createParams.set('id', anchorId);
            var createResponse = await fetch('/block/createBelowInline', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                credentials: 'same-origin',
                body: createParams.toString()
            });
            if (!createResponse.ok) {
                return {
                    ok: false,
                    retryable: createResponse.status >= 500 || createResponse.status === 0 || createResponse.status === 429,
                    error: 'HTTP ' + createResponse.status
                };
            }
            var html = await createResponse.text();
            var savedId = extractSavedBlockId(html);
            if (savedId && op.tempBlockId) {
                tempIdMap[op.tempBlockId] = savedId;
                var tempRow = document.querySelector('[data-block-id="' + op.tempBlockId + '"]');
                if (tempRow) {
                    replaceRowWithHtml(tempRow, html);
                }
            }
            return { ok: true };
        }

        return { ok: false, retryable: false, error: 'Unknown operation' };
    }

    async function syncPendingEdits() {
        if (syncing || !isEffectivelyOnline() || !window.scriptyOfflineStore) return;
        syncing = true;
        syncFailed = false;

        try {
            // Don't await a long cold-start probe before syncing. If we already
            // believe we're online, try the queue; network errors retry later.
            // Only abort when the dedicated probe has already marked us offline.
            if (window.scriptyIsOffline && window.scriptyIsOffline()) {
                syncFailed = true;
                setBannerMessage('Still reconnecting to Scripty — will retry automatically.', {
                    error: true,
                    showRetry: true
                });
                return;
            }

            var ops = await window.scriptyOfflineStore.listPendingOperations();
            if (ops.length === 0) {
                updateOfflineBanner();
                return;
            }

            showBanner();
            setBannerMessage('Back online — syncing ' + ops.length + ' change' + (ops.length === 1 ? '' : 's') + '…', { syncing: true });

            var tempIdMap = {};
            var synced = 0;
            var failedCount = 0;
            var shouldRetryLater = false;

            for (var i = 0; i < ops.length; i++) {
                if (!isEffectivelyOnline()) {
                    shouldRetryLater = true;
                    break;
                }
                var op = ops[i];
                var result;
                try {
                    result = await syncOneOperation(op, tempIdMap);
                } catch (err) {
                    result = { ok: false, retryable: true, error: (err && err.message) || 'Network error' };
                }

                if (result.ok) {
                    if (window.scriptyReportServerReachable) {
                        window.scriptyReportServerReachable(true);
                    }
                    await window.scriptyOfflineStore.removePendingOperation(op.id);
                    synced += 1;
                    continue;
                }

                failedCount += 1;
                var attempts = (op.attempts || 0) + 1;
                if (window.scriptyOfflineStore.updateOperation) {
                    await window.scriptyOfflineStore.updateOperation(op.id, {
                        attempts: attempts,
                        lastError: result.error || 'Sync failed',
                        lastAttemptAt: Date.now()
                    });
                }

                // Hard failures (auth/validation) stop the queue so order is preserved.
                if (!result.retryable || attempts >= MAX_SYNC_ATTEMPTS) {
                    syncFailed = true;
                    break;
                }

                // Soft/transient failure: keep later ops for a later pass.
                shouldRetryLater = true;
                syncFailed = true;
                break;
            }

            var projectId = getProjectIdFromPage();
            await refreshPendingMarkers(projectId);

            if (failedCount === 0 && !shouldRetryLater) {
                syncFailed = false;
                if (window.scriptySaveProjectSnapshot) {
                    await window.scriptySaveProjectSnapshot();
                }
                showBanner();
                setBannerMessage('All offline changes synced.');
                setTimeout(function () {
                    hideBannerIfOnline();
                    updateOfflineBanner();
                }, 1500);
            } else {
                showBanner();
                var remaining = await window.scriptyOfflineStore.countPendingEdits(projectId);
                setBannerMessage(
                    'Synced ' + synced + ' change' + (synced === 1 ? '' : 's') +
                    (remaining ? '; ' + remaining + ' still pending.' : '.') +
                    ' Retrying…',
                    { error: true, showRetry: true }
                );
                if (shouldRetryLater) {
                    scheduleSyncRetry(Math.min(30000, 2000 * Math.pow(2, Math.min(synced + failedCount, 4))));
                    registerBackgroundSync();
                }
            }
        } catch (err) {
            syncFailed = true;
            setBannerMessage('Sync paused — retrying shortly.', { error: true, showRetry: true });
            scheduleSyncRetry(8000);
            registerBackgroundSync();
        } finally {
            syncing = false;
        }
    }

    function isOfflineBlockPath(path) {
        return path.indexOf('/block/editInline') !== -1 ||
            path.indexOf('/block/createBelowInline') !== -1;
    }

    document.body.addEventListener('htmx:beforeRequest', function (e) {
        if (isEffectivelyOnline()) return;
        var path = (e.detail.pathInfo && e.detail.pathInfo.requestPath) || '';
        if (!isOfflineBlockPath(path)) return;

        e.preventDefault();

        if (path.indexOf('/block/editInline') !== -1 && e.detail.verb === 'get') {
            var start = null, end = null;
            if (window.scriptyPendingBlockCaret && window.scriptyPendingBlockCaret.blockContent === e.detail.elt) {
                start = window.scriptyPendingBlockCaret.offset;
                end = window.scriptyPendingBlockCaret.endOffset;
            }
            openBlockEditOffline(e.detail.elt, start, end);
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

        if (isEffectivelyOnline()) return;

        var typeItem = e.target.closest('[data-create-type]');
        if (typeItem && typeItem.closest('.create-below-menu')) {
            e.preventDefault();
            e.stopPropagation();
            var typeRow = typeItem.closest('.block-row');
            var type = typeItem.getAttribute('data-create-type');
            if (!typeRow || !type) return;
            document.querySelectorAll('.create-below-menu-dropdown.open').forEach(function(dropdown) {
                dropdown.classList.remove('open');
                var toggle = dropdown.querySelector('.create-below-menu-toggle');
                if (toggle) toggle.setAttribute('aria-expanded', 'false');
            });
            if (window.scriptySetCreateRowType) {
                window.scriptySetCreateRowType(typeRow, type);
            }
            return;
        }

        var plusBtn = e.target.closest('.create-below-menu-toggle, a.create-below');
        if (!plusBtn || plusBtn.closest('.create-below-menu')) return;
        var row = plusBtn.closest('.block-row[data-block-id], .block-row');
        if (!row) return;
        e.preventDefault();
        e.stopPropagation();
        var fromType = row.getAttribute('data-block-type') || 'ACTION';
        window.scriptyPendingCreateType = window.scriptyNextFountainType
            ? window.scriptyNextFountainType(fromType)
            : 'ACTION';
        var created = null;
        if (row.hasAttribute('data-block-id')) {
            created = openCreateBelowOffline(row);
        } else if (window.scriptySetCreateRowType) {
            window.scriptySetCreateRowType(row, window.scriptyPendingCreateType);
            window.scriptyPendingCreateType = null;
            created = row;
        }
        if (created) {
            created.classList.add('block-controls-revealed');
            var dropdown = created.querySelector('.create-below-menu-dropdown');
            var toggle = created.querySelector('.create-below-menu-toggle');
            if (dropdown && toggle) {
                dropdown.classList.add('open');
                toggle.setAttribute('aria-expanded', 'true');
            }
        }
    });

    document.addEventListener('visibilitychange', function () {
        if (!document.hidden && isEffectivelyOnline()) {
            syncPendingEdits();
        }
    });

    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.addEventListener('message', function (event) {
            if (event.data && event.data.type === 'SCRIPTY_SYNC_OUTBOX') {
                syncPendingEdits();
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        updateOfflineBanner();
        var projectId = getProjectIdFromPage();
        if (projectId) {
            refreshPendingMarkers(projectId);
            if (window.scriptyIsOffline && window.scriptyIsOffline() && window.scriptyApplyPendingBlockEdits) {
                window.scriptyApplyPendingBlockEdits(projectId);
            } else if (isEffectivelyOnline()) {
                syncPendingEdits();
            }
        }
        registerBackgroundSync();
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
    window.scriptyRenderCreateInlineRow = renderCreateInlineRow;
    window.scriptySyncPendingEdits = syncPendingEdits;
    window.scriptyUpdateOfflineEditBanner = updateOfflineBanner;
    window.scriptyRenderBlockShowInline = renderShowInline;
    window.scriptyRenderOptimisticBlockRow = renderOptimisticBlockRow;
    window.scriptyGetBlockEditContext = getBlockEditContext;
    window.scriptyRefreshOfflinePendingMarkers = refreshPendingMarkers;
    window.scriptyScheduleOfflineSnapshot = scheduleSnapshot;
}());
