package com.scripty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ProjectUndoRedoServiceImpl implements ProjectUndoRedoService {

    private static final int MAX_STACK_SIZE = 50;
    private static final String SESSION_KEY_PREFIX = "undoRedo_";
    private static final String ENTRY_TYPE_MOVE = "move";

    private final ProjectVersionService projectVersionService;
    private final BlockService blockService;
    private final BlockRepository blockRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    private final ThreadLocal<Boolean> suppressRecording = ThreadLocal.withInitial(() -> false);

    @Autowired
    public ProjectUndoRedoServiceImpl(ProjectVersionService projectVersionService,
                                        BlockService blockService,
                                        BlockRepository blockRepository,
                                        ProjectRepository projectRepository,
                                        ObjectMapper objectMapper) {
        this.projectVersionService = projectVersionService;
        this.blockService = blockService;
        this.blockRepository = blockRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public void recordCheckpoint(Integer projectId) {
        if (projectId == null || suppressRecording.get()) {
            return;
        }
        UndoRedoState state = getState(projectId);
        String snapshot = projectVersionService.buildSnapshotJson(projectId);
        state.undoStack.push(snapshot);
        state.redoStack.clear();
        trimUndoStack(state);
        saveState(projectId, state);
    }

    @Override
    @Transactional(readOnly = true)
    public void recordCheckpointForBlock(Integer blockId) {
        if (blockId == null) {
            return;
        }
        Block block = blockRepository.findById(blockId).orElse(null);
        if (block == null || block.getProject() == null) {
            return;
        }
        recordCheckpoint(block.getProject().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public void recordCheckpointForScene(Integer sceneBlockId) {
        if (sceneBlockId == null) {
            return;
        }
        Block block = blockRepository.findById(sceneBlockId).orElse(null);
        if (block == null || block.getProject() == null) {
            return;
        }
        recordCheckpoint(block.getProject().getId());
    }

    @Override
    public void recordMoveCheckpoint(Integer blockId, int fromOrder, int toOrder) {
        if (blockId == null || fromOrder == toOrder || suppressRecording.get()) {
            return;
        }
        Block block = blockRepository.findById(blockId).orElse(null);
        if (block == null || block.getProject() == null) {
            return;
        }
        Integer projectId = block.getProject().getId();
        UndoRedoState state = getState(projectId);
        state.undoStack.push(encodeMoveEntry(blockId, fromOrder, toOrder));
        state.redoStack.clear();
        trimUndoStack(state);
        saveState(projectId, state);
    }

    @Override
    @Transactional
    public boolean undo(Integer projectId) {
        return undoWithDetails(projectId).success();
    }

    @Override
    @Transactional
    public boolean redo(Integer projectId) {
        return redoWithDetails(projectId).success();
    }

    @Override
    @Transactional
    public UndoRedoResult undoWithDetails(Integer projectId) {
        return undoInternal(projectId);
    }

    @Override
    @Transactional
    public UndoRedoResult redoWithDetails(Integer projectId) {
        return redoInternal(projectId);
    }

    private UndoRedoResult undoInternal(Integer projectId) {
        if (projectId == null) {
            return UndoRedoResult.failed();
        }
        UndoRedoState state = getState(projectId);
        if (state.undoStack.isEmpty()) {
            return UndoRedoResult.failed();
        }

        suppressRecording.set(true);
        try {
            String entry = state.undoStack.pop();
            MoveEntry move = parseMoveEntry(entry);
            if (move != null) {
                state.redoStack.push(encodeMoveEntry(move.blockId(), move.toOrder(), move.fromOrder()));
                Block moved = blockService.moveBlockTo(move.blockId(), move.fromOrder());
                if (moved == null || moved.getOrder() != move.fromOrder()) {
                    state.undoStack.push(entry);
                    state.redoStack.pop();
                    saveState(projectId, state);
                    return UndoRedoResult.failed();
                }
                saveState(projectId, state);
                return UndoRedoResult.moveSuccess();
            }

            state.redoStack.push(projectVersionService.buildSnapshotJson(projectId));
            projectVersionService.applySnapshotJson(projectId, entry);
            saveState(projectId, state);
            return UndoRedoResult.snapshotSuccess();
        } finally {
            suppressRecording.set(false);
        }
    }

    private UndoRedoResult redoInternal(Integer projectId) {
        if (projectId == null) {
            return UndoRedoResult.failed();
        }
        UndoRedoState state = getState(projectId);
        if (state.redoStack.isEmpty()) {
            return UndoRedoResult.failed();
        }

        suppressRecording.set(true);
        try {
            String entry = state.redoStack.pop();
            MoveEntry move = parseMoveEntry(entry);
            if (move != null) {
                state.undoStack.push(encodeMoveEntry(move.blockId(), move.toOrder(), move.fromOrder()));
                Block moved = blockService.moveBlockTo(move.blockId(), move.fromOrder());
                if (moved == null || moved.getOrder() != move.fromOrder()) {
                    state.redoStack.push(entry);
                    state.undoStack.pop();
                    saveState(projectId, state);
                    return UndoRedoResult.failed();
                }
                saveState(projectId, state);
                return UndoRedoResult.moveSuccess();
            }

            state.undoStack.push(projectVersionService.buildSnapshotJson(projectId));
            projectVersionService.applySnapshotJson(projectId, entry);
            saveState(projectId, state);
            return UndoRedoResult.snapshotSuccess();
        } finally {
            suppressRecording.set(false);
        }
    }

    @Override
    public boolean canUndo(Integer projectId) {
        return projectId != null && !getState(projectId).undoStack.isEmpty();
    }

    @Override
    public boolean canRedo(Integer projectId) {
        return projectId != null && !getState(projectId).redoStack.isEmpty();
    }

    private String encodeMoveEntry(int blockId, int fromOrder, int toOrder) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("type", ENTRY_TYPE_MOVE);
        entry.put("blockId", blockId);
        entry.put("fromOrder", fromOrder);
        entry.put("toOrder", toOrder);
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode move undo entry", e);
        }
    }

    private MoveEntry parseMoveEntry(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> entry = objectMapper.readValue(json, Map.class);
            if (!ENTRY_TYPE_MOVE.equals(entry.get("type"))) {
                return null;
            }
            Integer blockId = toInteger(entry.get("blockId"));
            Integer fromOrder = toInteger(entry.get("fromOrder"));
            Integer toOrder = toInteger(entry.get("toOrder"));
            if (blockId == null || fromOrder == null || toOrder == null) {
                return null;
            }
            return new MoveEntry(blockId, fromOrder, toOrder);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static void trimUndoStack(UndoRedoState state) {
        while (state.undoStack.size() > MAX_STACK_SIZE) {
            state.undoStack.removeLast();
        }
    }

    private HttpSession getSession() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return attrs.getRequest().getSession(true);
    }

    private UndoRedoState getState(Integer projectId) {
        HttpSession session = getSession();
        if (session == null) {
            return new UndoRedoState();
        }
        String key = SESSION_KEY_PREFIX + projectId;
        UndoRedoState state = (UndoRedoState) session.getAttribute(key);
        if (state == null) {
            state = new UndoRedoState();
            session.setAttribute(key, state);
        }
        return state;
    }

    private void saveState(Integer projectId, UndoRedoState state) {
        HttpSession session = getSession();
        if (session != null) {
            session.setAttribute(SESSION_KEY_PREFIX + projectId, state);
        }
    }

    private record MoveEntry(int blockId, int fromOrder, int toOrder) {
    }

    static class UndoRedoState implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Deque<String> undoStack = new ArrayDeque<>();
        private final Deque<String> redoStack = new ArrayDeque<>();
    }
}
