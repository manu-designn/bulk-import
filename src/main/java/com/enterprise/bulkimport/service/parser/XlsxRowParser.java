package com.enterprise.bulkimport.service.parser;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;


@Component
public class XlsxRowParser implements RowParser {

    private static final Object END_OF_STREAM = new Object();
    private static final int QUEUE_CAPACITY = 200;

    @Override
    public boolean supports(String extensionNoDot) {
        return "xlsx".equalsIgnoreCase(extensionNoDot);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RowIterator parse(Path file) throws IOException {
        OPCPackage pkg;
        try {
            pkg = OPCPackage.open(file.toFile(), org.apache.poi.openxml4j.opc.PackageAccess.READ);
        } catch (Exception e) {
            throw new IOException("Could not open .xlsx file: " + e.getMessage(), e);
        }

        BlockingQueue<Object> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        AtomicReference<Exception> producerError = new AtomicReference<>();

        Thread producer = new Thread(() -> {
            try {
                XSSFReader reader = new XSSFReader(pkg);
                SharedStrings sst = reader.getSharedStringsTable();
                StylesTable styles = reader.getStylesTable();

                SheetContentsHandler sheetHandler = new QueueingSheetHandler(queue);
                ContentHandler handler = new XSSFSheetXMLHandler(styles, sst, sheetHandler, false);

                XMLReader xmlReader = XMLHelper.newXMLReader();
                xmlReader.setContentHandler(handler);

                java.util.Iterator<InputStream> sheets = reader.getSheetsData();
                if (sheets.hasNext()) {
                    // Only the first sheet is imported — matches the spec's single
                    // tabular "file" model. A multi-sheet import mode would be a
                    // reasonable future extension, not needed for this assignment.
                    try (InputStream sheetStream = sheets.next()) {
                        xmlReader.parse(new InputSource(sheetStream));
                    }
                }
            } catch (Exception e) {
                producerError.set(e);
            } finally {
                try {
                    pkg.close();
                } catch (Exception ignored) {
                }
                try {
                    queue.put(END_OF_STREAM);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "xlsx-row-parser");
        producer.setDaemon(true);
        producer.start();

        return new RowIterator() {
            private Object nextItem;
            private boolean finished = false;

            private void advance() {
                if (finished) return;
                try {
                    nextItem = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    nextItem = END_OF_STREAM;
                }
                if (nextItem == END_OF_STREAM) {
                    finished = true;
                    if (producerError.get() != null) {
                        throw new RuntimeException("Failed while streaming .xlsx rows",
                                producerError.get());
                    }
                }
            }

            @Override
            public boolean hasNext() {
                if (nextItem == null && !finished) {
                    advance();
                }
                return nextItem != null && nextItem != END_OF_STREAM;
            }

            @Override
            public Map<String, String> next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                Map<String, String> row = (Map<String, String>) nextItem;
                nextItem = null;
                return row;
            }

            @Override
            public void close() {
                // The producer thread owns pkg and closes it in its own finally
                // block once it stops (either by finishing normally or being
                // interrupted here). Not touching pkg from this thread avoids a
                // double-close race between the two threads.
                producer.interrupt();
            }
        };
    }

    /**
     * Buffers cells for the row currently being parsed, and on row-end either
     * captures the header (row 0) or emits a header-keyed row map onto the
     * queue for the consumer thread to pick up.
     */
    private static class QueueingSheetHandler implements SheetContentsHandler {
        private final BlockingQueue<Object> queue;
        private List<String> headers;
        private final Map<Integer, String> currentRowCells = new LinkedHashMap<>();
        private int currentRowNum = -1;

        QueueingSheetHandler(BlockingQueue<Object> queue) {
            this.queue = queue;
        }

        @Override
        public void startRow(int rowNum) {
            currentRowNum = rowNum;
            currentRowCells.clear();
        }

        @Override
        public void endRow(int rowNum) {
            if (rowNum == 0) {
                headers = new ArrayList<>();
                int maxCol = currentRowCells.keySet().stream().mapToInt(i -> i).max().orElse(-1);
                for (int i = 0; i <= maxCol; i++) {
                    String h = currentRowCells.get(i);
                    headers.add(h == null || h.isBlank() ? ("column_" + i) : h);
                }
                return;
            }
            if (headers == null) {
                // Sheet had no header row at all — nothing sensible to key rows by.
                return;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                row.put(headers.get(i), currentRowCells.getOrDefault(i, ""));
            }
            put(row);
        }

        @Override
        public void cell(String cellReference, String formattedValue, org.apache.poi.xssf.usermodel.XSSFComment comment) {
            int col = new CellReference(cellReference).getCol();
            currentRowCells.put(col, formattedValue == null ? "" : formattedValue);
        }

        private void put(Object item) {
            try {
                queue.put(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
