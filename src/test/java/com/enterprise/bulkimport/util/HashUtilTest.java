package com.enterprise.bulkimport.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HashUtilTest {

    @Test
    void sameValuesDifferentKeyOrder_produceSameHash() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("name", "John Smith");
        a.put("email", "john@example.com");

        Map<String, String> b = new LinkedHashMap<>();
        b.put("email", "john@example.com");
        b.put("name", "John Smith");

        assertEquals(HashUtil.hashRow(a), HashUtil.hashRow(b),
                "row hash should be order-independent so column order in the file doesn't matter");
    }

    @Test
    void caseAndWhitespaceDifferences_produceSameHash() {
        Map<String, String> a = Map.of("name", "John Smith", "email", "john@example.com");
        Map<String, String> b = Map.of("name", "  john smith  ", "email", "JOHN@EXAMPLE.COM");

        assertEquals(HashUtil.hashRow(a), HashUtil.hashRow(b),
                "row hash should normalize case/whitespace so near-identical rows are still caught as duplicates");
    }

    @Test
    void differentValues_produceDifferentHash() {
        Map<String, String> a = Map.of("name", "John Smith", "email", "john@example.com");
        Map<String, String> b = Map.of("name", "Jane Smith", "email", "jane@example.com");

        assertNotEquals(HashUtil.hashRow(a), HashUtil.hashRow(b));
    }

    @Test
    void nullValue_doesNotThrow() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", "John Smith");
        row.put("email", null);

        assertEquals(64, HashUtil.hashRow(row).length(), "SHA-256 hex digest should always be 64 chars");
    }
}
