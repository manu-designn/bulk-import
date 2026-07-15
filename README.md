# Bulk Data Import & Validation System

Spring Boot backend for uploading large CSV/Excel files, validating each row,
persisting valid records, and reporting an import summary.

## Status: COMPLETE (Day 6 of 6)

| Day | Scope | Status |
|-----|-------|--------|
| 1 | API + DB design | ✅ |
| 2–3 | File processing + validation | ✅ |
| 3 | Tests + edge-case hardening | ✅ |
| 4 | Async + batching | ✅ |
| 4+ | List-jobs endpoint + Actuator (post-comparison additions) | ✅ |
| 5–6 | Optimization, stale-job recovery, Postman collection, final docs | ✅ this delivery |

### Deliverables checklist (against the original assignment brief)

- [x] Spring Boot project source code
- [x] API documentation — Swagger UI (`/swagger-ui.html`, customized via
      `OpenApiConfig`) **and** a Postman collection (`docs/bulk-import.postman_collection.json`)
- [x] Sample input files (`sample-data/`)
- [x] Database schema (`src/main/resources/db/migration/V1__init_schema.sql`)
- [x] README with setup and execution steps (this file)

## What was added today (Day 5-6)

- **`StaleJobRecoveryScheduler`** — the one requirement that was still an
  honest gap: "system crash during processing." Full resumability
  (checkpointing exactly which row a crashed job reached and continuing
  from there) is a bigger feature than remaining scope allows, so this
  takes the more modest, honest approach: a `@Scheduled` check runs every 5
  minutes, finds any job that's been `PROCESSING` for longer than
  `app.import.stale-job-timeout-minutes` (default 30), and marks it
  `FAILED` with an explanatory message. Without this, a crashed job would
  sit in `PROCESSING` forever with no visible outcome. Records already
  flushed via batch `saveAll()` before the crash remain in the DB either
  way — this doesn't lose that progress, it just doesn't auto-resume from
  it.
- **`PaginationConfig`** — caps page size at 500 (previously unbounded — a
  client could request `?size=1000000` and defeat the entire point of
  paginating `/records`).
- **HikariCP tuning** (`application.properties`) — explicit pool size
  (max 20, min idle 5) and a short connection timeout (10s) so a saturated
  pool fails fast and visibly instead of requests hanging indefinitely.
- **`OpenApiConfig`** — real title/description on the Swagger page instead
  of springdoc's generic default, including a note that the API is
  asynchronous (submit → poll), since that's easy to miss just from looking
  at the endpoint list.
- **Postman collection** (`docs/bulk-import.postman_collection.json`) —
  covers every endpoint plus three edge cases: re-uploading an unmodified
  file (idempotency), uploading an unsupported file type (415), and
  fetching an unknown job id (404).
- **`StaleJobRecoverySchedulerTest`** — new test for the recovery logic.

## What was added on Day 4+ (post-comparison additions)

Compared this project against a peer's implementation of the same
assignment. Two genuine gaps found and added:

- **`GET /api/v1/imports`** — paginated/sortable list of every import job
  (was previously only possible to fetch one job at a time by id).
- **Spring Boot Actuator** — adds `/actuator/health` for free, a reasonable
  expectation for a "production-quality" system.

Everything else compared favorably already: this project streams both CSV
and XLSX (the peer's version loads the whole file into an in-memory list,
which violates the "no full memory load" requirement), uses real Bean
Validation (the peer used manual string-splitting + regex), hashes files
while streaming to disk (the peer calls `file.getBytes()`, buffering the
whole file just to hash it), detects duplicate rows within a file (the peer
only checks whole-file duplicates), and has `@Async` actually wired in and
called (the peer wrote an `@Async` method that's never injected or invoked
anywhere — upload stays fully synchronous despite `@EnableAsync` being
present). Full write-up: `docs/comparison-with-peer-project.md`.

## What was added on Day 4

Processing was synchronous through Day 3 — `submitImport` blocked until the
whole file was done. That's fixed now:

