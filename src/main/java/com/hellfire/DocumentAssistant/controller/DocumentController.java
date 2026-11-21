package com.hellfire.DocumentAssistant.controller;

import com.hellfire.DocumentAssistant.service.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.util.Map;

/**
 * Lets users upload additional PDFs to the knowledge base.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || !name.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please upload a non-empty PDF file."));
        }

        var resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return name;
            }
        };
        int chunks = ingestionService.ingestPdf(resource);
        return ResponseEntity.ok(Map.of("file", name, "chunks", chunks));
    }
}
