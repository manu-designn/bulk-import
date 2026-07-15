package com.enterprise.bulkimport.enums;

/**
 * Lifecycle of an ImportJob.
 *
 * PENDING   -> file received, saved, not yet picked up by the async worker
 * PROCESSING-> async worker actively streaming/validating the file
 * COMPLETED -> finished, all records processed (some may still have FAILED status)
 * FAILED    -> job-level failure (e.g. unreadable file, unsupported format)
 * PARTIALLY_COMPLETED -> finished, but at least one record failed validation/insert
 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED
}
