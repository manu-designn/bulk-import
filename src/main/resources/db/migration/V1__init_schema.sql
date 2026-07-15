-- Bulk Data Import System - Initial Schema
-- Day 1 deliverable: database design

CREATE TABLE import_job (
    id                VARCHAR(36)    PRIMARY KEY,
    file_name         VARCHAR(512)   NOT NULL,
    stored_file_path  VARCHAR(1024),
    file_hash         VARCHAR(64)    NOT NULL,
    file_size_bytes   BIGINT,
    status            VARCHAR(32)    NOT NULL,
    total_records     INTEGER,
    success_count     INTEGER        DEFAULT 0,
    failed_count      INTEGER        DEFAULT 0,
    duplicate_count   INTEGER        DEFAULT 0,
    error_message     VARCHAR(2000),
    created_at        TIMESTAMP      NOT NULL,
    started_at        TIMESTAMP,
    completed_at      TIMESTAMP,

    CONSTRAINT uq_import_job_file_hash UNIQUE (file_hash)
);

CREATE INDEX idx_import_job_status ON import_job (status);

CREATE TABLE import_record (
    id             VARCHAR(36)    PRIMARY KEY,
    job_id         VARCHAR(36)    NOT NULL,
    `row_number`   INTEGER,
    data           TEXT           NOT NULL,
    record_hash    VARCHAR(64),
    status         VARCHAR(32)    NOT NULL,
    error_message  VARCHAR(1000),

    CONSTRAINT fk_import_record_job
        FOREIGN KEY (job_id) REFERENCES import_job (id) ON DELETE CASCADE
);

CREATE INDEX idx_import_record_job_id ON import_record (job_id);
CREATE INDEX idx_import_record_status ON import_record (status);
CREATE INDEX idx_import_record_job_hash ON import_record (job_id, record_hash);
