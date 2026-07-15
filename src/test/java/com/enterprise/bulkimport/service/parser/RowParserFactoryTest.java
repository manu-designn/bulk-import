package com.enterprise.bulkimport.service.parser;

import com.enterprise.bulkimport.exception.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowParserFactoryTest {

    private final RowParserFactory factory =
            new RowParserFactory(List.of(new CsvRowParser(), new XlsxRowParser(), new XlsRowParser()));

    @Test
    void picksCorrectParserByExtension() {
        assertTrue(factory.forFile("data.csv") instanceof CsvRowParser);
        assertTrue(factory.forFile("data.xlsx") instanceof XlsxRowParser);
        assertTrue(factory.forFile("data.xls") instanceof XlsRowParser);
    }

    @Test
    void extensionIsCaseInsensitive() {
        assertTrue(factory.forFile("DATA.CSV") instanceof CsvRowParser);
    }

    @Test
    void unsupportedExtension_throws() {
        assertThrows(UnsupportedFileTypeException.class, () -> factory.forFile("data.pdf"));
    }

    @Test
    void noExtension_throws() {
        assertThrows(UnsupportedFileTypeException.class, () -> factory.forFile("data"));
    }

    @Test
    void nullFileName_throws() {
        assertThrows(UnsupportedFileTypeException.class, () -> factory.forFile(null));
    }
}
