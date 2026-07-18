package com.scripty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clears out deleted blocks that have sat in the trash past the retention window.
 * Runs nightly; the work is idempotent, so a missed run just purges a little more
 * next time.
 */
@Component
public class BlockTrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(BlockTrashPurgeJob.class);

    private final BlockTrashService blockTrashService;

    public BlockTrashPurgeJob(BlockTrashService blockTrashService) {
        this.blockTrashService = blockTrashService;
    }

    @Scheduled(cron = "${app.block-trash-purge-cron:0 45 3 * * *}")
    public void purgeExpiredBlocks() {
        try {
            int purged = blockTrashService.purgeExpired();
            if (purged > 0) {
                log.info("Purged {} expired block(s) from the trash", purged);
            }
        } catch (RuntimeException e) {
            // Never let a bad run kill the scheduler thread — the next one retries.
            log.error("Failed to purge expired blocks from the trash", e);
        }
    }
}
