package com.enterprise.bulkimport.service;

import com.enterprise.bulkimport.config.ImportProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.enterprise.bulkimport.util.HashUtil.toHex;


@Service
public class FileStorageService {

    private final ImportProperties importProperties;

    public FileStorageService(ImportProperties importProperties) {
        this.importProperties = importProperties;
    }

    public record StoredFile(Path path, String sha256Hex, long sizeBytes) {
    }

    public StoredFile store(MultipartFile file, String jobId) {
        try {
            Path dir = Path.of(importProperties.getStorageDir());
            Files.createDirectories(dir);

            String safeExtension = extensionOf(file.getOriginalFilename());
            Path target = dir.resolve(jobId + safeExtension);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (InputStream in = file.getInputStream();
                 OutputStream fileOut = Files.newOutputStream(target);
                 DigestOutputStream digestOut = new DigestOutputStream(fileOut, digest)) {
                size = in.transferTo(digestOut);
            }

            return new StoredFile(target, toHex(digest.digest()), size);
        } catch (IOException e) {
            throw new UncheckedIOStorageException("Failed to store uploaded file", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Non-fatal: a leftover file on disk doesn't affect correctness, just housekeeping.
        }
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : "";
    }

    public static class UncheckedIOStorageException extends RuntimeException {
        public UncheckedIOStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
