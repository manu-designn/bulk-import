package com.enterprise.bulkimport.dto;

import com.enterprise.bulkimport.enums.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportSummaryResponse {
    private String jobId;
    private JobStatus status;
    private int totalRecords;
    private int successCount;
    private int failedCount;
    private int duplicateCount;
}
