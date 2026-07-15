package com.enterprise.bulkimport.service.parser;

import com.enterprise.bulkimport.exception.UnsupportedFileTypeException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RowParserFactory {

    private final List<RowParser> parsers;

    public RowParserFactory(List<RowParser> parsers) {
        this.parsers = parsers;
    }

    public RowParser forFile(String fileName) {
        String ext = extensionOf(fileName);
        return parsers.stream()
                .filter(p -> p.supports(ext))
                .findFirst()
                .orElseThrow(() -> new UnsupportedFileTypeException(fileName));
    }

    private String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1) : "";
    }
}
