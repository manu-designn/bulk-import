package com.enterprise.bulkimport.repository;

import com.enterprise.bulkimport.entity.ImportRecord;
import com.enterprise.bulkimport.enums.RecordStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRecordRepository extends JpaRepository<ImportRecord, String> {

    /** Paginated so a job with 100k+ records never has to load all of them at once. */
    Page<ImportRecord> findByJobId(String jobId, Pageable pageable);

    Page<ImportRecord> findByJobIdAndStatus(String jobId, RecordStatus status, Pageable pageable);

    boolean existsByJobIdAndRecordHash(String jobId, String recordHash);
}
