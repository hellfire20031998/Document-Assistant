package com.hellfire.DocumentAssistant.loader;

import com.hellfire.DocumentAssistant.repository.VectorStoreRepository;
import com.hellfire.DocumentAssistant.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads the bundled reference PDF into the vector store when the store is empty.
 * Runs in the background after startup, so the web app is usable immediately and
 * a failed load (e.g. API quota) is logged instead of crashing the application.
 */
@Component
public class ReferenceDocsLoader {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDocsLoader.class);
    private final VectorStoreRepository vectorStoreRepository;
    private final DocumentIngestionService ingestionService;
    @Value("classpath:/docs/spring-boot-reference.pdf")
    private Resource pdfResource;


    public ReferenceDocsLoader(VectorStoreRepository vectorStoreRepository, DocumentIngestionService ingestionService) {
        this.vectorStoreRepository = vectorStoreRepository;
        this.ingestionService = ingestionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadInBackground() {
        Thread.ofVirtual().name("reference-docs-loader").start(this::load);
    }

    private void load() {
        try {
            long count = vectorStoreRepository.count();
            log.info("Current count of the Vector Store: {}", count);

            if (count == 0) {
                log.info("Loading Spring Boot Reference PDF into Vector Store (in background)");
                ingestionService.ingestPdf(pdfResource);
                log.info("Reference PDF loaded - Application is Ready");
            }
        } catch (Exception e) {
            log.error("Loading the reference PDF failed; the app keeps running. "
                    + "Empty the vector_store table and restart to retry. Cause: {}", e.getMessage(), e);
        }
    }
}
