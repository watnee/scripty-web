package com.scripty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.SongEdition;
import com.scripty.dto.SongUndoState;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.repository.SongEditionRepository;
import com.scripty.repository.SongUndoStateRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Undo/redo for the song block editor.
 *
 * <p>Stacks are persisted per (document, edition, user) so they outlive the HTTP
 * session, exactly as {@link ProjectUndoRedoServiceImpl} does for the
 * screenplay: API clients authenticate on each request and keep no session — the
 * iOS client refuses cookies outright — so a session-backed stack was always
 * empty by the time they asked to undo, and the editor's Undo button never came
 * out of its greyed state. Keying by user means a member's undo rewinds their
 * own edits, not a collaborator's.
 *
 * <p>When no user resolves — an unauthenticated context, or a dev auto-login
 * principal with no row in {@code user} — the stacks fall back to the session,
 * preserving the old behaviour for that request instead of losing undo entirely.
 */
@Service
public class SongUndoRedoServiceImpl implements SongUndoRedoService {

    private static final int MAX_STACK_SIZE = 50;
    private static final String SESSION_KEY_PREFIX = "songUndoRedo_";

    private final SongBlockService songBlockService;
    private final ObjectMapper objectMapper;
    private final SongUndoStateRepository songUndoStateRepository;
    private final TextDocumentRepository textDocumentRepository;
    private final SongEditionRepository songEditionRepository;
    private final UserRepository userRepository;

