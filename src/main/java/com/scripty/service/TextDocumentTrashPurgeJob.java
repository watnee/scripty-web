package com.scripty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Permanently removes documents that have sat in Recently deleted past the
 * {@link TextDocumentService#TRASH_RETENTION_DAYS}-day recovery window.
 */
@Component
@Lazy(false) // dev profile lazy-inits beans; the purge job must still run
public class TextDocumentTrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TextDocumentTrashPurgeJob.class);

    private final TextDocumentService textDocumentService;

    public TextDocumentTrashPurgeJob(TextDocumentService textDocumentService) {
        this.textDocumentService = textDocumentService;
    }

    // First run shortly after startup so long-idle servers still purge, then every 6 hours.
    @Scheduled(initialDelay = 2 * 60 * 1000L, fixedDelay = 6 * 60 * 60 * 1000L)
    public void purgeExpiredDeletedDocuments() {
        int purged = textDocumentService.purgeExpiredDeleted();
        if (purged > 0) {
            log.info("Purged {} document(s) deleted more than {} days ago",
                    purged, TextDocumentService.TRASH_RETENTION_DAYS);
        }
    }
}
