# Policy Intelligence Platform

An enterprise-style RAG platform for ingesting policy documents, chunking them,
embedding them, retrieving relevant policy context with PGVector cosine search,
and generating grounded advisor answers with source attribution.

This repository owns the backend API and the local full-stack Docker
orchestration. The complete project is split into three repos:

```text
API repo:
C:\Users\User\Documents\Codex\2026-06-14\files-mentioned-by-the-user-new\policy-intelligence-api

UI repo:
C:\Users\User\Documents\Codex\2026-06-14\files-mentioned-by-the-user-new\policy-intelligence-ui

ML repo:
C:\Users\User\Documents\Codex\2026-06-14\files-mentioned-by-the-user-new\policy-intelligence-ml
```

## Repos To Clone

Clone all three repos into the same parent folder. The folder names must stay
side-by-side because Docker Compose in the API repo references the UI and ML
repos by relative paths.

Required repos:

```text
policy-intelligence-api
policy-intelligence-ui
policy-intelligence-ml
```

Expected local folder layout:

```text
some-parent-folder/
|-- policy-intelligence-api/
|-- policy-intelligence-ui/
`-- policy-intelligence-ml/
```

Example using this machine's current workspace:

```text
C:\Users\User\Documents\Codex\2026-06-14\files-mentioned-by-the-user-new\
|-- policy-intelligence-api\
|-- policy-intelligence-ui\
`-- policy-intelligence-ml\
```

Example clone flow:

```bash
mkdir -p policy-intelligence-workspace
cd policy-intelligence-workspace

git clone <api-repo-url> policy-intelligence-api
git clone <ui-repo-url> policy-intelligence-ui
git clone <ml-repo-url> policy-intelligence-ml

cd policy-intelligence-api
sh scripts/start-stack.sh
```

Replace `<api-repo-url>`, `<ui-repo-url>`, and `<ml-repo-url>` with the actual
GitHub repository URLs after the repos are pushed.

## Why This Project Exists

Most simple RAG demos stop at "upload a file and ask a question." This project
goes further and demonstrates the engineering pieces needed for a serious
enterprise knowledge platform:

- document ingestion with immutable versions
- PDF, TXT, and Markdown extraction
- deterministic chunking strategies
- embedding lifecycle tracking
- PGVector-backed semantic retrieval
- cosine similarity search in the database
- context filtering before LLM calls
- LLM final answer generation from retrieved source chunks
- source attribution for auditability
- retrieval trace storage
- ML service integration for retrieval-quality prediction
- Dockerized API, UI, ML, and database
- local secrets kept out of Git
- startup, shutdown, restart, and logs scripts

The goal is to show how an enterprise RAG system is shaped, not just that an LLM
can answer a question. It gives you a concrete project to discuss backend
architecture, vector search, observability, ML feedback loops, containerization,
and deployment readiness.

## What It Demonstrates

The platform demonstrates these capabilities:

```text
Upload policy document
  -> extract text
  -> chunk text
  -> generate embeddings
  -> store chunks and vectors in PostgreSQL/PGVector
  -> search by semantic similarity
  -> build grounded context
  -> call LLM for final answer
  -> save trace, sources, and retrieval quality
```

The UI lets a user upload policy files, inspect document versions, view chunks,
run vector search directly, and ask the advisor for a final grounded answer.

The API owns ingestion, retrieval, advisor orchestration, tracing, and service
integration.

The ML service is intentionally separate. Today it predicts whether retrieval
quality looks good or bad based on retrieval features. Over time it can be
trained on real feedback from `retrieval_trace` and `retrieval_feedback`.

## Simple Architecture Flow

