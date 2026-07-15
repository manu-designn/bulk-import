package com.enterprise.bulkimport.dto;

import com.enterprise.bulkimport.enums.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportJobResponse {
    private String id;
    private String fileName;
    private JobStatus status;
    private Integer totalRecords;
    private Integer successCount;
    private Integer failedCount;
    private Integer duplicateCount;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
