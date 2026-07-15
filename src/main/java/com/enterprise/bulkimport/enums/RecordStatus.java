package com.enterprise.bulkimport.enums;

/**
 * Outcome of a single row/record within an ImportJob.
 */
public enum RecordStatus {
    VALID,
    INVALID,
    DUPLICATE,
    PERSISTED,
    FAILED_PERSISTENCE
}
