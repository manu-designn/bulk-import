package com.enterprise.bulkimport.entity;

import com.enterprise.bulkimport.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "import_record",
        indexes = {
                @Index(name = "idx_import_record_job_id", columnList = "job_id"),
                @Index(name = "idx_import_record_status", columnList = "status"),
                @Index(name = "idx_import_record_job_hash", columnList = "job_id, record_hash")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ImportJob job;

    @Column(name = "row_number")
    private Integer rowNumber;

    /** Raw row content, stored as JSON text. Kept schema-agnostic on purpose. */
    @Lob
    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data;

    
    @Column(name = "record_hash", length = 64)
    private String recordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RecordStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
