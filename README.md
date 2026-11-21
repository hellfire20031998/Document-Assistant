# Document Assistant

Ask questions about your PDF documents in a chat UI. Document Assistant is a
Retrieval-Augmented Generation (RAG) app built with **Spring Boot 4**, **Spring AI 2**,
**PostgreSQL + pgvector**, a **local embedding model** (no key, no rate limits), and
**Google Gemini** for answers (OpenAI optional).

It ships with the Spring Boot reference documentation preloaded, and you can upload
your own PDFs from the browser.

## How it works

```
Loading a document                          Answering a question
──────────────────                          ────────────────────
PDF ─► read pages (PDFBox, local)           question ─► embed (local model)
    ─► split into ~800-token chunks (local)          ─► find 4 closest chunks (pgvector)
    ─► embed each chunk (local model)                ─► LLM answers using those chunks (Gemini)
    ─► store text + vector in pgvector
```

- **Reading, chunking and embedding** run on your machine; only answering calls an AI API
  (one request per question).
- **Embeddings** use `all-MiniLM-L6-v2` via ONNX inside the JVM by default. Gemini or OpenAI
  embeddings can be switched on instead, but only one provider at a time (vectors from
  different models aren't comparable).
- **Chat** can use any enabled provider, chosen per question in the UI.
- Answers are grounded in your documents; the model is told to say it doesn't know
  when the answer isn't in the retrieved context.

## Tech stack

| Area | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 4.1, Spring MVC |
| AI | Spring AI 2.0 — `ChatClient`, `QuestionAnswerAdvisor`, PDF document reader |
| Chat models | Gemini `gemini-3.5-flash` (default); OpenAI `gpt-4o-mini` (optional) |
| Embeddings | Local `all-MiniLM-L6-v2` via Spring AI Transformers / ONNX Runtime (default, 384 dims); Gemini `gemini-embedding-001` or OpenAI `text-embedding-3-small` (optional, 768 dims) |
| Vector store | PostgreSQL 16 + pgvector (HNSW index, cosine distance) |
| Persistence | Spring Data JPA (read-only view of the vector table) |
| Dev services | Docker Compose, started automatically by Spring Boot |

## Prerequisites

- **JDK 21**
- **Docker Desktop**, running (the database container is started for you)
- A **Gemini API key** — free at <https://aistudio.google.com/apikey>
- *(Optional)* an **OpenAI API key** on an account with billing credits

## Getting started

1. **Clone**

   ```bash
   git clone https://github.com/hellfire20031998/Document-Assistant.git
   cd Document-Assistant
   ```

2. **Add your key** — create a `.env` file in the project root (it is git-ignored):

   ```properties
   GEMINI_API_KEY=your-gemini-key
   # Optional, only used with the "openai" profile:
   # OPENAI_API_KEY=your-openai-key
   ```

3. **Run**

   ```bash
   ./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
   ```

   Spring Boot starts the `pgvector` container from `compose.yaml`, then the app.

4. **Open** <http://localhost:8080>

On the first start the app downloads the local embedding model once (~90 MB, cached
afterwards) and embeds the bundled reference PDF **in the background** — about a minute for
its 769 chunks. The UI is usable right away; watch the log for `embedded N/M chunks` and
`Reference PDF loaded - Application is Ready`.

## Using it

**Web UI** — type a question, pick a provider if more than one is enabled, and use
**Upload PDF** to add documents (max 20 MB).

**REST API**

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/chat` | Ask a question. Body: `{"question": "...", "provider": "GEMINI"}` (`provider` optional) |
| `GET` | `/api/chat/providers` | List enabled providers |
| `POST` | `/api/documents` | Upload a PDF (`multipart/form-data`, field `file`) |

```bash
curl -X POST localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"How do I change the server port?"}'

curl -F "file=@my-document.pdf" localhost:8080/api/documents
```

## Configuration

Main settings live in `src/main/resources/application.yaml`.

| Setting | Default | Purpose |
|---|---|---|
| `GEMINI_API_KEY` (env / `.env`) | — | Gemini key, required by default |
| `OPENAI_API_KEY` (env / `.env`) | — | OpenAI key, used with the `openai` profile |
| `SPRING_PROFILES_ACTIVE=openai` | off | Enable OpenAI chat (and optionally embeddings) |
| `EMBEDDING_PROVIDER` | `transformers` | Which model embeds documents: `transformers` (local), `google-genai` or `openai` |
| `EMBEDDING_DIMENSIONS` | `384` | Vector size; must match the model (`384` local, `768` Gemini/OpenAI) |
| `app.ingest.batch-size` | `50` | Chunks embedded per batch |
| `INGEST_PAUSE_MS` | `0` | Pause between batches; use ~`60000` for a cloud provider's free tier |
| `app.ingest.max-retries` | `5` | Retries per batch on rate-limit errors (15 s, 30 s, … backoff) |

### Enabling OpenAI

```properties
# .env
OPENAI_API_KEY=sk-...
```

Then run with `SPRING_PROFILES_ACTIVE=openai`. OpenAI appears in the provider dropdown.

### Switching the embedding provider

Stored vectors only work with the model that created them. To use Gemini or OpenAI
embeddings, set `EMBEDDING_PROVIDER` (`google-genai` / `openai`), `EMBEDDING_DIMENSIONS=768`
and, on a free tier, `INGEST_PAUSE_MS=60000`. Then drop the table so it is recreated at the
new size and everything is re-embedded on the next start:

```bash
docker exec -it <pgvector-container> psql -U myuser -d mydatabase -c "DROP TABLE vector_store;"
```

To retry a load that stopped partway without changing models, empty the table instead
(`TRUNCATE vector_store;`) — the loader only runs when the table is empty.

## Project structure

```
src/main/java/com/hellfire/DocumentAssistant/
├── AiConfig.java                  # One ChatClient per enabled provider, with the RAG advisor
├── AiProvider.java                # GEMINI / OPENAI (first enabled = default)
├── ChatController.java            # /api/chat
├── DocumentController.java        # /api/documents (PDF upload)
├── DocumentIngestionService.java  # Read → split → embed in batches with retry
├── ReferenceDocsLoader.java       # Background load of the bundled PDF when the store is empty
├── VectorStoreEntry.java          # Read-only JPA entity for vector_store
└── VectorStoreRepository.java
src/main/resources/
├── application.yaml               # Main config (Gemini default)
├── application-openai.yaml        # "openai" profile
├── docs/spring-boot-reference.pdf # Bundled reference document
└── static/index.html              # Chat UI
compose.yaml                       # pgvector database for local development
```

## Troubleshooting

| Problem | Fix |
|---|---|
| `Cannot run program "docker"` | Install/start Docker Desktop and make sure `docker` is on your `PATH`; restart your IDE afterwards. |
| `Could not resolve placeholder 'GEMINI_API_KEY'` | Create `.env` in the project root (the run's working directory) with your key. |
| `onnxruntime.dll: A dynamic link library (DLL) initialization routine failed` (Windows) | ONNX Runtime 1.20+ needs a newer MSVC runtime than the JDK bundles; the pom pins `onnxruntime` 1.19.2 to avoid this. |
| `429` / quota errors while loading | Only with cloud embeddings on a free tier — the loader retries automatically. Set `INGEST_PAUSE_MS=60000`, or use the default local embeddings. |
| `429: You have no credits remaining` (OpenAI) | The OpenAI API needs billing credits; use Gemini or add credits. |
| Answers ignore new documents after switching models | Empty `vector_store` (see above) so everything is re-embedded with one model. |
