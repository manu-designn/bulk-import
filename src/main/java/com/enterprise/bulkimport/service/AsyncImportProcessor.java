package com.enterprise.bulkimport.service;

import com.enterprise.bulkimport.config.ImportProperties;
import com.enterprise.bulkimport.entity.ImportJob;
import com.enterprise.bulkimport.entity.ImportRecord;
import com.enterprise.bulkimport.enums.JobStatus;
import com.enterprise.bulkimport.enums.RecordStatus;
import com.enterprise.bulkimport.repository.ImportJobRepository;
import com.enterprise.bulkimport.repository.ImportRecordRepository;
import com.enterprise.bulkimport.service.parser.RowParser;
import com.enterprise.bulkimport.service.parser.RowParserFactory;
import com.enterprise.bulkimport.service.validation.RowValidator;
import com.enterprise.bulkimport.util.HashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
public class AsyncImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncImportProcessor.class);

    private final ImportJobRepository importJobRepository;
    private final ImportRecordRepository importRecordRepository;
    private final FileStorageService fileStorageService;
    private final RowParserFactory rowParserFactory;
    private final RowValidator rowValidator;
    private final ObjectMapper objectMapper;
    private final ImportProperties importProperties;

    public AsyncImportProcessor(ImportJobRepository importJobRepository,
                                 ImportRecordRepository importRecordRepository,
                                 FileStorageService fileStorageService,
                                 RowParserFactory rowParserFactory,
                                 RowValidator rowValidator,
                                 ObjectMapper objectMapper,
                                 ImportProperties importProperties) {
        this.importJobRepository = importJobRepository;
        this.importRecordRepository = importRecordRepository;
        this.fileStorageService = fileStorageService;
        this.rowParserFactory = rowParserFactory;
        this.rowValidator = rowValidator;
        this.objectMapper = objectMapper;
        this.importProperties = importProperties;
    }

    @Async("importTaskExecutor")
    public void processFileAsync(String jobId) {
        ImportJob job = importJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            // Shouldn't happen (the job was just persisted by the caller before
            // this was scheduled) but guards against a bad jobId being passed.
            log.warn("processFileAsync invoked for unknown job id {}", jobId);
            return;
        }

        RowParser parser;
        try {
            parser = rowParserFactory.forFile(job.getFileName());
        } catch (Exception e) {
            failJob(job, "Could not determine file parser: " + e.getMessage());
            return;
        }

        processFile(job, parser);
    }

    private void processFile(ImportJob job, RowParser parser) {
        job.setStatus(JobStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        importJobRepository.save(job);

        int rowNumber = 0;
        int successCount = 0;
        int failedCount = 0;
        int duplicateCount = 0;

        // See Day 2-3 note on this trade-off (fine to ~100k rows/job; would
        // need a DB-backed check for millions-of-rows jobs).
        Set<String> seenRowHashes = new HashSet<>();

        int batchSize = Math.max(1, importProperties.getRecordBatchSize());
        List<ImportRecord> buffer = new ArrayList<>(batchSize);

        try (RowParser.RowIterator rows = parser.parse(Path.of(job.getStoredFilePath()))) {
            while (rows.hasNext()) {
                rowNumber++;
                Map<String, String> raw = rows.next();
                String recordHash = HashUtil.hashRow(raw);

                ImportRecord record = ImportRecord.builder()
                        .job(job)
                        .rowNumber(rowNumber)
                        .data(toJson(raw))
                        .recordHash(recordHash)
                        .build();

                if (!seenRowHashes.add(recordHash)) {
                    record.setStatus(RecordStatus.DUPLICATE);
                    record.setErrorMessage("Duplicate of an earlier row in this file");
                    duplicateCount++;
                } else {
                    RowValidator.ValidationOutcome outcome = rowValidator.validate(raw);
                    if (outcome.valid()) {
                        record.setStatus(RecordStatus.PERSISTED);
                        successCount++;
                    } else {
                        record.setStatus(RecordStatus.INVALID);
                        record.setErrorMessage(String.join("; ", outcome.errors()));
                        failedCount++;
                    }
                }

                buffer.add(record);
                if (buffer.size() >= batchSize) {
                    flush(buffer);
                }
            }
            flush(buffer); // final partial batch
        } catch (Exception e) {
            log.error("Import job {} failed while processing '{}'", job.getId(), job.getFileName(), e);
            flush(buffer); // keep whatever was already parsed/validated before the failure
            failJob(job, "Processing failed at row " + rowNumber + ": " + e.getMessage());
            return;
        }

        job.setTotalRecords(rowNumber);
        job.setSuccessCount(successCount);
        job.setFailedCount(failedCount);
        job.setDuplicateCount(duplicateCount);
        job.setCompletedAt(LocalDateTime.now());
        job.setStatus(failedCount > 0 || duplicateCount > 0 ? JobStatus.PARTIALLY_COMPLETED : JobStatus.COMPLETED);
        importJobRepository.save(job);

        if (importProperties.isDeleteAfterProcessing()) {
            fileStorageService.delete(Path.of(job.getStoredFilePath()));
        }

        log.info("Import job {} finished: total={} success={} failed={} duplicate={} (batchSize={})",
                job.getId(), rowNumber, successCount, failedCount, duplicateCount, batchSize);
    }

    private void flush(List<ImportRecord> buffer) {
        if (buffer.isEmpty()) return;
        importRecordRepository.saveAll(buffer);
        buffer.clear();
    }

    private void failJob(ImportJob job, String message) {
        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(message);
        job.setCompletedAt(LocalDateTime.now());
        importJobRepository.save(job);
    }

    private String toJson(Map<String, String> raw) {
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize row to JSON", e);
        }
    }
}