- **`AsyncImportProcessor`** is a new bean holding everything that used to
  run inline inside `ImportJobServiceImpl.submitImport`. It has to be a
  *separate* bean, not just an `@Async`-annotated method on the same class
  — `@Async` only works through Spring's proxy, and a method calling another
  `@Async` method on itself (self-invocation) bypasses that proxy entirely
  and just runs synchronously with no warning. Splitting it out is the
  standard way around that trap.
- **`AsyncConfig`** defines a dedicated `importTaskExecutor` bean instead of
  relying on Spring Boot's auto-configured default, specifically to set a
  `CallerRunsPolicy`: if the queue (50) and all worker threads (4-8) are
  saturated, the calling thread runs the job itself instead of it being
  rejected. Under a burst of concurrent uploads that means things get
  slower, not that an upload silently vanishes. Also gives the threads a
  `import-async-` name prefix so they're identifiable in thread dumps.
- **`submitImport`** now: stores the file, creates the `ImportJob` row
  (`PENDING`), calls `asyncImportProcessor.processFileAsync(jobId)`, and
  returns immediately. The 202-Accepted response reflects `PENDING` —
  `totalRecords`/`successCount`/etc. are `null` until you poll
  `GET /api/v1/imports/{id}`. This is the contract the API was designed
  around back on Day 1; it just wasn't actually true until now.

### Batch inserts

`AsyncImportProcessor` buffers `ImportRecord`s in a `List` and flushes with
`importRecordRepository.saveAll(buffer)` every `app.import.record-batch-size`
rows (default 500, matching `hibernate.jdbc.batch_size` in
`application.properties` so the application-level and JDBC-level batch sizes
agree) instead of one `save()` per row. The buffer is also flushed on a
mid-file failure (whatever was already validated stays visible) and after
the loop for the final partial batch.

### Tests

- **`AsyncImportProcessorTest`** — the Day 2-3 row-processing tests (mixed
  valid/invalid/duplicate rows, all-valid happy path) moved here since
  that's where the logic lives now. Added a batching-specific test that
  sets `recordBatchSize=2` against a 5-row file and asserts `saveAll()` is
  called exactly 3 times with batch sizes `[2, 2, 1]`, and `save()` (the
  one-row-at-a-time method) is never called at all.
- **`ImportJobServiceImplTest`** — trimmed down to what `submitImport` now
  actually does: creates a `PENDING` job and delegates to
  `AsyncImportProcessor` (verified via mock), or short-circuits on a
  duplicate file hash without touching the processor at all.

## What was added today (Day 3)

### Hardening

- **`CsvRowParser`** now tolerates blank/missing header names and duplicate
  header names (`setAllowMissingColumnNames`, `setAllowDuplicateHeaderNames`)
  instead of throwing during setup on a slightly malformed header row —
  malformed *data* rows still surface as validation errors per-row, but a
  messy header no longer kills the whole job before a single row is read.

### Tests added (`src/test/java`)

- **`HashUtilTest`** — row-hash is order-independent and
  case/whitespace-normalized (so `"John Smith"` and `" john smith "` are
  still caught as the same row), and differing rows hash differently.
- **`RowValidatorTest`** — covers every case in `sample_with_errors.csv`:
  missing name, malformed email, negative age, non-numeric age (mapping
  error, not a constraint violation), missing age, age over 150, and the
  happy path.
- **`CsvRowParserTest`** — rows come back in order and correctly keyed,
  blank lines are skipped, an empty (header-only) file yields zero rows.
- **`RowParserFactoryTest`** — correct parser picked per extension
  (case-insensitively), unsupported/missing/null extensions all throw
  `UnsupportedFileTypeException` rather than an NPE or generic error.
- **`ImportJobServiceImplTest`** — the main one. Mocks the repositories and
  `FileStorageService`, but uses a **real** `CsvRowParser` + `RowValidator`
  against actual temp CSV files, so what's under test is the real
  processing logic, not JPA plumbing:
  - a 3-row file (1 valid, 1 invalid, 1 duplicate) rolls up to
    `PARTIALLY_COMPLETED` with the right counts
  - an all-valid file rolls up to `COMPLETED`
  - re-submitting a file with a hash that already exists returns the
    existing job, never calls the parser, and deletes the redundant temp
    file
  - `getJob`/`getSummary` on an unknown id throw `JobNotFoundException`