```text
Browser / Angular UI
  |
  | 1. Upload document or ask question
  v
Spring Boot API
  |
  | 2. Extract text from PDF/TXT/Markdown
  | 3. Create immutable document version
  | 4. Split text into chunks
  | 5. Generate embeddings for chunks
  v
PostgreSQL + PGVector
  |
  | 6. Store document, version, chunk, vector, trace data
  | 7. Run vector search using cosine distance
  v
Advisor Pipeline
  |
  | 8. Refine query
  | 9. Retrieve top matching chunks
  | 10. Build context from retrieved text chunks
  | 11. Send selected text chunks to LLM
  | 12. Receive final grounded answer
  | 13. Ask ML service to score retrieval quality
  v
Trace + Response
  |
  | 14. Save trace, sources, similarity scores, ML label
  v
UI displays answer, sources, chunks, and retrieval quality
```

Important distinction:

```text
PGVector receives numeric embeddings and performs cosine similarity search.
The LLM receives the retrieved human-readable text chunks, not raw vectors.
```

## Runtime Components

The Docker stack runs:

```text
postgres      PostgreSQL with PGVector extension
ml-service    FastAPI ML service for retrieval quality scoring
api           Spring Boot backend
ui            Angular production build served by Nginx
```

## Key APIs

Direct vector search:

```text
GET /api/v1/retrieval/search?query=...&topK=5
```

Full advisor flow with LLM answer generation:

```text
POST /api/v1/advisor
```

ML health through API:

```text
GET /api/v1/ml/health
```

Recent retrieval traces:

```text
GET /api/v1/retrieval-traces?limit=5
```

Document APIs:

```text
POST /api/v1/documents
GET  /api/v1/documents
GET  /api/v1/documents/{documentId}/versions
GET  /api/v1/documents/versions/{versionId}/chunks
```

## Current vertical slice

- Java 21 and Spring Boot 3.5
- PostgreSQL with PGVector
- Flyway-owned schema
- PDF, TXT, and Markdown text extraction
- Fixed-size and sliding-window chunking
- Immutable document versions
- Old chunk deactivation
- Corpus version increments for future cache invalidation
- Embedding lifecycle state (`PENDING`, `COMPLETED`, `FAILED`)
- Vector retrieval with cosine distance
- Advisor pipeline with LLM-backed final answer generation
- Retrieval traces and ML retrieval-quality prediction

## Module structure

```text
com.acme.policyintelligence
|-- advisor
|-- chunking
|-- context
|-- document
|   |-- api
|   |-- application
|   |-- domain
|   `-- infrastructure
|-- embedding
|-- ml
|-- retrieval
|-- trace
`-- shared
    `-- api
```

Modules communicate through application services and records. JPA repositories
remain infrastructure details. This keeps the modular monolith easy to split
only if operational evidence later justifies it.

## Data model

```text
Document 1 --- * DocumentVersion 1 --- * DocumentChunk

