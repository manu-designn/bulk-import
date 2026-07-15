package com.enterprise.bulkimport.service;

import com.enterprise.bulkimport.config.ImportProperties;
import com.enterprise.bulkimport.entity.ImportJob;
import com.enterprise.bulkimport.entity.ImportRecord;
import com.enterprise.bulkimport.enums.JobStatus;
import com.enterprise.bulkimport.repository.ImportJobRepository;
import com.enterprise.bulkimport.repository.ImportRecordRepository;
import com.enterprise.bulkimport.service.parser.CsvRowParser;
import com.enterprise.bulkimport.service.parser.RowParserFactory;
import com.enterprise.bulkimport.service.validation.RowValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Note: calling processFileAsync(...) directly here runs synchronously,
 * since @Async only takes effect through Spring's proxy — that's fine for
 * unit-testing the row-processing logic itself, just means these tests
 * don't exercise the actual threading/executor behavior (AsyncConfig).
 */
@ExtendWith(MockitoExtension.class)
class AsyncImportProcessorTest {

    @Mock
    private ImportJobRepository importJobRepository;
    @Mock
    private ImportRecordRepository importRecordRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private RowParserFactory rowParserFactory;

    private ImportProperties importProperties;
    private AsyncImportProcessor processor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        RowValidator rowValidator = new RowValidator(validator);
        importProperties = new ImportProperties();
        processor = new AsyncImportProcessor(
                importJobRepository, importRecordRepository, fileStorageService,
                rowParserFactory, rowValidator, new ObjectMapper(), importProperties);
    }

    private ImportJob pendingJobFor(Path file, String fileName) {
        return ImportJob.builder()
                .id("job-1")
                .fileName(fileName)
                .storedFilePath(file.toString())
                .fileHash("somehash")
                .status(JobStatus.PENDING)
                .build();
    }

    @Test
    void processFileAsync_mixedRows_rollsUpToPartiallyCompletedWithCorrectCounts() throws IOException {
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, """
                name,email,age,department
                John Smith,john.smith@example.com,34,Engineering
                ,missing.name@example.com,29,Marketing
                John Smith,john.smith@example.com,34,Engineering
                """);
        ImportJob job = pendingJobFor(csvFile, "test.csv");

        when(importJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(rowParserFactory.forFile("test.csv")).thenReturn(new CsvRowParser());
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        processor.processFileAsync("job-1");

        assertEquals(JobStatus.PARTIALLY_COMPLETED, job.getStatus());
        assertEquals(3, job.getTotalRecords());
        assertEquals(1, job.getSuccessCount());
        assertEquals(1, job.getFailedCount());
        assertEquals(1, job.getDuplicateCount());
        assertNotNull(job.getStartedAt());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void processFileAsync_allValidRows_completesCleanly() throws IOException {
        Path csvFile = tempDir.resolve("clean.csv");
        Files.writeString(csvFile, """
                name,email,age,department
                John Smith,john.smith@example.com,34,Engineering
                Priya Sharma,priya.sharma@example.com,29,Marketing
                """);
        ImportJob job = pendingJobFor(csvFile, "clean.csv");

        when(importJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(rowParserFactory.forFile("clean.csv")).thenReturn(new CsvRowParser());
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        processor.processFileAsync("job-1");

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(2, job.getSuccessCount());
        assertEquals(0, job.getFailedCount());
    }

    @Test
    void batching_flushesInChunksOfConfiguredSize_notOnePerRow() throws IOException {
        importProperties.setRecordBatchSize(2); // force multiple flushes for a 5-row file

        StringBuilder csv = new StringBuilder("name,email,age,department\n");
        for (int i = 0; i < 5; i++) {
            csv.append("Person").append(i).append(",person").append(i)
                    .append("@example.com,30,Eng\n");
        }
        Path csvFile = tempDir.resolve("batched.csv");
        Files.writeString(csvFile, csv.toString());
        ImportJob job = pendingJobFor(csvFile, "batched.csv");

        when(importJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(rowParserFactory.forFile("batched.csv")).thenReturn(new CsvRowParser());
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        processor.processFileAsync("job-1");

        // 5 rows, batch size 2 -> flushes of 2, 2, 1 = 3 saveAll calls, never a single save() per row
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImportRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(importRecordRepository, times(3)).saveAll(captor.capture());
        verify(importRecordRepository, never()).save(any());

        List<Integer> batchSizes = captor.getAllValues().stream().map(List::size).toList();
        assertEquals(List.of(2, 2, 1), batchSizes);
        assertEquals(5, job.getSuccessCount());
    }

    @Test
    void unknownJobId_doesNothing() {
        when(importJobRepository.findById("missing")).thenReturn(Optional.empty());

        processor.processFileAsync("missing");

        verify(rowParserFactory, never()).forFile(any());
        verify(importRecordRepository, never()).save(any());
        verify(importRecordRepository, never()).saveAll(any());
    }
}
