package com.scripty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clears out documents that have sat in the trash past the retention window.
 * Runs nightly; the work itself is idempotent, so a missed run just means the
 * next one purges a little more.
 */
@Component
public class TextDocumentTrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TextDocumentTrashPurgeJob.class);

    private final TextDocumentService textDocumentService;

    @Autowired
    public TextDocumentTrashPurgeJob(TextDocumentService textDocumentService) {
        this.textDocumentService = textDocumentService;
    }

    @Scheduled(cron = "${scripty.documents.trash-purge-cron:0 15 3 * * *}")
    public void purgeExpiredDocuments() {
        try {
            int purged = textDocumentService.purgeExpired();
            if (purged > 0) {
                log.info("Purged {} expired document(s) from the trash", purged);
            }
        } catch (RuntimeException e) {
            // Never let a bad run kill the scheduler thread — the next one retries.
            log.error("Failed to purge expired documents from the trash", e);
        }
    }
}
