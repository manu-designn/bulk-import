package com.enterprise.bulkimport.exception;

public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException(String fileName) {
        super("Unsupported file type for: " + fileName + ". Only .csv, .xls, .xlsx are accepted.");
    }
}
