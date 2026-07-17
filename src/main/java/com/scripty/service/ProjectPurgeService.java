package com.scripty.service;

import com.scripty.repository.ProjectRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard-deletes trashed projects once their recovery window has passed. Until
 * then a deleted project keeps all of its content and an admin can restore it
 * from /project/trash.
 */
@Service
public class ProjectPurgeService {

    private static final Logger log = LoggerFactory.getLogger(ProjectPurgeService.class);

    private final ProjectRepository projectRepository;

    @Value("${app.project-trash-retention-days:30}")
    private int retentionDays;

    public ProjectPurgeService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Scheduled(cron = "${app.project-purge-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpiredProjects() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int purged = projectRepository.purgeTrashedBefore(cutoff);
        if (purged > 0) {
            log.info("Purged {} project(s) deleted before {}", purged, cutoff);
        }
    }

    public int getRetentionDays() {
        return retentionDays;
    }
}
