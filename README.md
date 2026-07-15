# Bulk Import System

This is a Spring Boot project that I built to upload and process CSV/Excel files in bulk. The main objective of this project is to import large amounts of data efficiently while validating the records and storing them in a MySQL database.

The application processes the uploaded files asynchronously so that users don't have to wait for the entire import to complete. They can check the status of the import job at any time.

## Features

- Upload CSV or Excel files
- Asynchronous file processing
- Import status tracking
- Record validation
- MySQL database integration
- REST APIs
- Swagger UI for API testing
- Flyway database migration

## Technologies Used

- Java 21
- Spring Boot 3.3
- Spring Web
- Spring Data JPA
- MySQL 8
- Flyway
- Maven
- Swagger (OpenAPI)
- Git

## Project Structure

```
src
 ├── controller
 ├── service
 ├── repository
 ├── entity
 ├── dto
 ├── scheduler
 ├── config
 └── exception
```

## Database

Database Name:

```
bulk_import_db
```

Update the MySQL username and password in `application.properties` before running the application.

## Running the Project

### Clone the repository

```bash
git clone https://github.com/manu-designn/bulk-import.git
```

### Go to the project

```bash
cd bulk-import
```

### Build the project

```bash
mvn clean install
```

### Run the application

```bash
mvn spring-boot:run
```

The application starts on:

```
http://localhost:8080
```

## Swagger

After starting the application, open:

```
http://localhost:8080/swagger-ui/index.html
```

You can use Swagger to test all available APIs.

## API Overview

| Method | Endpoint | Description |
|---------|----------|-------------|
| POST | /api/v1/imports | Upload a CSV/Excel file |
| GET | /api/v1/imports | View all import jobs |
| GET | /api/v1/imports/{id} | Get import details |
| GET | /api/v1/imports/{id}/summary | View import summary |

## Future Improvements

Some improvements that can be added later are:

- Authentication using Spring Security
- Email notification after import completion
- Progress bar for large imports
- Support for additional file formats
- Docker deployment

## Author

Manohar