DocumentVersion is immutable.
Only chunks belonging to the latest version are active.
CorpusState is locked and incremented in the version transaction.
```

Chunks store a PGVector embedding. Retrieval uses PGVector's cosine-distance
operator:

```sql
chunk.embedding <=> ?::vector
```

`<=>` returns cosine distance. Lower distance means closer semantic match. The
API exposes similarity as:

```sql
1 - (chunk.embedding <=> ?::vector)
```

So a result closer to `1.0` is more similar.

## Quick start on Windows

Use this path for the least painful setup. Docker runs every runtime component:
PostgreSQL/PGVector, ML service, API, and UI.

### 1. Start Docker Desktop

Open Docker Desktop and wait until it says Docker is running.

Verify Docker from Git Bash, WSL, or IntelliJ Terminal:

```bash
docker version
docker run --rm hello-world
```

If Docker is not running, startup may fail with an error like:

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

That means Docker Desktop is not started yet, or the Linux engine is still
initializing. Start Docker Desktop, wait, and run the verification commands
again.

### 2. Use a shell that can run `.sh`

On Windows, run the scripts from one of these:

- Git Bash
- WSL
- IntelliJ Terminal configured to Git Bash or WSL

For IntelliJ, set:

```text
Settings -> Tools -> Terminal -> Shell path
```

Git Bash is usually:

```text
C:\Program Files\Git\bin\bash.exe
```

Check:

```bash
sh --version
```

### 3. Configure local secrets

From this API repo:

```bash
cp .env.example .env.local
```

Fill `.env.local` locally:

```text
OPENAI_API_KEY=
LLM_MODEL=gpt-5.5
```

Do not commit `.env.local`. It is ignored by Git.

The startup script copies local secret values into ignored files under
`.secrets/` for container use. Neither `.env.local` nor `.secrets/*` is tracked
by Git.

### 4. Start the full stack

From this API repo:

```bash
sh scripts/start-stack.sh
```

The script starts services sequentially:

```text
1. PostgreSQL / PGVector
2. ML service
3. API
4. UI
```

Open:

```text
http://localhost:4200
```

## Stack URLs

The stack publishes:

```text
UI:       http://localhost:4200
API:      http://localhost:8080
ML:       http://localhost:8090
Postgres: localhost:5433
```

The Dockerized PGVector instance is published on host port `5433` to avoid
colliding with an existing local PostgreSQL installation.

Check container status:

```bash
docker compose ps
```

Expected status:

```text
postgres     healthy
ml-service   healthy
api          healthy
ui           up
```

Check API health:

```bash
curl http://localhost:8080/actuator/health
```

Check ML through the API:

```bash
curl http://localhost:8080/api/v1/ml/health
```

Check the UI-to-API proxy:

```bash
curl http://localhost:4200/api/v1/ml/health
```

## Test Walkthrough

After the stack is running, open:

```text
http://localhost:4200
```

Upload a PDF, TXT, or Markdown policy document.

For a larger local test PDF, use:

```text
C:\Users\User\Documents\Codex\2026-06-14\files-mentioned-by-the-user-new\outputs\enterprise-policy-stress-test-600-lines.pdf
```

Suggested upload settings:

```text
Title: Enterprise Policy Stress Test
Chunking strategy: Sliding window
Chunk size: 1000
Overlap: 200
```

After upload:

1. Confirm the document appears in `Documents and versions`.
2. Confirm chunks appear in `Chunk Inspection`.
3. Wait until chunk embedding status is `COMPLETED`.
4. Test direct vector search in the UI or with curl:

```bash
curl "http://localhost:8080/api/v1/retrieval/search?query=Can%20contractors%20access%20production%20customer%20data%3F&topK=5"
```

5. Test Ask Advisor with:

```text
Can contractors access production customer data, and what approval is required?
```

Expected answer should say that contractors cannot access production customer
data by default and require explicit approval recorded in the policy context.
The exact wording may vary because the final answer can be LLM-generated.

Watch API logs while asking:

```bash
sh scripts/logs-stack.sh api
```

Relevant logs include:

```text
Advisor request started
Advisor retrieval completed
Advisor context built
LLM answer generation started
LLM answer generation completed
Advisor retrieval quality predicted
Advisor request completed
```

If the LLM is not configured or fails, the API logs a fallback message and
returns a local extractive answer.

## Stack operations

Start everything:

```bash
sh scripts/start-stack.sh
```

Bring everything down, keeping database/model volumes:

```bash
sh scripts/stop-stack.sh
```

Restart everything:

```bash
sh scripts/stop-stack.sh
sh scripts/start-stack.sh
```

List containers:

```bash
docker compose ps
```

View logs:

```bash
sh scripts/logs-stack.sh
sh scripts/logs-stack.sh api
```

Restart one service:

```bash
sh scripts/restart-service.sh api
sh scripts/restart-service.sh ui
sh scripts/restart-service.sh ml-service
sh scripts/restart-service.sh postgres
```

Or use Docker Compose directly:

```bash
docker compose restart api
docker compose restart ui
docker compose restart ml-service
docker compose restart postgres
```

Recreate one service after code/config changes:

```bash
docker compose up -d --build api
docker compose up -d --build ui
docker compose up -d --build ml-service
```

Rebuild after code changes:

```bash
docker compose build api ui
sh scripts/start-stack.sh
```

Reset containers but keep database volumes:

```bash
docker compose down
```

Reset everything including local database and ML model volumes:

```bash
docker compose down -v
```

Use `down -v` carefully; it deletes uploaded documents, chunks, traces, and
trained model volume data.

Health checks:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/ml/health
curl http://localhost:4200/api/v1/ml/health
```

## Troubleshooting

### Docker API pipe error

Error:

```text
unable to get image 'policy-intelligence-api-ml-service':
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

Fix:

```bash
docker version
docker run --rm hello-world
```

If either command fails, open Docker Desktop and wait until it is fully running.

### `sh` command not found

Use Git Bash or WSL. In IntelliJ, configure the Terminal shell path to Git Bash:

```text
C:\Program Files\Git\bin\bash.exe
```

### Port already in use

Check the process using a port:

```bash
netstat -ano | grep ':8080'
netstat -ano | grep ':4200'
```

Stop the existing local API/UI process or change the exposed port in
`compose.yaml`.

### Verify secrets are not tracked

```bash
git check-ignore -v .env.local
git check-ignore -v .secrets/openai_api_key
```

Both should point to `.gitignore`.

## Run the API locally

If you want to run only the API outside Docker while Postgres and ML run in
containers:

```bash
sh scripts/start-dev.sh
```

It starts PGVector and ML, stops a stale instance of this API if it owns port `8080`,
and starts Spring Boot. It deliberately refuses to terminate unrelated
processes. To use another application port:

```bash
PORT=8081 sh scripts/start-dev.sh
```

If PGVector is already running and should not be managed by the script:

```bash
NO_DOCKER=true sh scripts/start-dev.sh
```

Enable LLM-backed advisor answers by setting an OpenAI-compatible API key
before starting the API:

```bash
export OPENAI_API_KEY="your-api-key"
export LLM_MODEL="gpt-5.5"
sh scripts/start-dev.sh
```

If `OPENAI_API_KEY` is not set, the advisor automatically falls back to the
local extractive answer generator.

Create a document:

```bash
curl.exe -X POST http://localhost:8080/api/v1/documents `
  -F "title=Production Data Access Policy" `
  -F "file=@policy.md" `
  -F "strategy=SLIDING_WINDOW" `
  -F "chunkSize=1000" `
  -F "overlap=200"
```

Create a new immutable version:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/documents/{documentId}/versions `
  -F "file=@policy-v2.md" `
  -F "strategy=SLIDING_WINDOW" `
  -F "chunkSize=1000" `
  -F "overlap=200"
```

Explore stored data:

```text
GET /api/v1/documents
GET /api/v1/documents/{documentId}/versions
GET /api/v1/documents/versions/{versionId}/chunks
```

## Architectural decisions

1. Character chunking is the baseline experiment, not the final answer.
   Token-aware and structure-aware chunkers can be added behind `Chunker`.
2. Version creation, chunk activation, and corpus version increment share one
   transaction so retrieval never observes a partially activated version.
3. Provider calls will occur after commit because remote embedding calls cannot
   participate reliably in a PostgreSQL transaction.
4. Raw uploaded files are not stored yet. Production deployment should add an
   object-storage port and retain the source artifact for audit and reprocessing.

## Testing strategy

- Unit tests for chunk boundaries and validation
- Repository integration tests against real PGVector via Testcontainers
- API tests for multipart validation and error contracts
- Later: golden retrieval datasets and answer/source evaluation

## Scaling and interview discussion

- The singleton corpus row serializes writes, which is acceptable for the MVP
  but should become a database sequence or event-derived generation at higher
  ingestion throughput.
- `count + 1` is protected by a pessimistic document lock. The unique database
  constraint remains the final concurrency guard.
- Chunking by characters is deterministic and easy to compare, but token counts
  and semantic boundaries matter more for model context utilization.
- PGVector index choice (`HNSW` versus `IVFFlat`) depends on corpus size,
  update frequency, latency goals, and recall measurements.
