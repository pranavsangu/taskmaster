# Taskmaster

Taskmaster is a V1 Spring Boot REST API for project and task tracking with project membership, comments, and task attachments.

## Stack

- Java 21
- Spring Boot 3.4.5
- Maven
- PostgreSQL for runtime persistence
- Spring Data JPA
- Spring Security with stateless JWT access tokens
- Jakarta Validation
- Swagger/OpenAPI

## Prerequisites

- Java 21 or newer
- Maven 3.9 or newer
- PostgreSQL running locally

Create the database once if needed:

```sql
CREATE DATABASE taskmaster;
```

## Configuration

Runtime configuration is read from environment variables. The application does not contain database credentials.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/taskmaster
export DB_USERNAME=your_postgres_username
export DB_PASSWORD='your_postgres_password'
export JWT_SECRET='a-long-base64-secret'
export JWT_EXPIRATION_MS=3600000
export ATTACHMENTS_STORAGE_DIR="$PWD/uploads"
export MAX_ATTACHMENT_SIZE=10485760
```

`DB_URL` defaults to the local `taskmaster` database. `JWT_EXPIRATION_MS` defaults to one hour. Attachment storage defaults to `uploads`, and `MAX_ATTACHMENT_SIZE` is specified in bytes; the default is 10 MB (`10485760`).

Do not commit real credentials or JWT secrets. Use a shell-local environment, a secret manager, or an ignored local environment file.

## Run

```bash
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

## Test

Tests use an in-memory H2 database and do not require PostgreSQL credentials:

```bash
mvn clean test
```

## API Documentation

With the application running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Registration and login are public. All application endpoints require:

```http
Authorization: Bearer <jwt>
```

Use the JWT returned by `POST /api/auth/login` with the Swagger **Authorize** button.

## Authentication and Logout

`POST /api/auth/register` creates a user, and `POST /api/auth/login` returns a JWT access token. V1 has no refresh tokens, token blacklist, or server-side logout endpoint. Logout is completed by the client discarding its JWT.

## Main API Areas

- Auth: register and login
- Users: get and update the current profile
- Projects: create, list, view, update, and delete
- Membership: add, list, and remove project members
- Tasks: CRUD, status, assignment, filtering, searching, sorting, and pagination
- Comments: create, list, update-own, and delete-own
- Attachments: upload, list, download, and delete task-scoped files

Project authorization is based on `ProjectMember`: `OWNER` manages membership and project update/delete, while `MEMBER` can access project tasks but cannot perform OWNER-only operations.

## Project Documents

Design and review documents are maintained locally in `docs/` and are intentionally excluded from the public repository.
