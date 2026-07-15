package com.enterprise.bulkimport.service;

import com.enterprise.bulkimport.dto.ImportJobResponse;
import com.enterprise.bulkimport.dto.ImportRecordResponse;
import com.enterprise.bulkimport.dto.ImportSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;


public interface ImportJobService {

    
    ImportJobResponse submitImport(MultipartFile file);

    ImportJobResponse getJob(String jobId);

    
    Page<ImportJobResponse> getAllJobs(Pageable pageable);

    Page<ImportRecordResponse> getRecords(String jobId, Pageable pageable);

    ImportSummaryResponse getSummary(String jobId);
}
