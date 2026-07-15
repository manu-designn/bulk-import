package com.enterprise.bulkimport.service.parser;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;


@Component
public class CsvRowParser implements RowParser {

    @Override
    public boolean supports(String extensionNoDot) {
        return "csv".equalsIgnoreCase(extensionNoDot);
    }

    @Override
    public RowIterator parse(Path file) throws IOException {
        BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        CSVParser csvParser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(true)
                .setAllowDuplicateHeaderNames(true)
                .build()
                .parse(reader);

        Iterator<CSVRecord> delegate = csvParser.iterator();

        return new RowIterator() {
            @Override
            public boolean hasNext() {
                try {
                    return delegate.hasNext();
                } catch (UncheckedIOException e) {
                    throw e;
                }
            }

            @Override
            public Map<String, String> next() {
                CSVRecord record = delegate.next();
                Map<String, String> row = new LinkedHashMap<>();
                record.toMap().forEach(row::put);
                return row;
            }

            @Override
            public void close() {
                try {
                    csvParser.close();
                    reader.close();
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        };
    }
}