    @Autowired
    public SongUndoRedoServiceImpl(SongBlockService songBlockService,
                                   ObjectMapper objectMapper,
                                   SongUndoStateRepository songUndoStateRepository,
                                   TextDocumentRepository textDocumentRepository,
                                   SongEditionRepository songEditionRepository,
                                   UserRepository userRepository) {
        this.songBlockService = songBlockService;
        this.objectMapper = objectMapper;
        this.songUndoStateRepository = songUndoStateRepository;
        this.textDocumentRepository = textDocumentRepository;
        this.songEditionRepository = songEditionRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void recordCheckpoint(Integer documentId, Integer editionId) {
        if (documentId == null || editionId == null) {
            return;
        }
        List<SongBlockService.LineSnapshot> lines = songBlockService.snapshotLines(documentId, editionId);
        if (lines == null) {
            return;
        }
        UndoRedoState state = getState(documentId, editionId);
        state.undoStack.push(encode(lines));
        state.redoStack.clear();
        while (state.undoStack.size() > MAX_STACK_SIZE) {
            state.undoStack.removeLast();
        }
        saveState(documentId, editionId, state);
    }

    @Override
    @Transactional
    public void recordCheckpointForBlock(Integer blockId) {
        recordCheckpoint(songBlockService.documentIdForBlock(blockId), songBlockService.editionIdForBlock(blockId));
    }

    @Override
    @Transactional
    public boolean undo(Integer documentId, Integer editionId) {
        return apply(documentId, editionId, true);
    }

    @Override
    @Transactional
    public boolean redo(Integer documentId, Integer editionId) {
        return apply(documentId, editionId, false);
    }

    private boolean apply(Integer documentId, Integer editionId, boolean undo) {
        if (documentId == null || editionId == null) {
            return false;
        }
        UndoRedoState state = getState(documentId, editionId);
        Deque<String> from = undo ? state.undoStack : state.redoStack;
        Deque<String> to = undo ? state.redoStack : state.undoStack;
        if (from.isEmpty()) {
            return false;
        }
        List<SongBlockService.LineSnapshot> current = songBlockService.snapshotLines(documentId, editionId);
        if (current == null) {
            return false;
        }
        String entry = from.pop();
        List<SongBlockService.LineSnapshot> restored = decode(entry);
        if (restored == null) {
            saveState(documentId, editionId, state);
            return false;
        }
        to.push(encode(current));
        songBlockService.replaceLines(documentId, editionId, restored);
        saveState(documentId, editionId, state);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUndo(Integer documentId, Integer editionId) {
        return documentId != null && editionId != null && !getState(documentId, editionId).undoStack.isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRedo(Integer documentId, Integer editionId) {
        return documentId != null && editionId != null && !getState(documentId, editionId).redoStack.isEmpty();
    }

    private String encode(List<SongBlockService.LineSnapshot> lines) {
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode song undo snapshot", e);
        }
    }

    private List<SongBlockService.LineSnapshot> decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SongBlockService.LineSnapshot>>() { });
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private UndoRedoState getState(Integer documentId, Integer editionId) {
        Integer userId = currentUserId();
        return userId != null
                ? getPersistentState(documentId, editionId, userId)
                : getSessionState(documentId, editionId);
    }

    private void saveState(Integer documentId, Integer editionId, UndoRedoState state) {
        Integer userId = currentUserId();
        if (userId != null) {
            savePersistentState(documentId, editionId, userId, state);
        } else {
            saveSessionState(documentId, editionId, state);
        }
    }

    private UndoRedoState getPersistentState(Integer documentId, Integer editionId, Integer userId) {
        return songUndoStateRepository
                .findByTextDocumentIdAndSongEditionIdAndUserId(documentId, editionId, userId)
                .map(row -> {
                    UndoRedoState state = new UndoRedoState();
                    fill(state.undoStack, row.getUndoJson());
                    fill(state.redoStack, row.getRedoJson());
                    return state;
                })
                .orElseGet(UndoRedoState::new);
    }

    private void savePersistentState(Integer documentId, Integer editionId, Integer userId, UndoRedoState state) {
        TextDocument document = textDocumentRepository.findById(documentId).orElse(null);
        SongEdition edition = songEditionRepository.findById(editionId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (document == null || edition == null || user == null) {
            return;
        }
        SongUndoState row = songUndoStateRepository
                .findByTextDocumentIdAndSongEditionIdAndUserId(documentId, editionId, userId)
                .orElseGet(SongUndoState::new);
        row.setTextDocument(document);
        row.setSongEdition(edition);
        row.setUser(user);
        row.setUndoJson(encodeStack(state.undoStack));
        row.setRedoJson(encodeStack(state.redoStack));
        row.setUpdatedAt(LocalDateTime.now());
        songUndoStateRepository.save(row);
    }

    /**
     * Serialises a stack head-first, so refilling with {@code addLast} rebuilds
     * the same order — the head stays the next entry to pop.
     */
    private String encodeStack(Deque<String> stack) {
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(stack));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode song undo stack", e);
        }
    }

    private void fill(Deque<String> stack, String json) {
        stack.clear();
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            for (String entry : objectMapper.readValue(json, new TypeReference<List<String>>() { })) {
                stack.addLast(entry);
            }
        } catch (JsonProcessingException e) {
            // A corrupt stack costs the writer their history, not their song.
            stack.clear();
        }
    }

    private Integer currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    private String sessionKey(Integer documentId, Integer editionId) {
        return SESSION_KEY_PREFIX + documentId + "_" + editionId;
    }

    private HttpSession getSession() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest().getSession(true);
    }

    private UndoRedoState getSessionState(Integer documentId, Integer editionId) {
        HttpSession session = getSession();
        if (session == null) {
            return new UndoRedoState();
        }
        String key = sessionKey(documentId, editionId);
        UndoRedoState state = (UndoRedoState) session.getAttribute(key);
        if (state == null) {
            state = new UndoRedoState();
            session.setAttribute(key, state);
        }
        return state;
    }

    private void saveSessionState(Integer documentId, Integer editionId, UndoRedoState state) {
        HttpSession session = getSession();
        if (session != null) {
            session.setAttribute(sessionKey(documentId, editionId), state);
        }
    }

    static class UndoRedoState implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Deque<String> undoStack = new ArrayDeque<>();
        private final Deque<String> redoStack = new ArrayDeque<>();
    }
}