Run them with `mvn test`.

## What was added today (Day 2-3)

### Streaming parsers (`service/parser/`)

- **`CsvRowParser`** — Apache Commons CSV over a `BufferedReader`. Streams row
  by row; never materializes the file.
- **`XlsxRowParser`** — the interesting one. `.xlsx` via POI's normal
  `XSSFWorkbook` API loads the whole workbook into memory, which breaks the
  "no full memory load" requirement for large files. Instead this uses POI's
  SAX ("event user model") API (`XSSFReader`), which is push-based — so a
  background thread drives the SAX parse and pushes finished rows onto a
  small bounded `BlockingQueue`, and the returned iterator just drains that
  queue. The bounded queue means the producer blocks once it's 200 rows
  ahead of the consumer, so memory use stays flat regardless of file size.
- **`XlsRowParser`** — legacy binary `.xls`. Documented limitation: HSSF
  doesn't have a practical SAX-equivalent streaming API, so this one *does*
  load the workbook fully via `HSSFWorkbook`. Flagged in the class Javadoc
  rather than silently violating the streaming requirement — for very large
  files, ask for `.xlsx` or `.csv` instead.
- **`RowParserFactory`** picks the right parser by file extension, and is
  also where an unrecognized extension becomes `UnsupportedFileTypeException`
  (Edge Case: invalid file format) before any file I/O happens.

### Validation (`service/validation/`)

- **`PersonImportRow`** — a concrete Bean-Validation-annotated DTO
  (`@NotBlank`, `@Email`, `@Min/@Max`) matching the sample CSV columns
  (name/email/age/department).
  **Trade-off flagged deliberately:** the DB design keeps `ImportRecord.data`
  schema-agnostic (raw JSON) so it can hold any row shape, but *validating*
  against an unknown shape needs something concrete to validate. Building a
  fully dynamic per-upload schema/rule engine is out of scope for a 6-day
  assignment, so this fixes one row schema. A real multi-tenant version would
  make this configurable — noted as a possible Day 5-6 extension, not built.
- **`RowValidator`** — maps the raw `Map<String,String>` row onto
  `PersonImportRow` (catching type errors like a non-numeric age separately
  from constraint violations, since `@Min`/`@Max` can't run on a value that
  never parsed to an `Integer`), then runs it through `jakarta.validation.
  Validator` programmatically (this isn't an HTTP request, so no
  `@Valid`/`@Validated`).

### Wired `ImportJobServiceImpl` for real

- `submitImport`: validates the file isn't empty → picks a parser (fails
  fast on bad extension) → streams the upload to disk while computing its
  SHA-256 in the same pass (`FileStorageService`, one I/O pass, not
  buffer-then-hash) → checks `import_job.file_hash` for an existing job
  (idempotency: re-uploading identical bytes returns the existing job and
  deletes the new temp file) → otherwise creates the job and processes it.
