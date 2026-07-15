package com.enterprise.bulkimport.controller;

import com.enterprise.bulkimport.dto.ImportJobResponse;
import com.enterprise.bulkimport.dto.ImportRecordResponse;
import com.enterprise.bulkimport.dto.ImportSummaryResponse;
import com.enterprise.bulkimport.service.ImportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Bulk Import", description = "Upload and track bulk CSV/Excel data imports")
public class ImportController {

    private final ImportJobService importJobService;

    public ImportController(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Upload a CSV/Excel file to start an import job",
            description = "Streams and validates the file asynchronously. Re-uploading an " +
                    "identical file (same content hash) returns the existing job instead of " +
                    "starting a new one.")
    public ResponseEntity<ImportJobResponse> uploadFile(
            @Parameter(description = "CSV or Excel file (.csv, .xls, .xlsx)")
            @RequestParam("file") MultipartFile file) {
        ImportJobResponse response = importJobService.submitImport(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get the current status of an import job")
    public ResponseEntity<ImportJobResponse> getJob(@PathVariable String jobId) {
        return ResponseEntity.ok(importJobService.getJob(jobId));
    }

    @GetMapping
    @Operation(summary = "List all import jobs",
            description = "Paginated/sortable, e.g. ?page=0&size=20&sort=createdAt,desc")
    public ResponseEntity<Page<ImportJobResponse>> getAllJobs(Pageable pageable) {
        return ResponseEntity.ok(importJobService.getAllJobs(pageable));
    }

    @GetMapping("/{jobId}/records")
    @Operation(summary = "Get a paginated list of records for a job",
            description = "Supports standard Spring pagination params: page, size, sort. " +
                    "Kept paginated so a 100k+ record job never returns everything at once.")
    public ResponseEntity<Page<ImportRecordResponse>> getRecords(
            @PathVariable String jobId, Pageable pageable) {
        return ResponseEntity.ok(importJobService.getRecords(jobId, pageable));
    }

    @GetMapping("/{jobId}/summary")
    @Operation(summary = "Get success/failed/duplicate counts for a job")
    public ResponseEntity<ImportSummaryResponse> getSummary(@PathVariable String jobId) {
        return ResponseEntity.ok(importJobService.getSummary(jobId));
    }
}
