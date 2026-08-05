/**
 * Recordings kept with a song.
 *
 * The panel is markup with nothing in it until somebody unfolds it; from there
 * this reads /api/song/audio — the HAL surface the iPhone app follows — and
 * draws a row per take. Doing it this way rather than server-rendering the list
 * means one description of what a recording is, and it means an editor page
 * that never opens the panel transfers no audio at all.
 *
 * What may be done to a recording is decided by the links the server put on it,
 * not by a flag from this page: a row grows a Rename and a Delete only when
 * `renameAudio` and `deleteAudio` came back with it, so a collaborator with
 * view-only access hears the demo and cannot touch it. CSRF tokens ride along
 * by themselves — csrf.js patches window.fetch.
 */
(function () {
    'use strict';

    var API = '/api/song/audio';

    /** The server namespaces its own relations; `audioFile` arrives as `scripty:audioFile`. */
    function link(resource, rel) {
        var links = resource && resource._links;
        if (!links) {
            return null;
        }
        var found = links[rel] || links['scripty:' + rel];
        return found && found.href ? found.href : null;
    }

    function embedded(document_) {
        var body = document_ && document_._embedded;
        if (!body) {
            return [];
        }
        // Key-agnostic, like the app's own HAL reader: the collection relation
        // is `audioRecordings`, curied or not, and one key is all there is.
        var keys = Object.keys(body);
        for (var i = 0; i < keys.length; i++) {
            if (Array.isArray(body[keys[i]])) {
                return body[keys[i]];
            }
        }
        return [];
    }

    function json(url, options) {
        return fetch(url, Object.assign({
            credentials: 'same-origin',
            headers: { 'Accept': 'application/hal+json' }
        }, options || {})).then(function (res) {
            if (!res.ok) {
                return res.json().catch(function () { return {}; }).then(function (body) {
                    var message = body && (body.file || body.title || body.error);
                    return Promise.reject(new Error(message || 'That did not work. Try again.'));
                });
            }
            return res.status === 204 ? null : res.json();
        });
    }

    /** mm:ss, and h:mm:ss for the take that turned into a jam. */
    function formatDuration(ms) {
        if (!ms || ms <= 0) {
            return '';
        }
        var total = Math.round(ms / 1000);
        var seconds = total % 60;
        var minutes = Math.floor(total / 60) % 60;
        var hours = Math.floor(total / 3600);
        var pad = function (n) { return n < 10 ? '0' + n : String(n); };
        return hours > 0
            ? hours + ':' + pad(minutes) + ':' + pad(seconds)
            : minutes + ':' + pad(seconds);
    }

    function formatSize(bytes) {
        if (!bytes || bytes <= 0) {
            return '';
        }
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024)) + ' KB';
        }
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    /** The line under a take's name: how long it plays, how big it is. */
    function describe(audio) {
        return [formatDuration(audio.durationMs), formatSize(audio.byteSize)]
            .filter(Boolean)
            .join(' · ');
    }

    function show(element, visible) {
        if (element) {
            element.hidden = !visible;
        }
    }

    function fail(panel, error) {
        var box = panel.querySelector('[data-song-audio-error]');
        if (!box) {
            return;
        }
        box.textContent = error && error.message ? error.message : String(error);
        show(box, true);
    }

    function clearError(panel) {
        show(panel.querySelector('[data-song-audio-error]'), false);
    }

    function row(panel, audio) {
        var item = document.createElement('li');
        item.className = 'song-audio-item';

        var head = document.createElement('div');
        head.className = 'song-audio-item-head';

        var name = document.createElement('span');
        name.className = 'song-audio-title';
        name.textContent = audio.title || audio.fileName || 'Recording';
        head.appendChild(name);

        var meta = describe(audio);
        if (meta) {
            var metaEl = document.createElement('span');
            metaEl.className = 'song-audio-meta muted';
            metaEl.textContent = meta;
            head.appendChild(metaEl);
        }
        item.appendChild(head);

        var href = link(audio, 'audioFile');
        if (href) {
            var player = document.createElement('audio');
            player.className = 'song-audio-player';
            player.controls = true;
            player.preload = 'none';
            player.src = href;
            item.appendChild(player);
        }

        var actions = document.createElement('div');
        actions.className = 'song-audio-item-actions';

        if (href) {
            var download = document.createElement('a');
            download.className = 'song-audio-btn';
            download.href = href;
            download.download = audio.fileName || 'recording';
            download.textContent = 'Download';
            actions.appendChild(download);
        }

        var renameHref = link(audio, 'renameAudio');
        if (renameHref) {
            var rename = document.createElement('button');
            rename.type = 'button';
            rename.className = 'song-audio-btn';
            rename.textContent = 'Rename';
            rename.addEventListener('click', function () {
                var next = window.prompt('Name this recording', audio.title || '');
                if (next === null || !next.trim()) {
                    return;
                }
                clearError(panel);
                json(renameHref, {
                    method: 'PUT',
                    headers: {
                        'Accept': 'application/hal+json',
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ title: next.trim() })
                }).then(function () {
                    load(panel);
                }).catch(function (error) {
                    fail(panel, error);
                });
            });
            actions.appendChild(rename);
        }

        var deleteHref = link(audio, 'deleteAudio');
        if (deleteHref) {
            var remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'song-audio-btn song-audio-btn--danger';
            remove.textContent = 'Delete';
            remove.addEventListener('click', function () {
                // Asked once, because there is no trash for a file: what is
                // deleted here is gone, and nothing else in the application
                // pretends otherwise.
                if (!window.confirm('Delete “' + (audio.title || 'this recording') + '”? This cannot be undone.')) {
                    return;
                }
                clearError(panel);
                json(deleteHref, { method: 'DELETE' }).then(function () {
                    load(panel);
                }).catch(function (error) {
                    fail(panel, error);
                });
            });
            actions.appendChild(remove);
        }

        if (actions.childNodes.length) {
            item.appendChild(actions);
        }
        return item;
    }

    function render(panel, recordings) {
        var list = panel.querySelector('[data-song-audio-list]');
        var count = panel.querySelector('[data-song-audio-count]');
        if (!list) {
            return;
        }
        list.textContent = '';
        recordings.forEach(function (audio) {
            list.appendChild(row(panel, audio));
        });
        show(panel.querySelector('[data-song-audio-empty]'), recordings.length === 0);
        if (count) {
            count.textContent = recordings.length
                ? '(' + recordings.length + ')'
                : '';
        }
    }

    function load(panel) {
        var documentId = panel.getAttribute('data-document-id');
        if (!documentId) {
            return Promise.resolve();
        }
        return json(API + '?documentId=' + encodeURIComponent(documentId))
            .then(function (body) {
                render(panel, embedded(body));
            })
            .catch(function (error) {
                fail(panel, error);
            });
    }

    /**
     * How long the file plays, measured here so the server never has to decode
     * audio. Best effort by design: a format the browser cannot open still
     * uploads, it just arrives without a duration, and every screen already
     * draws a take that has none.
     */
    function measure(file) {
        return new Promise(function (resolve) {
            var url = URL.createObjectURL(file);
            var probe = document.createElement('audio');
            var done = function (value) {
                URL.revokeObjectURL(url);
                resolve(value);
            };
            probe.preload = 'metadata';
            probe.onloadedmetadata = function () {
                var seconds = probe.duration;
                done(isFinite(seconds) && seconds > 0 ? Math.round(seconds * 1000) : null);
            };
            probe.onerror = function () { done(null); };
            window.setTimeout(function () { done(null); }, 5000);
            probe.src = url;
        });
    }

    function upload(panel, file) {
        var documentId = panel.getAttribute('data-document-id');
        var progress = panel.querySelector('[data-song-audio-progress]');
        clearError(panel);
        if (progress) {
            progress.textContent = 'Adding ' + file.name + '…';
            show(progress, true);
        }
        return measure(file).then(function (durationMs) {
            var form = new FormData();
            form.append('file', file);
            form.append('title', file.name.replace(/\.[^.]+$/, ''));
            if (durationMs) {
                form.append('durationMs', String(durationMs));
            }
            return json(API + '?documentId=' + encodeURIComponent(documentId), {
                method: 'POST',
                body: form
            });
        }).then(function () {
            return load(panel);
        }).catch(function (error) {
            fail(panel, error);
        }).then(function () {
            show(progress, false);
        });
    }

    function wire(panel) {
        if (panel.__songAudioWired) {
            return;
        }
        panel.__songAudioWired = true;

        var details = panel.querySelector('.song-audio-details');
        if (details) {
            details.addEventListener('toggle', function () {
                if (details.open && !panel.__songAudioLoaded) {
                    panel.__songAudioLoaded = true;
                    load(panel);
                }
            });
        }

        var input = panel.querySelector('[data-song-audio-file]');
        if (input) {
            input.addEventListener('change', function () {
                var files = Array.prototype.slice.call(input.files || []);
                // Cleared before the upload runs, so choosing the same file
                // twice in a row still fires a change event the second time.
                input.value = '';
                files.reduce(function (queue, file) {
                    return queue.then(function () { return upload(panel, file); });
                }, Promise.resolve());
            });
        }
    }

    function wireAll(root) {
        (root || document).querySelectorAll('.song-audio').forEach(wire);
    }

    document.addEventListener('DOMContentLoaded', function () { wireAll(document); });
    document.body.addEventListener('htmx:afterSwap', function (event) {
        wireAll(event.target || document);
    });
})();
