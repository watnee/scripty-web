package com.scripty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class SongUndoRedoServiceImpl implements SongUndoRedoService {

    private static final int MAX_STACK_SIZE = 50;
    private static final String SESSION_KEY_PREFIX = "songUndoRedo_";

    private final SongBlockService songBlockService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SongUndoRedoServiceImpl(SongBlockService songBlockService, ObjectMapper objectMapper) {
        this.songBlockService = songBlockService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    public boolean canUndo(Integer documentId, Integer editionId) {
        return documentId != null && editionId != null && !getState(documentId, editionId).undoStack.isEmpty();
    }

    @Override
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

    private String sessionKey(Integer documentId, Integer editionId) {
        return SESSION_KEY_PREFIX + documentId + "_" + editionId;
    }

    private HttpSession getSession() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest().getSession(true);
    }

    private UndoRedoState getState(Integer documentId, Integer editionId) {
        HttpSession session = getSession();
        if (session == null) {
            return new UndoRedoState();
        }
        UndoRedoState state = (UndoRedoState) session.getAttribute(sessionKey(documentId, editionId));
        if (state == null) {
            state = new UndoRedoState();
            session.setAttribute(sessionKey(documentId, editionId), state);
        }
        return state;
    }

    private void saveState(Integer documentId, Integer editionId, UndoRedoState state) {
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
