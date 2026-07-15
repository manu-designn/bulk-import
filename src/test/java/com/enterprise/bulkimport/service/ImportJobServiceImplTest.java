package com.enterprise.bulkimport.service;

import com.enterprise.bulkimport.dto.ImportJobResponse;
import com.enterprise.bulkimport.entity.ImportJob;
import com.enterprise.bulkimport.enums.JobStatus;
import com.enterprise.bulkimport.exception.JobNotFoundException;
import com.enterprise.bulkimport.repository.ImportJobRepository;
import com.enterprise.bulkimport.repository.ImportRecordRepository;
import com.enterprise.bulkimport.service.parser.CsvRowParser;
import com.enterprise.bulkimport.service.parser.RowParserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DAY 4: submitImport now just stores the file, creates the job, and hands
 * off to AsyncImportProcessor — these tests verify that handoff and the
 * idempotency short-circuit, not row-level processing logic (that moved to
 * AsyncImportProcessorTest, since it's a different bean's responsibility now).
 */
@ExtendWith(MockitoExtension.class)
class ImportJobServiceImplTest {

    @Mock
    private ImportJobRepository importJobRepository;
    @Mock
    private ImportRecordRepository importRecordRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private RowParserFactory rowParserFactory;
    @Mock
    private AsyncImportProcessor asyncImportProcessor;

    private ImportJobServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new ImportJobServiceImpl(
                importJobRepository, importRecordRepository, fileStorageService,
                rowParserFactory, asyncImportProcessor);
    }

    @Test
    void submitImport_newFile_createsPendingJob_andDelegatesToAsyncProcessor() throws IOException {
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, "name,email,age,department\nJohn Smith,john@example.com,34,Eng\n");
        MockMultipartFile upload = new MockMultipartFile("file", "test.csv", "text/csv", "x".getBytes());

        when(rowParserFactory.forFile("test.csv")).thenReturn(new CsvRowParser());
        when(fileStorageService.store(eq(upload), anyString()))
                .thenReturn(new FileStorageService.StoredFile(csvFile, "hash123", 100L));
        when(importJobRepository.findByFileHash("hash123")).thenReturn(Optional.empty());
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportJobResponse response = service.submitImport(upload);

        assertEquals(JobStatus.PENDING, response.getStatus(),
                "submitImport should return immediately with PENDING, not wait for processing");
        assertNull(response.getTotalRecords(), "counts aren't known yet at PENDING time");

        verify(asyncImportProcessor).processFileAsync(response.getId());
        verify(importRecordRepository, never()).save(any());
        verify(importRecordRepository, never()).saveAll(any());
    }

    @Test
    void submitImport_sameFileHashAlreadyImported_returnsExistingJob_withoutTouchingProcessor() throws IOException {
        Path csvFile = tempDir.resolve("dupe.csv");
        Files.writeString(csvFile, "name,email,age,department\n");
        MockMultipartFile upload = new MockMultipartFile("file", "dupe.csv", "text/csv", "x".getBytes());

        ImportJob existingJob = ImportJob.builder()
                .id("existing-id")
                .fileName("dupe.csv")
                .status(JobStatus.COMPLETED)
                .totalRecords(10)
                .successCount(10)
                .failedCount(0)
                .duplicateCount(0)
                .build();

        when(rowParserFactory.forFile("dupe.csv")).thenReturn(new CsvRowParser());
        when(fileStorageService.store(eq(upload), anyString()))
                .thenReturn(new FileStorageService.StoredFile(csvFile, "existingHash", 10L));
        when(importJobRepository.findByFileHash("existingHash")).thenReturn(Optional.of(existingJob));

        ImportJobResponse response = service.submitImport(upload);

        assertEquals("existing-id", response.getId());
        assertEquals(JobStatus.COMPLETED, response.getStatus());
        verify(fileStorageService).delete(csvFile);
        verify(asyncImportProcessor, never()).processFileAsync(any());
        verify(importJobRepository, never()).save(any());
    }

    @Test
    void getJob_unknownId_throwsJobNotFoundException() {
        when(importJobRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(JobNotFoundException.class, () -> service.getJob("missing"));
    }

    @Test
    void getAllJobs_returnsPagedMappedResults() {
        ImportJob job = ImportJob.builder()
                .id("job-1").fileName("a.csv").status(JobStatus.COMPLETED)
                .totalRecords(5).successCount(5).failedCount(0).duplicateCount(0)
                .build();
        when(importJobRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(job)));

        var page = service.getAllJobs(org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("job-1", page.getContent().get(0).getId());
    }

    @Test
    void getSummary_unknownId_throwsJobNotFoundException() {
        when(importJobRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(JobNotFoundException.class, () -> service.getSummary("missing"));
    }
}
