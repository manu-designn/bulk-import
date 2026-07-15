package com.enterprise.bulkimport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.import")
public class ImportProperties {

    private String storageDir = "./import-files";

    private boolean deleteAfterProcessing = false;

    
    private int recordBatchSize = 500;

    
    private int staleJobTimeoutMinutes = 30;

    public int getStaleJobTimeoutMinutes() {
        return staleJobTimeoutMinutes;
    }

    public void setStaleJobTimeoutMinutes(int staleJobTimeoutMinutes) {
        this.staleJobTimeoutMinutes = staleJobTimeoutMinutes;
    }

    public int getRecordBatchSize() {
        return recordBatchSize;
    }

    public void setRecordBatchSize(int recordBatchSize) {
        this.recordBatchSize = recordBatchSize;
    }

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public boolean isDeleteAfterProcessing() {
        return deleteAfterProcessing;
    }

    public void setDeleteAfterProcessing(boolean deleteAfterProcessing) {
        this.deleteAfterProcessing = deleteAfterProcessing;
    }
}
