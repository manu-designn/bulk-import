package com.enterprise.bulkimport.scheduler;

import com.enterprise.bulkimport.config.ImportProperties;
import com.enterprise.bulkimport.entity.ImportJob;
import com.enterprise.bulkimport.enums.JobStatus;
import com.enterprise.bulkimport.repository.ImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Edge Case: "system crash during processing." Full crash recovery would
 * mean checkpointing a row offset and resuming from there — not built here
 * (flagged in the README as a real limitation). What this DOES do: if the
 * application crashes (or is killed) while a job is PROCESSING, that job
 * has no way to notice on its own — nothing is left running to update its
 * status. Without this, it would sit in PROCESSING forever, invisible to
 * anyone polling GET /api/v1/imports/{id}.
 *
 * This runs periodically, finds any job that's been in PROCESSING for
 * longer than app.import.stale-job-timeout-minutes, and marks it FAILED
 * with an explanatory message — so a crash produces a visible, terminal
 * state instead of a silent hang. The already-flushed batches for that job
 * (see AsyncImportProcessor's periodic saveAll()) remain in the DB either
 * way, so partial progress isn't lost — it's just not resumed automatically.
 */
@Component
public class StaleJobRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleJobRecoveryScheduler.class);

    private final ImportJobRepository importJobRepository;
    private final ImportProperties importProperties;

    public StaleJobRecoveryScheduler(ImportJobRepository importJobRepository,
                                      ImportProperties importProperties) {
        this.importJobRepository = importJobRepository;
        this.importProperties = importProperties;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void recoverStaleJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(importProperties.getStaleJobTimeoutMinutes());
        List<ImportJob> stale = importJobRepository.findByStatusAndStartedAtBefore(JobStatus.PROCESSING, cutoff);

        if (stale.isEmpty()) {
            return;
        }

        for (ImportJob job : stale) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("Marked FAILED by stale-job recovery: still PROCESSING after "
                    + importProperties.getStaleJobTimeoutMinutes()
                    + " minutes, most likely the application crashed or was restarted mid-file.");
            job.setCompletedAt(LocalDateTime.now());
            importJobRepository.save(job);
            log.warn("Stale job {} ({}) marked FAILED — stuck in PROCESSING since {}",
                    job.getId(), job.getFileName(), job.getStartedAt());
        }
    }
}