- `processFile`: streams rows via the picked parser; each row gets a
  content hash (`HashUtil.hashRow`, key-sorted + normalized so column order/
  case don't affect it) checked against an in-memory `Set` for
  **within-file duplicate detection** (Edge Case: duplicate records), then
  validated. Every row becomes an `ImportRecord` (`PERSISTED` / `INVALID` /
  `DUPLICATE`) — that table doubles as both the audit trail and the "store
  valid records" target, since the spec doesn't define a separate business
  entity. A parse-level exception (corrupt file, wrong delimiter, etc.)
  marks the whole job `FAILED` with the row number it died at, rather than
  silently swallowing the error.
- `getJob` / `getRecords` / `getSummary` now actually query the DB, mapped
  through the same DTOs designed on Day 1.

### Known Day 2-3 limitations (by design, not oversight)

- **One `save()` per row.** Correct but slow at scale — Day 4 batches these
  with `saveAll()` every N rows.
- **Synchronous end-to-end.** `submitImport` blocks until the whole file is
  processed. The 202-Accepted-then-poll API contract from Day 1 is
  technically premature until `@Async` lands Day 4; left the contract
  as-is rather than changing it twice.
- **No crash recovery** *(as of Day 2-3 — mitigated Day 5-6)*. If the
  process dies mid-file, the job was stuck in `PROCESSING` with no record
  of exactly which row it reached. `StaleJobRecoveryScheduler` (Day 5-6)
  now detects this and marks the job `FAILED` after a timeout instead of
  leaving it stuck forever — but this is detection, not resumption; there's
  still no row-offset checkpoint to resume a crashed job from where it left
  off. Full resumability would be the next step beyond this assignment's
  scope.
- **In-memory dedup set** (`Set<String>` of row hashes) is fine up to
  ~100k rows per job (~6-7MB) but would need to move to a DB-backed check
  for jobs in the millions-of-rows range.



### 1. Database design (`src/main/resources/db/migration/V1__init_schema.sql`)

Two tables, matching the spec's minimum (`ImportJob`, `ImportRecord`) plus
the columns needed to satisfy the Advanced Requirements later:

- **`import_job`** — one row per uploaded file. Beyond `id/file_name/status/
  created_at`, it has `file_hash` (unique) for idempotency, `total/success/
  failed/duplicate_count` for the summary endpoint, and
  `started_at/completed_at` for tracking async runs.
- **`import_record`** — one row per parsed line, FK'd to `import_job` with
  `ON DELETE CASCADE`. `data` is stored as a TEXT/JSON blob rather than fixed
  columns, so the schema doesn't have to change if the imported file's
  columns change. `record_hash` + an index on `(job_id, record_hash)` is what
  duplicate detection will use in Day 2–3.

Indexes are on `status` (for filtering/polling) and on the hash columns (for
idempotency and dedup lookups) — these are the two query patterns a
100k+-row job will hit repeatedly.

Managed with **Flyway**, not `hibernate.ddl-auto=update`, so the schema has a
version history and prod deploys are predictable.

### 2. Entities & repositories

`ImportJob` / `ImportRecord` (JPA entities) plus `ImportJobRepository` /
`ImportRecordRepository`. Record lookups are paginated (`Page<ImportRecord>
findByJobId(...)`) on purpose — Day 1 already rules out ever loading a full
100k-row job into memory to display it.

### 3. API design (`ImportController`)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/v1/imports` | Upload a file, starts (or returns existing) import job |
| GET | `/api/v1/imports` | List all import jobs, paginated/sortable |
| GET | `/api/v1/imports/{jobId}` | Job status/metadata |
| GET | `/api/v1/imports/{jobId}/records` | Paginated parsed records |
| GET | `/api/v1/imports/{jobId}/summary` | Success/failed/duplicate counts |
| GET | `/actuator/health` | Health check |

Upload returns **202 Accepted** with the job in `PENDING` status — the API
is designed around "submit now, poll status later" from day one, since
processing will be `@Async` starting Day 4. There's no endpoint that blocks
waiting for a large file to finish processing.

Documented via springdoc-openapi — run the app and open
`http://localhost:8080/swagger-ui.html`.

### 4. Error handling shape

`GlobalExceptionHandler` + a couple of domain exceptions
(`JobNotFoundException`, `UnsupportedFileTypeException`) so error responses
are already a consistent JSON shape (`ApiError`: status/error/message/
timestamp) before there's real logic to trigger them.

### 5. Config

- Single `application.properties` (MySQL) — Hibernate batch size (500),
  multipart limits (200MB), Flyway on, `ddl-auto=validate` (schema truth
  lives in the migration, not in Hibernate), HikariCP pool tuning. DB
  credentials come from `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env vars with
  local-dev-friendly defaults, rather than a real password committed to the
  file.

## Project layout

```
src/main/java/com/enterprise/bulkimport/
  entity/       ImportJob, ImportRecord
  enums/        JobStatus, RecordStatus
  repository/   ImportJobRepository, ImportRecordRepository
  dto/          ImportJobResponse, ImportRecordResponse, ImportSummaryResponse, ApiError
  controller/   ImportController
  service/      ImportJobService + ImportJobServiceImpl
                 AsyncImportProcessor, FileStorageService
                 parser/    RowParser, CsvRowParser, XlsxRowParser, XlsRowParser, RowParserFactory
                 validation/ PersonImportRow, RowValidator
  scheduler/    StaleJobRecoveryScheduler
  util/         HashUtil
  config/       ImportProperties, AsyncConfig, PaginationConfig, OpenApiConfig
  exception/    JobNotFoundException, UnsupportedFileTypeException, EmptyFileException,
                 GlobalExceptionHandler
src/main/resources/
  application.properties
  db/migration/V1__init_schema.sql
sample-data/
  sample_valid.csv            (3 clean rows)
  sample_with_errors.csv      (missing name, bad email, negative age, one duplicate row)
docs/
  bulk-import.postman_collection.json
  comparison-with-peer-project.md
  architecture.drawio
```

## Running it

Requires Java 17+, Maven, and a running MySQL instance (this delivery
hasn't been compiled in the sandbox — Maven Central is unreachable there;
build it locally).

Create the database (or let it auto-create — the default URL includes
`createDatabaseIfNotExist=true`):

```sql
CREATE DATABASE IF NOT EXISTS bulk_import_db;
```

Set credentials if they differ from the defaults (`root` / `changeme`):

```bash
export DB_URL=jdbc:mysql://localhost:3306/bulk_import_db?createDatabaseIfNotExist=true
export DB_USERNAME=root
export DB_PASSWORD=your_actual_password
```

Then:

```bash
mvn spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

Try it against the sample files:

```bash
curl -F "file=@sample-data/sample_valid.csv" http://localhost:8080/api/v1/imports
# -> 202 Accepted, status: PENDING, no counts yet (processing happens in the background)

curl http://localhost:8080/api/v1/imports/{id}
# -> poll until status is COMPLETED / PARTIALLY_COMPLETED / FAILED

curl http://localhost:8080/api/v1/imports/{id}/summary
curl http://localhost:8080/api/v1/imports/{id}/records

# re-run the first command unmodified -> same job returned immediately, not reprocessed
```

## Design decisions worth flagging

- **`data` as JSON text, not typed columns** — keeps `ImportRecord` generic
  across different file schemas, at the cost of needing a fixed
  `PersonImportRow` DTO for the part that actually validates (see Day 2-3
  section above for the reasoning).
- **Idempotency via file content hash**, not filename — a re-uploaded file
  with a different name but identical bytes is still recognized as a
  duplicate job.
- **String/UUID ids as `VARCHAR(36)`**, app-assigned (not
  `@GeneratedValue`) — so the uploaded file can be written to disk under the
  job's id *before* the row is even inserted, avoiding a rename step.
- **Pagination is in the repository/controller contract**, not bolted on
  later — this is the main lever for the "1L+ records" edge case in the
  spec.

## Running the tests

```bash
mvn test
```

Covers: row hashing (dedup normalization), Bean Validation on every edge
case in `sample_with_errors.csv`, CSV streaming behavior, parser selection
by extension, the async processor's row-processing and batching logic, the
service-layer delegation/idempotency handoff, and stale-job recovery.

## Using the Postman collection

Import `docs/bulk-import.postman_collection.json` into Postman. It's set up
with a `baseUrl` variable (defaults to `http://localhost:8080`) and a
`jobId` variable you set manually from an upload response, then reuse for
the status/records/summary requests. Includes the three edge cases called
out in the assignment brief: re-uploading an unmodified file (idempotency),
an unsupported file type (expect 415), and an unknown job id (expect 404).

## Remaining known gaps (honest, final)

Everything in the assignment brief is implemented. Two things are
deliberately incomplete, both flagged rather than hidden:

- **Full crash resumability.** Day 5-6 added stale-job *detection*
  (`StaleJobRecoveryScheduler`), which turns "job silently stuck forever"
  into "job visibly marked FAILED after a timeout." It does not
  checkpoint a row offset and resume a crashed job from where it left off
  — that would be the natural next step, not attempted here.
- **Load testing.** The streaming/batching design is sized for 1L+ (100k+)
  row files based on the parser/executor/queue design choices documented
  throughout this README, but hasn't been run against an actual
  100k+-row file end-to-end in this environment to confirm real-world
  throughput numbers.

