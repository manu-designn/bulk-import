package com.enterprise.bulkimport.exception;

public class EmptyFileException extends RuntimeException {
    public EmptyFileException() {
        super("Uploaded file is empty.");
    }
}
