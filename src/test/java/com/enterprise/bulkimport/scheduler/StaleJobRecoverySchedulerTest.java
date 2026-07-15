package com.enterprise.bulkimport.scheduler;

import com.enterprise.bulkimport.config.ImportProperties;
import com.enterprise.bulkimport.entity.ImportJob;
import com.enterprise.bulkimport.enums.JobStatus;
import com.enterprise.bulkimport.repository.ImportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaleJobRecoverySchedulerTest {

    @Mock
    private ImportJobRepository importJobRepository;

    private StaleJobRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        ImportProperties props = new ImportProperties();
        props.setStaleJobTimeoutMinutes(30);
        scheduler = new StaleJobRecoveryScheduler(importJobRepository, props);
    }

    @Test
    void staleProcessingJobs_areMarkedFailed() {
        ImportJob stuckJob = ImportJob.builder()
                .id("stuck-1")
                .fileName("big.csv")
                .status(JobStatus.PROCESSING)
                .startedAt(LocalDateTime.now().minusHours(2))
                .build();

        when(importJobRepository.findByStatusAndStartedAtBefore(eq(JobStatus.PROCESSING), any()))
                .thenReturn(List.of(stuckJob));

        scheduler.recoverStaleJobs();

        assertEquals(JobStatus.FAILED, stuckJob.getStatus());
        assertNotNull(stuckJob.getErrorMessage());
        assertNotNull(stuckJob.getCompletedAt());
        verify(importJobRepository).save(stuckJob);
    }

    @Test
    void noStaleJobs_doesNothing() {
        when(importJobRepository.findByStatusAndStartedAtBefore(eq(JobStatus.PROCESSING), any()))
                .thenReturn(List.of());

        scheduler.recoverStaleJobs();

        verify(importJobRepository, never()).save(any());
    }
}
