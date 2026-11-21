package com.hellfire.DocumentAssistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads a PDF, splits it into chunks and stores their embeddings in the vector store.
 * Chunks are embedded in small batches with a pause between them, and each batch is retried
 * with increasing waits, so free-tier rate limits (HTTP 429) slow the load down instead of failing it.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final int batchSize;
    private final long pauseMs;
    private final int maxRetries;

    public DocumentIngestionService(VectorStore vectorStore,
                                    @Value("${app.ingest.batch-size:50}") int batchSize,
                                    @Value("${app.ingest.pause-ms:2000}") long pauseMs,
                                    @Value("${app.ingest.max-retries:5}") int maxRetries) {
        this.vectorStore = vectorStore;
        this.batchSize = batchSize;
        this.pauseMs = pauseMs;
        this.maxRetries = maxRetries;
    }

    /**
     * @return number of chunks stored
     */
    public int ingestPdf(Resource pdf) {
        var config = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
                        .withNumberOfBottomTextLinesToDelete(0)
                        .withNumberOfTopPagesToSkipBeforeDelete(0)
                        .build())
                .withPagesPerDocument(1)
                .build();

        var pdfReader = new PagePdfDocumentReader(pdf, config);
        List<Document> chunks = TokenTextSplitter.builder().build().apply(pdfReader.get());
        log.info("{}: {} chunks to embed in batches of {}", pdf.getFilename(), chunks.size(), batchSize);

        for (int start = 0; start < chunks.size(); start += batchSize) {
            List<Document> batch = chunks.subList(start, Math.min(start + batchSize, chunks.size()));
            storeWithRetry(batch);
            int done = start + batch.size();
            log.info("{}: embedded {}/{} chunks", pdf.getFilename(), done, chunks.size());
            if (done < chunks.size()) {
                sleep(pauseMs);
            }
        }

        log.info("Stored {} chunks from {}", chunks.size(), pdf.getFilename());
        return chunks.size();
    }

    private void storeWithRetry(List<Document> batch) {
        for (int attempt = 1; ; attempt++) {
            try {
                vectorStore.accept(batch);
                return;
            } catch (RuntimeException e) {
                if (attempt > maxRetries) {
                    throw e;
                }
                long wait = 15_000L * attempt;   // 15s, 30s, 45s, ... gives per-minute quotas time to reset
                log.warn("Embedding batch failed (attempt {}/{}): {} - retrying in {}s",
                        attempt, maxRetries, e.getMessage(), wait / 1000);
                sleep(wait);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ingestion interrupted", e);
        }
    }
}
