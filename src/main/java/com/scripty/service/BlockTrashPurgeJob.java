package com.scripty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hard-deletes soft-deleted blocks once they age past
 * {@link BlockService#TRASH_RETENTION_DAYS}. Until then a deleted block stays
 * restorable from the project's Recently Deleted page.
 */
@Component
public class BlockTrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(BlockTrashPurgeJob.class);

    private final BlockService blockService;

    public BlockTrashPurgeJob(BlockService blockService) {
        this.blockService = blockService;
    }

    @Scheduled(cron = "0 30 4 * * *")
    public void purgeExpiredDeletedBlocks() {
        int purged = blockService.purgeExpiredDeletedBlocks();
        if (purged > 0) {
            log.info("Purged {} block(s) deleted more than {} days ago",
                    purged, BlockService.TRASH_RETENTION_DAYS);
        }
    }
}
