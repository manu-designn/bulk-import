package com.enterprise.bulkimport.service;

import com.enterprise.bulkimport.dto.ImportJobResponse;
import com.enterprise.bulkimport.dto.ImportRecordResponse;
import com.enterprise.bulkimport.dto.ImportSummaryResponse;
import com.enterprise.bulkimport.entity.ImportJob;
import com.enterprise.bulkimport.entity.ImportRecord;
import com.enterprise.bulkimport.enums.JobStatus;
import com.enterprise.bulkimport.exception.EmptyFileException;
import com.enterprise.bulkimport.exception.JobNotFoundException;
import com.enterprise.bulkimport.repository.ImportJobRepository;
import com.enterprise.bulkimport.repository.ImportRecordRepository;
import com.enterprise.bulkimport.service.parser.RowParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;


@Service
public class ImportJobServiceImpl implements ImportJobService {

    private static final Logger log = LoggerFactory.getLogger(ImportJobServiceImpl.class);

    private final ImportJobRepository importJobRepository;
    private final ImportRecordRepository importRecordRepository;
    private final FileStorageService fileStorageService;
    private final RowParserFactory rowParserFactory;
    private final AsyncImportProcessor asyncImportProcessor;

    public ImportJobServiceImpl(ImportJobRepository importJobRepository,
                                 ImportRecordRepository importRecordRepository,
                                 FileStorageService fileStorageService,
                                 RowParserFactory rowParserFactory,
                                 AsyncImportProcessor asyncImportProcessor) {
        this.importJobRepository = importJobRepository;
        this.importRecordRepository = importRecordRepository;
        this.fileStorageService = fileStorageService;
        this.rowParserFactory = rowParserFactory;
        this.asyncImportProcessor = asyncImportProcessor;
    }

    @Override
    public ImportJobResponse submitImport(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyFileException();
        }
        String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();

        // Fail fast on an unsupported extension before spending any I/O storing
        // the file. The parser instance itself is discarded here — the async
        // processor looks it up again by filename when it actually runs, since
        // that happens on a different thread at a later time.
        rowParserFactory.forFile(fileName);

        String jobId = UUID.randomUUID().toString();
        FileStorageService.StoredFile stored = fileStorageService.store(file, jobId);

        // Idempotency: same file content (by hash), regardless of filename, is not reprocessed.
        Optional<ImportJob> existing = importJobRepository.findByFileHash(stored.sha256Hex());
        if (existing.isPresent()) {
            fileStorageService.delete(stored.path());
            log.info("Duplicate upload detected (hash={}), returning existing job {}",
                    stored.sha256Hex(), existing.get().getId());
            return toJobResponse(existing.get());
        }

        ImportJob job = ImportJob.builder()
                .id(jobId)
                .fileName(fileName)
                .storedFilePath(stored.path().toString())
                .fileHash(stored.sha256Hex())
                .fileSizeBytes(stored.sizeBytes())
                .status(JobStatus.PENDING)
                .build();
        job = importJobRepository.save(job);

        asyncImportProcessor.processFileAsync(job.getId());

        return toJobResponse(job);
    }

    @Override
    public ImportJobResponse getJob(String jobId) {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return toJobResponse(job);
    }

    @Override
    public Page<ImportJobResponse> getAllJobs(Pageable pageable) {
        return importJobRepository.findAll(pageable).map(this::toJobResponse);
    }

    @Override
    public Page<ImportRecordResponse> getRecords(String jobId, Pageable pageable) {
        if (!importJobRepository.existsById(jobId)) {
            throw new JobNotFoundException(jobId);
        }
        return importRecordRepository.findByJobId(jobId, pageable).map(this::toRecordResponse);
    }

    @Override
    public ImportSummaryResponse getSummary(String jobId) {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return ImportSummaryResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .totalRecords(zeroIfNull(job.getTotalRecords()))
                .successCount(zeroIfNull(job.getSuccessCount()))
                .failedCount(zeroIfNull(job.getFailedCount()))
                .duplicateCount(zeroIfNull(job.getDuplicateCount()))
                .build();
    }

    private ImportJobResponse toJobResponse(ImportJob job) {
        return ImportJobResponse.builder()
                .id(job.getId())
                .fileName(job.getFileName())
                .status(job.getStatus())
                .totalRecords(job.getTotalRecords())
                .successCount(job.getSuccessCount())
                .failedCount(job.getFailedCount())
                .duplicateCount(job.getDuplicateCount())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }

    private ImportRecordResponse toRecordResponse(ImportRecord record) {
        return ImportRecordResponse.builder()
                .id(record.getId())
                .rowNumber(record.getRowNumber())
                .data(record.getData())
                .status(record.getStatus())
                .errorMessage(record.getErrorMessage())
                .build();
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
