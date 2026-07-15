package com.enterprise.bulkimport.exception;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String jobId) {
        super("Import job not found: " + jobId);
    }
}
