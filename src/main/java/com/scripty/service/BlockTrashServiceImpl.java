package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.DeletedBlock;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.User;
import com.scripty.repository.DeletedBlockRepository;
import com.scripty.repository.ScriptEditionRepository;
import com.scripty.viewmodel.block.trash.DeletedBlockListViewModel;
import com.scripty.viewmodel.block.trash.DeletedBlockViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockTrashServiceImpl implements BlockTrashService {

    private static final Logger log = LoggerFactory.getLogger(BlockTrashServiceImpl.class);

    private static final int PREVIEW_MAX_CHARS = 140;

    private final DeletedBlockRepository deletedBlockRepository;
    private final ScriptEditionRepository scriptEditionRepository;
    private final BlockService blockService;
    private final ProjectService projectService;

    // Zero or less keeps deleted blocks indefinitely: the nightly sweep no-ops and the
    // trash page drops its purge-date copy. Set a positive value to re-enable expiry.
    @Value("${app.block-trash-retention-days:0}")
    private int retentionDays;

    public BlockTrashServiceImpl(DeletedBlockRepository deletedBlockRepository,
                                 ScriptEditionRepository scriptEditionRepository,
                                 BlockService blockService,
                                 ProjectService projectService) {
        this.deletedBlockRepository = deletedBlockRepository;
        this.scriptEditionRepository = scriptEditionRepository;
        this.blockService = blockService;
        this.projectService = projectService;
    }

    @Override
    @Transactional(readOnly = true)
    public DeletedBlockListViewModel getTrashViewModel(Integer projectId, User currentUser) {
        Project project = projectService.read(projectId);
        if (project == null || !projectService.canUserAccessProject(project, currentUser)) {
            return null;
        }
        // Only worth naming the edition when the project has more than one; otherwise
        // it's noise on every row.
        boolean multipleEditions = scriptEditionRepository.countByProjectId(projectId) > 1;
        List<DeletedBlock> records = deletedBlockRepository.findByProjectIdOrderByDeletedAtDesc(projectId);
        List<DeletedBlockViewModel> rows = new ArrayList<>(records.size());
        for (DeletedBlock record : records) {
            rows.add(toViewModel(record, multipleEditions));
        }
        return new DeletedBlockListViewModel(projectId, project.getTitle(), retentionDays, rows);
    }

    @Override
    @Transactional
    public Block restore(Integer deletedBlockId, Integer projectId, User currentUser) {
        DeletedBlock record = accessibleRecord(deletedBlockId, projectId, currentUser);
        if (record == null) {
            return null;
        }
        Block restored = blockService.restoreBlock(record);
        if (restored == null) {
            return null;
        }
        deletedBlockRepository.delete(record);
        return restored;
    }

    @Override
    @Transactional
    public boolean purge(Integer deletedBlockId, Integer projectId, User currentUser) {
        DeletedBlock record = accessibleRecord(deletedBlockId, projectId, currentUser);
        if (record == null) {
            return false;
        }
        deletedBlockRepository.delete(record);
        return true;
    }

    @Override
    @Transactional
    public int purgeExpired() {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<DeletedBlock> expired = deletedBlockRepository.findByDeletedAtBefore(cutoff);
        if (expired.isEmpty()) {
            return 0;
        }
        deletedBlockRepository.deleteAll(expired);
        return expired.size();
    }

    @Override
    public int getRetentionDays() {
        return retentionDays;
    }

    /** Loads a trash record only if it belongs to a project the user may access. */
    private DeletedBlock accessibleRecord(Integer deletedBlockId, Integer projectId, User currentUser) {
        if (deletedBlockId == null || projectId == null) {
            return null;
        }
        if (!projectService.canUserAccessProject(projectId, currentUser)) {
            return null;
        }
        return deletedBlockRepository.findByIdAndProjectId(deletedBlockId, projectId).orElse(null);
    }

    private DeletedBlockViewModel toViewModel(DeletedBlock record, boolean showEdition) {
        String preview = buildPreview(record.getContent());
        String editionName = null;
        if (showEdition) {
            ScriptEdition edition = record.getScriptEdition();
            editionName = edition != null ? edition.getName() : null;
        }
        String deletedByName = null;
        User deletedBy = record.getDeletedBy();
        if (deletedBy != null) {
            deletedByName = deletedBy.getUsername();
        }
        LocalDateTime purgeAt = retentionDays > 0 && record.getDeletedAt() != null
                ? record.getDeletedAt().plusDays(retentionDays)
                : null;
        return new DeletedBlockViewModel(
                record.getId(),
                preview,
                preview.isEmpty(),
                Block.typeLabelFor(record.getType()),
                editionName,
                deletedByName,
                record.getDeletedAt(),
                purgeAt);
    }

    /** Collapses a block's content to a single trimmed, length-capped preview line. */
    private static String buildPreview(String content) {
        if (content == null) {
            return "";
        }
        String flattened = content.replaceAll("\\s+", " ").trim();
        if (flattened.length() <= PREVIEW_MAX_CHARS) {
            return flattened;
        }
        return flattened.substring(0, PREVIEW_MAX_CHARS - 1).trim() + "…";
    }
}
