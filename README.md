# JDM — Java Download Manager Backend

JDM is a backend service for managing HTTP downloads. Clients create download tasks for HTTP URLs; the server downloads files in parallel and stores them in S3-compatible storage. APIs are exposed via REST and gRPC.

---

## Purpose

- **Manage HTTP downloads**: create tasks for HTTP URLs, pause, resume, list, delete
- **Parallel segmented download**: split files into chunks, download concurrently (dynamic segmentation with work stealing)
- **Resumable**: when server supports `Range`, uses Range requests; falls back to stream download when size is unknown
- **Track progress**: poll task status, query in-memory progress, receive WebSocket pushes
- **Auth**: signup, login, JWT access/refresh tokens, password reset (gRPC), session revocation

---

## How It Works

This section explains the main end‑to‑end flows and how the external services fit together.

### Architecture & external services

- **HTTP controllers (Spring MVC + Tomcat)**: handle REST API on port 8080.
- **gRPC controllers (Netty + grpc‑java)**: expose `AccountService`, `SessionService`, `DownloadTaskService` on port 9090, wired via `grpc-server-spring-boot-starter`.
- **MySQL**: stores accounts, sessions, download tasks, and Flyway migration history.
- **Redis**: cache for account lookups and session checks to offload MySQL.
- **RabbitMQ (optional)**: queue for `download-task.created` events so long‑running downloads do not block HTTP/gRPC threads.
- **S3 / MinIO**: final storage for downloaded files; the app only keeps a temp file locally while downloading.

### Download task flow

1. Client creates a task (`POST /tasks` with URL).
2. Task is saved with status `PENDING`.
3. **If RabbitMQ configured**: event is published; consumer runs the download.
4. **If RabbitMQ not configured**: `PendingDownloadTaskScheduler` cron (every 30s) picks up PENDING tasks.
5. **DownloadExecutor** fetches metadata (HEAD → Range 0-0 → full GET fallback), then either:
   - **Chunked path**: `DynamicSegmentPool` + `ChunkWorker`s for parallel Range GETs; segments split dynamically as workers finish.
   - **Stream path**: single GET when size unknown or server does not support Range.
6. Completed file is uploaded to S3/MinIO; task status becomes `SUCCESS` (or `FAILED`).
7. Client streams the file via `GET /tasks/{id}/file`.

### Internal download pipeline (detailed)

1. **Metadata resolution**  
   - `FileRequester` runs several strategies in parallel: `HEAD`, small `GET` range requests (`0-0`, `0-1`, `0-499`), then a final full `GET` (headers only) as a fallback.  
   - It normalizes different server behaviours (missing `Content-Length`, disabled `HEAD`, CDN quirks, custom headers) into a single metadata object: filename, size, and effective headers.
2. **Strategy decision**  
   - If the server supports `Range` and the size is known → **chunked download**.  
   - Otherwise → **stream download** using a single `GET`.
3. **Chunked download (dynamic segmentation)**  
   - The file is pre‑split into N equal segments (see `jdm.download.chunk-count`); all segments start in a pending queue managed by `DynamicSegmentPool`.  
   - A fixed‑size worker pool submits multiple `ChunkWorker` instances; each worker:  
     - Grabs the largest pending segment; if it is too large, the pool splits it and gives half to the worker (work stealing).  
     - Sends a `Range` request, writes bytes to the correct offset in a temp file using `RandomAccessFile`.  
     - Updates `DownloadProgress` (bytes, last update time, speed).  
   - When all workers complete, the temp file holds the full content.
4. **Stream download**  
   - `StreamDownloader` performs a regular `GET` and reads from the response stream in 64KB chunks, writing sequentially to disk and updating a streaming `DownloadProgress`.
5. **Finalize & upload**  
   - On success, the temp file is uploaded to S3/MinIO, and the `DownloadTask` row is updated with `SUCCESS` status and storage key.  
   - On failure, the task is marked `FAILED`, the error is stored in metadata, and no object is created in S3.

### Storage

- S3-compatible storage (MinIO, AWS S3). Bucket auto-created. Switch providers via config only.

### Auth

- JWT access/refresh tokens. Sessions in DB. Protected routes use `Authorization: Bearer <token>`.

### WebSocket

- STOMP endpoint: `/ws`.
- Progress topic: `/topic/progress/{taskId}` — pushed every ~500 ms while downloading.
- With no frontend yet, run `python scripts/ws_test.py` (requires `requests`, `websocket-client`). Example output:


---

## Tech Stack

| Category     | Technology |
|-------------|------------|
| Language    | Java 17    |
| Framework   | Spring Boot 4.x |
| Build       | Gradle     |
| Database    | MySQL 8, Spring Data JPA, Flyway |
| Cache       | Redis (Lettuce) |
| Queue       | RabbitMQ (optional; cron fallback when not configured) |
| Storage     | AWS SDK S3 (MinIO-compatible) |
| Auth        | JWT (jjwt), BCrypt |
| REST docs   | Springdoc OpenAPI (Swagger UI) |
| RPC         | gRPC on port 9090 |
| WebSocket   | Spring STOMP |

---

## REST API

- **Public**: `POST /login`, `POST /signup`, `POST /refresh`
- **Protected** (`/tasks`, requires Bearer token):
  - `POST /tasks` — create
  - `GET /tasks` — list (paginated)
  - `GET /tasks/{id}` — get task
  - `PUT /tasks/{id}` — update URL
  - `DELETE /tasks/{id}`
  - `GET /tasks/{id}/progress` — in-memory progress (active downloads)
  - `GET /tasks/{id}/segments` — segment/worker status
  - `GET /tasks/{id}/file` — stream completed file

---

## gRPC

- Proto: `src/main/proto/jdm.proto`. Services: Account, Session, DownloadTask.
- Port 9090. Auth: Bearer token in `authorization` metadata.


---

## Running Locally

### Prerequisites

- Java 17
- MySQL
- Redis
- MinIO (or any S3-compatible backend)
- RabbitMQ (optional)

### Setup

1. Create `.env` in project root (git-ignored). See `application.properties` for env var names.

2. Start infra: `docker compose -f deployments/docker-compose.dev.yml up -d`.

3. Run: `./gradlew bootRun`. HTTP on 8080, gRPC on 9090.

