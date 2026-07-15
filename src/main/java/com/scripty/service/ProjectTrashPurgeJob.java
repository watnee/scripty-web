package com.scripty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Permanently deletes projects that have been in the trash longer than
 * {@link ProjectServiceImpl#TRASH_RETENTION_DAYS} days.
 */
@Component
@Lazy(false) // dev profile lazy-inits beans; @Scheduled only fires on eagerly created beans
public class ProjectTrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ProjectTrashPurgeJob.class);

    private final ProjectService projectService;

    public ProjectTrashPurgeJob(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT6H")
    public void purgeExpiredTrash() {
        try {
            int purged = projectService.purgeExpiredTrash();
            if (purged > 0) {
                log.info("Purged {} project(s) trashed more than {} days ago",
                        purged, ProjectServiceImpl.TRASH_RETENTION_DAYS);
            }
        } catch (Exception e) {
            log.error("Project trash purge failed", e);
        }
    }
}
