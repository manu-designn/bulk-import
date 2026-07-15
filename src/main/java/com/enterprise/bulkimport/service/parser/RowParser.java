package com.enterprise.bulkimport.service.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;


public interface RowParser {

   
    boolean supports(String extensionNoDot);

    RowIterator parse(Path file) throws IOException;

    interface RowIterator extends Iterator<Map<String, String>>, AutoCloseable {
        @Override
        void close();
    }
}
