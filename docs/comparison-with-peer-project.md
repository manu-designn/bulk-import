# Comparison: Our Implementation vs. Peer Implementation

Assignment: Bulk Data Import & Validation System – Advanced Spring Boot Assignment
Compared against: Praveena Prasath's implementation (`Bulk-Data-Import-System.zip`)
Date: 14/07/2026

## Summary

Both projects implement the same assignment brief. This document records
where the two differ, and what (if anything) was adopted from the peer
project as a result.

## Features added to our project after this comparison

| Feature | Reason |
|---|---|
| `GET /api/v1/imports` (list all jobs, paginated/sortable) | Genuine gap — we only supported fetching one job by id. |
| Spring Boot Actuator (`/actuator/health`) | Low-cost addition expected of a "production-quality" system. |

## Where our implementation was already ahead (kept as-is)

| Area | Peer implementation | Our implementation |
|---|---|---|
| File streaming | Parses CSV/XLSX fully into an in-memory `List<String>` before processing | True streaming: CSV via buffered `Reader`, XLSX via POI SAX + bounded queue — neither loads the full file into memory |
| Field validation | Manual comma-split + regex per field | Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Min/@Max`) via `PersonImportRow`, as the spec's Technical Requirements explicitly call for |
| File hashing (idempotency) | `file.getBytes()` — buffers the entire file into memory just to hash it | SHA-256 computed in the same I/O pass as streaming the file to disk (`DigestOutputStream`), no extra full-file buffering |
| Duplicate detection | File-level only (whole-file SHA-256) | File-level **and** row-level (per-row content hash, normalized/order-independent) |
| Async processing | `@Async` method exists but is never injected or called from the upload path — processing is fully synchronous in practice despite `@EnableAsync` being present | `@Async` correctly wired via a dedicated executor bean (`AsyncConfig`) with `CallerRunsPolicy` backpressure; upload returns immediately and processing genuinely runs off-thread |
| Batch inserts | Batch-insert code exists in the same unused/uninjected service as the async method | `saveAll()` flush every N rows (configurable), actually exercised in the real processing path |
| Schema management | `ddl-auto=update` against MySQL (schema drift risk) | Flyway-versioned migrations |
| Legacy Excel | `.xlsx` only | `.xlsx` (streaming) and `.xls` (documented non-streaming fallback) |
| Status modeling | One shared `ImportStatus` enum reused for both job and row, conflating job-level states (e.g. `PROCESSING`) with row-level outcomes | Separate `JobStatus` and `RecordStatus` enums |
| Tests | Only the generated `contextLoads()` stub | 9 test classes covering hashing, validation, parsing, and the async/batch processing logic |
| Secrets | DB password committed in plaintext in `application.properties` | DB credentials read from `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env vars, not hardcoded |

## Not adopted, and why

- **Field-name-pattern validation** (matching rules by column name like
  `age`, `email`, `mobile` rather than one fixed DTO) — more schema-flexible
  in principle, but the peer's version is regex/string-splitting, not Bean
  Validation, so it doesn't meet the spec's explicit validation requirement.
  If per-upload schema flexibility is wanted later, it should be built as a
  configurable rule set layered on top of Bean Validation, not a replacement
  for it. Not attempted in this pass.
