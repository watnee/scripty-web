package com.scripty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clears out screenplays that have sat in the trash past the retention window.
 * Runs nightly; the work itself is idempotent, so a missed run just means the
 * next one purges a little more.
 */
@Component
public class ProjectTrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ProjectTrashPurgeJob.class);

    private final ProjectService projectService;

    @Autowired
    public ProjectTrashPurgeJob(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Scheduled(cron = "${scripty.projects.trash-purge-cron:0 45 3 * * *}")
    public void purgeExpiredProjects() {
        try {
            int purged = projectService.purgeExpiredProjects();
            if (purged > 0) {
                log.info("Purged {} expired screenplay(s) from the trash", purged);
            }
        } catch (RuntimeException e) {
            // Never let a bad run kill the scheduler thread — the next one retries.
            log.error("Failed to purge expired screenplays from the trash", e);
        }
    }
}
