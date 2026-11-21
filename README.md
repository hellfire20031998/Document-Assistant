# Document Assistant

Ask questions about your PDF documents in a chat UI. Document Assistant is a
Retrieval-Augmented Generation (RAG) app built with **Spring Boot 4**, **Spring AI 2**,
**PostgreSQL + pgvector**, and **Google Gemini** (OpenAI optional).

It ships with the Spring Boot reference documentation preloaded, and you can upload
your own PDFs from the browser.

## How it works

```
Loading a document                          Answering a question
──────────────────                          ────────────────────
PDF ─► read pages (PDFBox, local)           question ─► embed (Gemini/OpenAI)
    ─► split into ~800-token chunks (local)          ─► find 4 closest chunks (pgvector)
    ─► embed each chunk (Gemini/OpenAI)              ─► LLM answers using those chunks
    ─► store text + vector in pgvector
```

- **Reading and chunking** run locally; only embedding and answering call an AI API.
- **Embeddings** use a single provider (vectors from different models aren't comparable).
- **Chat** can use any enabled provider, chosen per question in the UI.
- Answers are grounded in your documents; the model is told to say it doesn't know
  when the answer isn't in the retrieved context.

## Tech stack

| Area | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 4.1, Spring MVC |
| AI | Spring AI 2.0 — `ChatClient`, `QuestionAnswerAdvisor`, PDF document reader |
| Models | Gemini `gemini-3.5-flash` + `gemini-embedding-001` (default); OpenAI `gpt-4o-mini` + `text-embedding-3-small` (optional) |
| Vector store | PostgreSQL 16 + pgvector (HNSW index, cosine distance, 768 dimensions) |
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

On the first start the bundled reference PDF is embedded **in the background**. The UI is
usable right away; watch the log for `embedded N/M chunks` and
`Reference PDF loaded - Application is Ready`. On the Gemini free tier this can take a while.

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
| `EMBEDDING_PROVIDER` | `google-genai` | Which provider embeds documents: `google-genai` or `openai` |
| `app.ingest.batch-size` | `50` | Chunks embedded per API request |
| `app.ingest.pause-ms` | `2000` | Pause between batches |
| `app.ingest.max-retries` | `5` | Retries per batch on rate-limit errors (15 s, 30 s, … backoff) |

### Enabling OpenAI

```properties
# .env
OPENAI_API_KEY=sk-...
```

Then run with `SPRING_PROFILES_ACTIVE=openai`. OpenAI appears in the provider dropdown.
Add `EMBEDDING_PROVIDER=openai` to embed with OpenAI instead of Gemini.

### Switching the embedding provider

Stored vectors only work with the model that created them. After changing
`EMBEDDING_PROVIDER`, empty the table so documents are re-embedded on the next start:

```bash
docker exec -it <pgvector-container> psql -U myuser -d mydatabase -c "TRUNCATE vector_store;"
```

The same command retries a reference-PDF load that stopped partway (the loader only runs
when the table is empty).

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
| `429` / quota errors while loading | Free-tier limits — the loader retries automatically. Increase `app.ingest.pause-ms` or use a smaller PDF. |
| `429: You have no credits remaining` (OpenAI) | The OpenAI API needs billing credits; use Gemini or add credits. |
| Answers ignore new documents after switching models | Empty `vector_store` (see above) so everything is re-embedded with one model. |
