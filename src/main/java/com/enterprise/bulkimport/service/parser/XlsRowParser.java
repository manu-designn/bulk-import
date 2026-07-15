package com.enterprise.bulkimport.service.parser;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Component
public class XlsRowParser implements RowParser {

    @Override
    public boolean supports(String extensionNoDot) {
        return "xls".equalsIgnoreCase(extensionNoDot);
    }

    @Override
    public RowIterator parse(Path file) throws IOException {
        InputStream in = Files.newInputStream(file);
        Workbook workbook = new HSSFWorkbook(in);
        Sheet sheet = workbook.getSheetAt(0);
        Iterator<Row> rowIterator = sheet.iterator();

        List<String> headers = new ArrayList<>();
        if (rowIterator.hasNext()) {
            Row headerRow = rowIterator.next();
            for (Cell cell : headerRow) {
                String h = cell.getStringCellValue();
                headers.add(h == null || h.isBlank() ? ("column_" + cell.getColumnIndex()) : h);
            }
        }

        DataFormatter formatter = new DataFormatter();

        return new RowIterator() {
            @Override
            public boolean hasNext() {
                return rowIterator.hasNext();
            }

            @Override
            public Map<String, String> next() {
                Row row = rowIterator.next();
                Map<String, String> result = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    result.put(headers.get(i), cell == null ? "" : formatter.formatCellValue(cell));
                }
                return result;
            }

            @Override
            public void close() {
                try {
                    workbook.close();
                    in.close();
                } catch (IOException ignored) {
                }
            }
        };
    }
}
