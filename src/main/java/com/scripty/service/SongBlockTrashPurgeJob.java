package com.scripty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clears out song lines that have sat in the per-song trash past the retention
 * window, mirroring {@link TextDocumentTrashPurgeJob}. Runs nightly; the work is
 * idempotent, so a missed run just means the next one purges a little more.
 */
@Component
public class SongBlockTrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(SongBlockTrashPurgeJob.class);

    private final SongBlockService songBlockService;

    @Autowired
    public SongBlockTrashPurgeJob(SongBlockService songBlockService) {
        this.songBlockService = songBlockService;
    }

    @Scheduled(cron = "${scripty.songblocks.trash-purge-cron:0 20 3 * * *}")
    public void purgeExpiredSongBlocks() {
        try {
            int purged = songBlockService.purgeExpiredBlocks();
            if (purged > 0) {
                log.info("Purged {} expired song line(s) from the trash", purged);
            }
        } catch (RuntimeException e) {
            // Never let a bad run kill the scheduler thread — the next one retries.
            log.error("Failed to purge expired song lines from the trash", e);
        }
    }
}
