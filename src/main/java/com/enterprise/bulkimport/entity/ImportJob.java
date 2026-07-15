package com.enterprise.bulkimport.entity;

import com.enterprise.bulkimport.enums.JobStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Table(
        name = "import_job",
        indexes = {
                @Index(name = "idx_import_job_status", columnList = "status"),
                @Index(name = "idx_import_job_file_hash", columnList = "file_hash", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportJob {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "stored_file_path", length = 1024)
    private String storedFilePath;

    /** SHA-256 hex digest of the uploaded file's bytes — drives idempotency. */
    @Column(name = "file_hash", nullable = false, unique = true, length = 64)
    private String fileHash;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JobStatus status;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "failed_count")
    private Integer failedCount;

    @Column(name = "duplicate_count")
    private Integer duplicateCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = JobStatus.PENDING;
        }
        if (this.successCount == null) this.successCount = 0;
        if (this.failedCount == null) this.failedCount = 0;
        if (this.duplicateCount == null) this.duplicateCount = 0;
    }
}
