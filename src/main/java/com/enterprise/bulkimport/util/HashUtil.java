package com.enterprise.bulkimport.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    private HashUtil() {
    }

    
    public static String toHex(byte[] digestBytes) {
        StringBuilder sb = new StringBuilder(digestBytes.length * 2);
        for (byte b : digestBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    
    public static String hashRow(java.util.Map<String, String> row) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            row.keySet().stream()
                    .sorted()
                    .forEach(key -> {
                        String value = row.get(key) == null ? "" : row.get(key).trim().toLowerCase();
                        digest.update(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    });
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every standard JVM
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
