package com.enterprise.bulkimport.service.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvRowParserTest {

    private final CsvRowParser parser = new CsvRowParser();

    @Test
    void supports_onlyCsvExtension() {
        assertTrue(parser.supports("csv"));
        assertTrue(parser.supports("CSV"));
        assertFalse(parser.supports("xlsx"));
        assertFalse(parser.supports(""));
    }

    @Test
    void parsesRowsInOrder_asHeaderKeyedMaps(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("sample.csv");
        Files.writeString(file, """
                name,email,age,department
                John Smith,john.smith@example.com,34,Engineering
                Priya Sharma,priya.sharma@example.com,29,Marketing
                """, StandardCharsets.UTF_8);

        List<Map<String, String>> rows = new ArrayList<>();
        try (RowParser.RowIterator it = parser.parse(file)) {
            while (it.hasNext()) {
                rows.add(it.next());
            }
        }

        assertEquals(2, rows.size());
        assertEquals("John Smith", rows.get(0).get("name"));
        assertEquals("john.smith@example.com", rows.get(0).get("email"));
        assertEquals("Priya Sharma", rows.get(1).get("name"));
    }

    @Test
    void emptyFile_producesNoRows(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("headerOnly.csv");
        Files.writeString(file, "name,email,age,department\n", StandardCharsets.UTF_8);

        int count = 0;
        try (RowParser.RowIterator it = parser.parse(file)) {
            while (it.hasNext()) {
                it.next();
                count++;
            }
        }

        assertEquals(0, count);
    }

    @Test
    void blankLinesAreIgnored(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("withBlankLines.csv");
        Files.writeString(file, """
                name,email,age,department
                John Smith,john.smith@example.com,34,Engineering

                Priya Sharma,priya.sharma@example.com,29,Marketing
                """, StandardCharsets.UTF_8);

        int count = 0;
        try (RowParser.RowIterator it = parser.parse(file)) {
            while (it.hasNext()) {
                it.next();
                count++;
            }
        }

        assertEquals(2, count, "blank lines should not be counted as data rows");
    }
}
