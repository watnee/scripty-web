/**
 * Read aloud: a screenplay, a song or a note, said by the browser's voice.
 *
 * Two halves, as on the Apple client. The first decides *what* is said and in
 * what order — a script on the page is laid out for the eye, and read back
 * literally it comes out wrong: a synthesizer spells "INT." a letter at a time,
 * says every character name twice, and runs the action into the dialogue with
 * no seam. So the document becomes an ordered run of cues, each with the text
 * as it should be *said*, whose line it is, and what kind of thing it is —
 * which is what decides the silence in front of it. That half is plain text in,
 * values out. The second half is the transport: one run at a time, a bar at the
 * foot of the window, and the element being read lit up on the page.
 *
 * There is one reading on the device, whichever surface started it, because
 * there is one voice in the room and one set of headphone buttons. Starting a
 * reading anywhere ends whatever was being read before it.
 *
 * Voice, speed and what to include are the writer's preferences rather than the
 * document's — a reading voice is picked once, like type size — so they live in
 * localStorage and outlive the page.
 *
 * Loaded from nav.html so it survives HTMX-boosted navigation, and so the
 * screenplay, the song editor and the note editor all reach the same narrator.
 */
(function () {
    'use strict';

    if (window._scriptyReadAloudInit) return;
    window._scriptyReadAloudInit = true;

    var synth = window.speechSynthesis;
    if (!synth || typeof window.SpeechSynthesisUtterance !== 'function') {
        // No voice on this browser. The menu entries hide themselves against
        // this class rather than offering a control that can do nothing.
        document.documentElement.classList.add('scripty-no-read-aloud');
        return;
    }

    // ---------------------------------------------------------------- rules

    /** Lowercase in any alphabet, for spotting a line written in shouting case. */
    var LOWERCASE = (function () {
        try {
            return new RegExp('\\p{Ll}', 'u');
        } catch (e) {
            return /[a-z]/;
        }
    })();

    /** The Fountain force markers, as characters that can open a line. */
    var MARKERS = '.@>~#=*_ ';

    /**
     * Longest first: "int./ext." has to be taken before "int.".
     *
     * Each expansion keeps a comma where the abbreviation's full stop was. The
     * stop is what makes a slug line read as "interior. house." rather than one
     * long noun phrase. The bare "int" and "ext" are here because plenty of
     * writers leave the stop off, and "int hallway" comes back as a word rather
     * than a room.
     */
    var SCENE_ABBREVIATIONS = [
        ['int./ext.', 'interior, exterior,'],
        ['ext./int.', 'exterior, interior,'],
        ['int/ext.', 'interior, exterior,'],
        ['i/e.', 'interior, exterior,'],
        ['int.', 'interior,'],
        ['ext.', 'exterior,'],
        ['est.', 'establishing,'],
        ['int', 'interior,'],
        ['ext', 'exterior,'],
        ['p.o.v.', 'point of view'],
        ['pov', 'point of view']
    ];

    /** The extensions that ride along with a cue, and the two inside dialogue. */
    var CUE_ABBREVIATIONS = [
        ['v.o.', 'voice over'],
        ['o.s.', 'off screen'],
        ['o.c.', 'off camera'],
        ["cont'd", 'continued'],
        ['cont’d', 'continued']
    ];

    var CUE_TEXT_TYPES = {
        CHARACTER: true, DUAL_DIALOGUE: true, DIALOGUE: true, LYRICS: true
    };

    /**
     * The silence in front of a cue, in seconds. A new scene wants air around
     * it; a line following its own character cue wants almost none.
     */
    var PAUSE = {
        heading: 0.7,
        description: 0.35,
        cue: 0.4,
        speech: 0.1,
        direction: 0.2
    };

    /** The air a blank line asks for, between one verse or paragraph and the next. */
    var BREAK_PAUSE = PAUSE.heading;

    function escapeRe(text) {
        return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    /**
     * Expands abbreviations, but only where the abbreviation is a whole word.
     *
     * A plain substring replacement reaches inside words: "est." lives in
     * "WEST.", "int." in "SPRINT.", so "WEST. HOUSE" came back as
     * "westablishing, house". The character before is matched and put back
     * rather than looked behind, so this works in browsers without lookbehind.
     * Only the front of an abbreviation ending in a stop is anchored — the stop
     * is itself a boundary, and requiring another after it would leave
     * "INT.HOUSE", typed in a hurry, unexpanded.
     */
    function expand(text, rules) {
        return rules.reduce(function (partial, rule) {
            var trailing = rule[0].charAt(rule[0].length - 1) === '.' ? '' : "(?![\\w'’])";
            var pattern = new RegExp("(^|[^\\w'’])" + escapeRe(rule[0]) + trailing, 'gi');
            return partial.replace(pattern, function (match, before) {
                return before + rule[1];
            });
        }, text);
    }

    /** A parenthetical's parentheses are how it is set; the words are what is said. */
    function stripParens(text) {
        if (text.length > 2 && text.charAt(0) === '(' && text.charAt(text.length - 1) === ')') {
            return text.slice(1, -1).trim();
        }
        return text;
    }

    /** One element's text, as it should be said rather than as it is written. */
    function spoken(text, type) {
        var result = String(text == null ? '' : text).replace(/\u00a0/g, ' ').trim();
        if (!result) return '';

        if (type === 'PARENTHETICAL') result = stripParens(result);

        // Shouting first: an all-caps line is spelled out a letter at a time,
        // and every heading, cue and transition in a screenplay is shouted.
        if (!LOWERCASE.test(result)) result = result.toLowerCase();

        if (type === 'SCENE') {
            result = expand(result, SCENE_ABBREVIATIONS);
            // The dashes in a slug line are joints, not punctuation to read.
            result = result.split(' -- ').join(', ').split(' - ').join(', ');
        } else if (CUE_TEXT_TYPES[type]) {
            result = expand(result, CUE_ABBREVIATIONS);
        }

        // A leftover force marker would be read as punctuation. Leading only: a
        // trailing full stop is a sentence ending, and the pause at one is
        // worth keeping.
        while (result.length && MARKERS.indexOf(result.charAt(0)) !== -1) {
            result = result.slice(1);
        }
        return result.trim();
    }

    // ------------------------------------------------------------ the run

    function cue(list, elementId, speaker, text, kind, pause) {
        if (!text) return;
        list.push({
            index: list.length,
            elementId: elementId,
            speaker: speaker || null,
            text: text,
            kind: kind,
            pause: pause == null ? PAUSE[kind] : pause
        });
    }

    /**
     * The speaker a character cue names. The cue's own text is the name, but a
     * cue picked from the character list can be empty with the name held on the
     * link instead — the reader makes the same substitution.
     */
    function cueName(block) {
        var content = (block.content || '').trim();
        if (content) return content;
        var person = (block.personName || '').trim();
        return person || null;
    }

    /**
     * The screenplay as an ordered run of cues.
     *
     * Synopses, notes and page breaks are left out for the same reason the
     * reader view drops them: they are the writer's marks on the script, not
     * part of it. Empty elements are skipped too — an utterance of "" is a
     * stall in the middle of the read.
     */
    function scriptCues(blocks, options) {
        var cues = [];
        // Who is speaking, carried forward from the last character cue: the
        // dialogue block itself does not name them.
        var speaker = null;

        blocks.forEach(function (block) {
            var type = block.type || 'ACTION';
            if (type === 'SYNOPSIS' || type === 'NOTE' || type === 'PAGE_BREAK') return;

            if (type === 'CHARACTER' || type === 'DUAL_DIALOGUE') {
                // A cue names the speaker even when it is not itself said, so
                // this runs before the option is consulted.
                var name = cueName(block);
                speaker = name;
                if (!options.announceSpeakers || !name) return;
                cue(cues, block.id, name, spoken(name, type), 'cue');
            } else if (type === 'DIALOGUE' || type === 'LYRICS') {
                cue(cues, block.id, speaker || block.personName || null,
                    spoken(block.content, type), 'speech');
            } else if (type === 'PARENTHETICAL') {
                if (!options.includeDirections) return;
                cue(cues, block.id, speaker, spoken(block.content, type), 'direction');
            } else if (type === 'SCENE' || type === 'SECTION') {
                // A new scene ends whatever speech was running; the next
                // dialogue in it will have its own cue.
                speaker = null;
                if (!options.includeDescription) return;
                cue(cues, block.id, null, spoken(block.content, type), 'heading');
            } else {
                speaker = null;
                if (!options.includeDescription) return;
                cue(cues, block.id, block.personName || null,
                    spoken(block.content, type), 'description');
            }
        });
        return cues;
    }

    /**
     * A lyric as a run of cues, one per line that has words in it.
     *
     * Every line is speech — a song is somebody singing, not a page being
     * described — and none of them names a speaker: a lyric has one voice
     * throughout. A blank line makes no cue of its own; what the verse break
     * becomes is the pause in front of the line after it, which is the beat a
     * singer leaves.
     */
    function lyricCues(lines) {
        var cues = [];
        var afterBreak = false;
        lines.forEach(function (line) {
            var text = spoken(line.text, 'LYRICS');
            if (!text) {
                afterBreak = cues.length > 0;
                return;
            }
            cue(cues, line.id, null, text, 'speech', afterBreak ? BREAK_PAUSE : null);
            afterBreak = false;
        });
        return cues;
    }

    /**
     * A note as a run of cues, one per line that has words in it.
     *
     * A note is prose the writer marked up with the prefixes the formatting
     * toolbar maintains — `#` for a heading, `-` and `1.` for a list. Reading
     * the markers out is exactly what nobody wants: `# Act One` is a heading
     * called "Act One". A blank line is a paragraph break rather than a line
     * with nothing in it, and a marker with no words after it — the empty
     * bullet Return leaves waiting — is neither read nor treated as a break.
     */
    function noteCues(text) {
        var LIST_RE = /^(\s*)([-*]|\d+\.)(\s+)/;
        var HEADING_RE = /^(#{1,6})\s+/;
        var cues = [];
        var atParagraphStart = true;
        var seenAnything = false;

        String(text || '').split(/\r\n|\r|\n/).forEach(function (raw) {
            if (!raw.trim()) {
                if (seenAnything) atParagraphStart = true;
                return;
            }
            var isHeading = false;
            var words;
            var list = LIST_RE.exec(raw);
            if (list) {
                words = raw.slice(list[0].length);
            } else {
                var body = raw.replace(/^\s+/, '');
                var heading = HEADING_RE.exec(body);
                if (heading) {
                    isHeading = true;
                    words = body.slice(heading[0].length);
                } else {
                    words = body;
                }
            }
            var said = spoken(words, 'ACTION');
            if (!said) return;
            // The break belongs to the first line of a paragraph, and a heading
            // already takes that much air on its own.
            var pause = (atParagraphStart && !isHeading && seenAnything) ? BREAK_PAUSE : null;
            cue(cues, cues.length, null, said, isHeading ? 'heading' : 'description', pause);
            atParagraphStart = false;
            seenAnything = true;
        });
        return cues;
    }

    // --------------------------------------------------- what is on screen

    function textOf(node) {
        return node ? String(node.textContent || '').replace(/\u00a0/g, ' ') : '';
    }

    /**
     * A block's words. While a block is open for inline editing the row holds
     * both the rendered text and the textarea being typed into; the textarea is
     * the newer of the two, so it wins.
     */
    function blockText(row) {
        var input = row.querySelector('textarea.block-input-textarea');
        if (input) return input.value;
        return textOf(row.querySelector('.script-block-text'));
    }

    function blockPerson(row) {
        return textOf(row.querySelector('.script-character-name strong')).trim();
    }

    /** A lyric, wherever its lines are: one editor, or one card of a stack. */
    function lyricSource(key, title, editor, prepare) {
        return {
            kind: 'lyric',
            key: key,
            noun: 'Line',
            title: title,
            prepare: prepare,
            read: function () {
                var lines = [];
                editor.querySelectorAll('.song-block-textarea').forEach(function (area) {
                    lines.push({ id: area.getAttribute('data-block-id'), text: area.value });
                });
                return lyricCues(lines);
            },
            element: function (id) {
                return document.getElementById('song-block-' + id);
            }
        };
    }

    /** A note: one textarea, and nothing on screen for a cue to point at. */
    function noteSource(key, title, area, prepare) {
        return {
            kind: 'note',
            key: key,
            noun: 'Line',
            title: title,
            prepare: prepare,
            read: function () { return noteCues(area.value); },
            element: function () { return null; }
        };
    }

    /**
     * A screenplay, from either of the two places it is drawn: the editor's
     * block rows, and the reader page's article — which carries the same type
     * and id on every element for exactly this reason.
     */
    function scriptSource(key, title, root, selector, text) {
        function rows() {
            return root.querySelectorAll(selector + '[data-block-type]');
        }
        return {
            kind: 'script',
            key: key,
            noun: 'Element',
            title: title,
            read: function (options) {
                var blocks = [];
                rows().forEach(function (element) {
                    blocks.push({
                        id: element.getAttribute('data-block-id'),
                        type: element.getAttribute('data-block-type'),
                        content: text(element),
                        personName: blockPerson(element)
                    });
                });
                return scriptCues(blocks, options);
            },
            element: function (id) {
                var found = null;
                rows().forEach(function (element) {
                    if (!found && element.getAttribute('data-block-id') === String(id)) found = element;
                });
                return found;
            }
        };
    }

    /**
     * What the button that was pressed asks to have read, or null.
     *
     * Most pages hold one document and the answer is the page. The two
     * workspaces hold a stack of them, and there the reading belongs to the
     * card whose button was pressed — which is why this takes the trigger
     * rather than looking at the page alone.
     */
    function detectSource(trigger) {
        var card = trigger && trigger.closest ? trigger.closest('.song-workspace-item') : null;
        if (card) return workspaceSource(card);

        var song = document.querySelector('.song-blocks-editor .song-blocks');
        if (song) {
            return lyricSource('song:' + (documentId() || 'open'), documentTitle('Song'), song);
        }

        var note = document.getElementById('text-document-content');
        if (note) {
            return noteSource('note:' + (documentId() || 'open'), documentTitle('Note'), note);
        }

        var script = document.querySelector('.project-script');
        if (script) {
            return scriptSource('script',
                textOf(document.querySelector('.project-header-title')).trim() || 'Screenplay',
                script, '.block-row[data-block-id]', blockText);
        }

        var reader = document.querySelector('.script-reader-page article');
        if (reader) {
            return scriptSource('reader',
                textOf(reader.querySelector('h1')).trim() || 'Screenplay',
                reader, '[data-block-id]', function (element) {
                    var text = element.querySelector('.script-block-text');
                    return textOf(text || element);
                });
        }
        return null;
    }

    /**
     * One card of the songs or notes workspace. A collapsed card is unfolded
     * first: a reading whose highlight is inside something hidden is a voice
     * with nothing to follow.
     */
    function workspaceSource(card) {
        var id = card.getAttribute('data-song-id');
        var title = card.querySelector('.song-workspace-title');
        var name = title ? String(title.value || '').trim() : '';
        var prepare = function () {
            var toggle = card.querySelector('.song-workspace-toggle');
            if (toggle && toggle.getAttribute('aria-expanded') === 'false') toggle.click();
        };

        var note = card.querySelector('.note-workspace-content');
        if (note) return noteSource('note:' + id, name || 'Note', note, prepare);

        var editor = card.querySelector('.song-blocks-editor');
        if (editor) return lyricSource('song:' + id, name || 'Song', editor, prepare);
        return null;
    }

    /** Which song or note the open editor is of, for telling one reading from another. */
    function documentId() {
        var editor = document.querySelector('.song-blocks-editor[data-document-id]');
        if (editor) return editor.getAttribute('data-document-id');
        var field = document.querySelector('#text-document-form input[name="id"]');
        return field ? field.value : null;
    }

    function documentTitle(fallback) {
        var input = document.getElementById('title');
        var value = input ? String(input.value || '').trim() : '';
        return value || fallback;
    }

    // ------------------------------------------------------- preferences

    var PREFS_KEY = 'scripty-read-aloud';
    var SPEEDS = [0.75, 0.9, 1, 1.25, 1.5, 1.75, 2];

    var prefs = {
        voice: null,
        speed: 1,
        announceSpeakers: true,
        includeDescription: true,
        includeDirections: true
    };

    function loadPrefs() {
        var raw;
        try {
            raw = window.localStorage.getItem(PREFS_KEY);
        } catch (e) {
            return;
        }
        if (!raw) return;
        var saved;
        try {
            saved = JSON.parse(raw);
        } catch (e) {
            return;
        }
        if (!saved || typeof saved !== 'object') return;
        if (typeof saved.voice === 'string') prefs.voice = saved.voice;
        if (SPEEDS.indexOf(saved.speed) !== -1) prefs.speed = saved.speed;
        ['announceSpeakers', 'includeDescription', 'includeDirections'].forEach(function (key) {
            if (typeof saved[key] === 'boolean') prefs[key] = saved[key];
        });
    }

    function savePrefs() {
        try {
            window.localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
        } catch (e) { /* private browsing, or a full store */ }
    }

    loadPrefs();

    // ------------------------------------------------------------- voices

    /**
     * The joke voices a Mac has carried since the nineties. A browser gives no
     * way to ask whether a voice is one of them — the Apple client reads a
     * trait the web has no equivalent of — so they are named here. Offered raw,
     * the picker is a wall of names with a robot choir in the middle of it.
     */
    var NOVELTY = [
        'albert', 'bad news', 'bahh', 'bells', 'boing', 'bubbles', 'cellos',
        'deranged', 'good news', 'hysterical', 'jester', 'organ', 'princess',
        'superstar', 'trinoids', 'whisper', 'wobble', 'zarvox'
    ];

    function isNovelty(voice) {
        var name = String(voice.name || '').toLowerCase();
        return NOVELTY.some(function (joke) {
            return name === joke || name.indexOf(joke + ' ') === 0 || name.indexOf('(' + joke) !== -1;
        });
    }

    function pageLanguage() {
        return String(document.documentElement.lang || navigator.language || 'en').toLowerCase();
    }

    /**
     * The voices worth offering, the page's own language first. Sorting rather
     * than filtering by language: a writer working in English on a French
     * machine still has to be able to find the English voice, and the one they
     * pick is remembered.
     */
    function voices() {
        var language = pageLanguage().slice(0, 2);
        var all = (synth.getVoices() || []).filter(function (voice) {
            return !isNovelty(voice);
        });
        return all.sort(function (a, b) {
            var aLang = String(a.lang || '').toLowerCase().slice(0, 2) === language ? 0 : 1;
            var bLang = String(b.lang || '').toLowerCase().slice(0, 2) === language ? 0 : 1;
            if (aLang !== bLang) return aLang - bLang;
            return String(a.name).localeCompare(String(b.name));
        });
    }

    function chosenVoice() {
        var available = voices();
        if (!available.length) return null;
        var wanted = prefs.voice;
        var match = null;
        available.forEach(function (voice) {
            if (!match && (voice.voiceURI === wanted || voice.name === wanted)) match = voice;
        });
        if (match) return match;
        var fallback = null;
        available.forEach(function (voice) {
            if (!fallback && voice['default']) fallback = voice;
        });
        return fallback || available[0];
    }

    // ------------------------------------------------------------- the run

    var state = {
        source: null,
        cues: [],
        index: -1,
        playing: false,
        paused: false,
        // Bumped whenever the run is cancelled or moved, so a callback from an
        // utterance that has been thrown away looks up a token that no longer
        // matches and is ignored.
        token: 0,
        timer: null,
        pendingIndex: null,
        chunks: [],
        chunkIndex: 0
    };

    /**
     * Long text, cut at sentence ends into utterances no browser will cut off
     * on its own. Chrome stops speaking part way through anything much longer
     * than a paragraph; splitting the text is the fix that needs no guess about
     * which browser is running.
     */
    var CHUNK_LIMIT = 180;

    function chunk(text) {
        if (text.length <= CHUNK_LIMIT) return [text];
        var sentences = text.match(/[^.!?…]+[.!?…]*\s*/g) || [text];
        var out = [];
        var current = '';
        sentences.forEach(function (sentence) {
            if (current && (current + sentence).length > CHUNK_LIMIT) {
                out.push(current.trim());
                current = '';
            }
            // A single sentence longer than the limit is broken on a space
            // rather than mid-word.
            while (sentence.length > CHUNK_LIMIT) {
                var cut = sentence.lastIndexOf(' ', CHUNK_LIMIT);
                if (cut <= 0) cut = CHUNK_LIMIT;
                out.push(sentence.slice(0, cut).trim());
                sentence = sentence.slice(cut);
            }
            current += sentence;
        });
        if (current.trim()) out.push(current.trim());
        return out.filter(Boolean);
    }

    function cancelSpeech() {
        state.token++;
        if (state.timer) {
            clearTimeout(state.timer);
            state.timer = null;
        }
        try {
            synth.cancel();
        } catch (e) { /* nothing was speaking */ }
    }

    function rebuild(keepPlace) {
        if (!state.source) return;
        var at = state.index;
        state.cues = state.source.read(prefs) || [];
        state.index = keepPlace ? Math.min(Math.max(at, 0), state.cues.length - 1) : 0;
    }

    function start(source) {
        stop(true);
        state.source = source;
        // A card that has to be unfolded before it can be followed says so here.
        if (source.prepare) source.prepare();
        state.cues = source.read(prefs) || [];
        if (!state.cues.length) {
            showBar();
            announce('Nothing to read');
            return;
        }
        state.playing = true;
        state.paused = false;
        showBar();
        speakFrom(0);
    }

    function speakFrom(index) {
        cancelSpeech();
        if (index >= state.cues.length) {
            finish();
            return;
        }
        state.index = Math.max(0, index);
        var current = state.cues[state.index];
        highlight(current);
        state.chunks = chunk(current.text);
        state.chunkIndex = 0;
        render();

        var token = state.token;
        state.timer = setTimeout(function () {
            state.timer = null;
            if (token !== state.token) return;
            speakChunk(token);
        }, Math.round(current.pause * 1000));
    }

    function speakChunk(token) {
        if (token !== state.token) return;
        if (state.chunkIndex >= state.chunks.length) {
            speakFrom(state.index + 1);
            return;
        }
        var utterance = new window.SpeechSynthesisUtterance(state.chunks[state.chunkIndex]);
        var voice = chosenVoice();
        if (voice) {
            utterance.voice = voice;
            utterance.lang = voice.lang;
        }
        // Unlike the Apple synthesizer's 0…1 dial, `rate` here is a plain
        // multiplier, so the writer's "1.5×" is the number itself.
        utterance.rate = prefs.speed;
        utterance.onend = function () {
            if (token !== state.token) return;
            state.chunkIndex++;
            speakChunk(token);
        };
        utterance.onerror = function (event) {
            if (token !== state.token) return;
            var reason = event && event.error;
            // Cancelling is how stepping and stopping work; only a real failure
            // should skip the rest of the line.
            if (reason === 'interrupted' || reason === 'canceled') return;
            state.chunkIndex++;
            speakChunk(token);
        };
        synth.speak(utterance);
    }

    function finish() {
        stop(false);
        announce('Finished');
    }

    function stop(quiet) {
        cancelSpeech();
        state.playing = false;
        state.paused = false;
        state.pendingIndex = null;
        state.index = -1;
        clearHighlight();
        if (!quiet) render();
    }

    function pause() {
        if (!state.playing || state.paused) return;
        state.paused = true;
        if (state.timer) {
            // Paused in the silence in front of a cue: hold the cue rather than
            // the synthesizer, and start it again on resume.
            clearTimeout(state.timer);
            state.timer = null;
            state.pendingIndex = state.index;
        } else {
            try {
                synth.pause();
            } catch (e) { /* nothing to hold */ }
        }
        render();
    }

    function resume() {
        if (!state.playing || !state.paused) return;
        state.paused = false;
        if (state.pendingIndex != null) {
            var index = state.pendingIndex;
            state.pendingIndex = null;
            speakFrom(index);
        } else {
            try {
                synth.resume();
            } catch (e) { /* nothing to resume */ }
        }
        render();
    }

    function step(delta) {
        if (!state.playing || !state.cues.length) return;
        var next = Math.min(state.cues.length - 1, Math.max(0, state.index + delta));
        state.paused = false;
        state.pendingIndex = null;
        speakFrom(next);
    }

    // ---------------------------------------------------------- the screen

    var HIGHLIGHT_CLASS = 'is-reading-aloud';
    var highlighted = null;

    function clearHighlight() {
        if (highlighted) {
            highlighted.classList.remove(HIGHLIGHT_CLASS);
            highlighted = null;
        }
    }

    function highlight(current) {
        clearHighlight();
        if (!state.source || !current) return;
        var element = state.source.element(current.elementId);
        if (!element) return;
        element.classList.add(HIGHLIGHT_CLASS);
        highlighted = element;
        var box = element.getBoundingClientRect();
        if (box.top < 80 || box.bottom > window.innerHeight - 120) {
            var reduced = window.matchMedia
                && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            element.scrollIntoView({
                block: 'center',
                behavior: reduced ? 'auto' : 'smooth'
            });
        }
    }

    /**
     * Whose line is being read — the one thing the bar can say that the
     * highlight cannot. A song and a note have no speakers, and "Narration"
     * over a lyric would be a label that says nothing at all, so what they show
     * instead is the line itself.
     */
    function nowReading() {
        var current = state.cues[state.index];
        if (!current) return '';
        if (current.speaker && (current.kind === 'cue' || current.kind === 'speech')) {
            return current.speaker;
        }
        if (state.source && state.source.kind === 'script') return 'Narration';
        return current.text;
    }

    var bar = null;

    function buildBar() {
        if (bar) return bar;
        bar = document.createElement('div');
        bar.className = 'read-aloud-bar';
        bar.id = 'read-aloud-bar';
        bar.setAttribute('role', 'region');
        bar.setAttribute('aria-label', 'Read aloud');
        bar.hidden = true;
        bar.innerHTML =
            '<div class="read-aloud-main">' +
            '  <div class="read-aloud-transport" role="group" aria-label="Playback">' +
            '    <button type="button" class="read-aloud-btn" data-read-aloud="previous" title="Previous" aria-label="Previous">&#9198;</button>' +
            '    <button type="button" class="read-aloud-btn read-aloud-btn--play" data-read-aloud="play" title="Play" aria-label="Play">&#9654;</button>' +
            '    <button type="button" class="read-aloud-btn" data-read-aloud="next" title="Next" aria-label="Next">&#9197;</button>' +
            '    <button type="button" class="read-aloud-btn" data-read-aloud="stop" title="Stop" aria-label="Stop">&#9209;</button>' +
            '  </div>' +
            '  <p class="read-aloud-reading">' +
            '    <span class="read-aloud-title"></span>' +
            '    <span class="read-aloud-now" role="status" aria-live="polite"></span>' +
            '  </p>' +
            '  <div class="read-aloud-settings">' +
            '    <label class="read-aloud-field"><span>Speed</span>' +
            '      <select class="read-aloud-select" data-read-aloud="speed" aria-label="Reading speed"></select>' +
            '    </label>' +
            '    <label class="read-aloud-field read-aloud-field--voice"><span>Voice</span>' +
            '      <select class="read-aloud-select" data-read-aloud="voice" aria-label="Reading voice"></select>' +
            '    </label>' +
            '    <button type="button" class="read-aloud-btn read-aloud-btn--wide read-aloud-options-toggle"' +
            '            data-read-aloud="options" aria-expanded="false" title="What to read"' +
            '            aria-label="What to read">Read&hellip;</button>' +
            '  </div>' +
            '</div>' +
            '<div class="read-aloud-options" hidden>' +
            '  <label><input type="checkbox" data-read-aloud-option="announceSpeakers"> Character names</label>' +
            '  <label><input type="checkbox" data-read-aloud-option="includeDescription"> Action and headings</label>' +
            '  <label><input type="checkbox" data-read-aloud-option="includeDirections"> Parentheticals</label>' +
            '</div>';
        document.body.appendChild(bar);

        var speed = bar.querySelector('[data-read-aloud="speed"]');
        SPEEDS.forEach(function (value) {
            var option = document.createElement('option');
            option.value = String(value);
            option.textContent = value === 1 ? 'Normal' : value + '×';
            speed.appendChild(option);
        });

        bar.addEventListener('click', function (event) {
            var button = event.target.closest('[data-read-aloud]');
            if (!button || button.tagName === 'SELECT') return;
            var action = button.getAttribute('data-read-aloud');
            if (action === 'play') {
                if (!state.playing) restart();
                else if (state.paused) resume();
                else pause();
            } else if (action === 'stop') {
                stop(false);
                hideBar();
            } else if (action === 'next') {
                step(1);
            } else if (action === 'previous') {
                step(-1);
            } else if (action === 'options') {
                var panel = bar.querySelector('.read-aloud-options');
                panel.hidden = !panel.hidden;
                button.setAttribute('aria-expanded', panel.hidden ? 'false' : 'true');
            }
        });

        bar.addEventListener('change', function (event) {
            var target = event.target;
            var control = target.getAttribute && target.getAttribute('data-read-aloud');
            if (control === 'speed') {
                prefs.speed = Number(target.value);
                savePrefs();
                // The rate is fixed when an utterance starts, so the change is
                // heard from the current line rather than the next one.
                if (state.playing && !state.paused) speakFrom(state.index);
                return;
            }
            if (control === 'voice') {
                prefs.voice = target.value || null;
                savePrefs();
                if (state.playing && !state.paused) speakFrom(state.index);
                return;
            }
            var option = target.getAttribute && target.getAttribute('data-read-aloud-option');
            if (option) {
                prefs[option] = !!target.checked;
                savePrefs();
                // What to include changes the shape of the run, so it is built
                // again around wherever the reading currently is.
                if (state.playing) {
                    rebuild(true);
                    if (!state.cues.length) {
                        stop(false);
                        announce('Nothing to read');
                        return;
                    }
                    if (!state.paused) speakFrom(state.index);
                    else render();
                }
            }
        });
        return bar;
    }

    function restart() {
        var source = state.source || detectSource();
        if (source) start(source);
    }

    function showBar() {
        buildBar().hidden = false;
        render();
    }

    function hideBar() {
        if (bar) bar.hidden = true;
    }

    function announce(message) {
        if (!bar) return;
        var label = bar.querySelector('.read-aloud-now');
        if (label) label.textContent = message;
    }

    function fillVoices() {
        if (!bar) return;
        var select = bar.querySelector('[data-read-aloud="voice"]');
        var available = voices();
        var current = chosenVoice();
        select.innerHTML = '';
        if (!available.length) {
            var none = document.createElement('option');
            none.textContent = 'Default';
            select.appendChild(none);
            select.disabled = true;
            return;
        }
        select.disabled = false;
        available.forEach(function (voice) {
            var option = document.createElement('option');
            option.value = voice.voiceURI || voice.name;
            option.textContent = voice.name + ' (' + voice.lang + ')';
            select.appendChild(option);
        });
        if (current) select.value = current.voiceURI || current.name;
    }

    function render() {
        if (!bar || bar.hidden) return;
        var play = bar.querySelector('[data-read-aloud="play"]');
        var speaking = state.playing && !state.paused;
        play.innerHTML = speaking ? '&#10074;&#10074;' : '&#9654;';
        play.setAttribute('aria-label', speaking ? 'Pause' : 'Play');
        play.setAttribute('title', speaking ? 'Pause' : 'Play');

        var isScript = state.source && state.source.kind === 'script';
        bar.querySelector('.read-aloud-options-toggle').hidden = !isScript;
        if (!isScript) {
            var panel = bar.querySelector('.read-aloud-options');
            panel.hidden = true;
        }

        var noun = state.source ? state.source.noun : 'Element';
        ['previous', 'next'].forEach(function (action) {
            var button = bar.querySelector('[data-read-aloud="' + action + '"]');
            var label = (action === 'previous' ? 'Previous ' : 'Next ') + noun.toLowerCase();
            button.setAttribute('aria-label', label);
            button.setAttribute('title', label);
            button.disabled = !state.playing;
        });

        bar.querySelector('[data-read-aloud="speed"]').value = String(prefs.speed);
        fillVoices();
        bar.querySelectorAll('[data-read-aloud-option]').forEach(function (box) {
            box.checked = !!prefs[box.getAttribute('data-read-aloud-option')];
        });

        // Which document the voice is reading. On a page with one it is only a
        // label; on a stack of songs it is the answer to "which one is that?".
        var title = bar.querySelector('.read-aloud-title');
        title.textContent = state.source ? state.source.title : '';
        title.hidden = !title.textContent;

        var label = bar.querySelector('.read-aloud-now');
        if (state.playing) {
            var position = state.cues.length
                ? ' · ' + (state.index + 1) + ' of ' + state.cues.length
                : '';
            label.textContent = (state.paused ? 'Paused' : nowReading()) + position;
        }
    }

    // ------------------------------------------------------------- wiring

    /**
     * The button's own document: pressed again it stops, and pressed on a
     * different song it reads that one instead — one voice in the room, and the
     * second ask is an ask for something else rather than a request to be quiet.
     */
    function toggle(trigger) {
        var source = detectSource(trigger);
        if (!source) return;
        if (state.playing && state.source && state.source.key === source.key) {
            stop(false);
            hideBar();
            return;
        }
        start(source);
    }

    document.addEventListener('click', function (event) {
        var trigger = event.target.closest && event.target.closest('[data-read-aloud-entry]');
        if (!trigger) return;
        event.preventDefault();
        toggle(trigger);
    });

    // Escape stops the reading, as it closes everything else here.
    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape' || !state.playing) return;
        stop(false);
        hideBar();
    });

    // A boosted navigation replaces the page under the voice: what was being
    // read is no longer on screen, so the reading ends with it.
    ['htmx:beforeSwap', 'htmx:historyRestore'].forEach(function (name) {
        document.body.addEventListener(name, function () {
            if (!state.playing) return;
            stop(false);
            hideBar();
        });
    });

    window.addEventListener('beforeunload', function () {
        cancelSpeech();
    });

    // Chrome hands back an empty voice list until it has loaded them.
    if (typeof synth.onvoiceschanged !== 'undefined') {
        synth.addEventListener('voiceschanged', function () {
            if (bar && !bar.hidden) fillVoices();
        });
    }

    window.scriptyReadAloud = {
        toggle: toggle,
        stop: function () {
            stop(false);
            hideBar();
        },
        // Text rules, exposed for tests and for anything else that has to say a
        // screenplay line the way it should be heard.
        spoken: spoken
    };
})();
