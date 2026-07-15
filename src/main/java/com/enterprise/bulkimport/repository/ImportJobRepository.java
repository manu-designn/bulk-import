package com.enterprise.bulkimport.repository;

import com.enterprise.bulkimport.entity.ImportJob;
import com.enterprise.bulkimport.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJob, String> {

   
    Optional<ImportJob> findByFileHash(String fileHash);

    
    List<ImportJob> findByStatusAndStartedAtBefore(JobStatus status, LocalDateTime cutoff);
}
