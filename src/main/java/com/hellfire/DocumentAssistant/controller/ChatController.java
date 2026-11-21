package com.hellfire.DocumentAssistant.controller;

import com.hellfire.DocumentAssistant.config.ChatClients;
import com.hellfire.DocumentAssistant.dto.ChatRequest;
import com.hellfire.DocumentAssistant.dto.ChatResponse;
import com.hellfire.DocumentAssistant.model.AiProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Answers questions using the documents stored in the vector store (RAG),
 * with any enabled provider (Gemini and/or OpenAI) as the chat model.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClients chatClients;

    public ChatController(ChatClients chatClients) {
        this.chatClients = chatClients;
    }

    /** Which providers are enabled — the web page uses this to build its dropdown. */
    @GetMapping("/providers")
    public Set<AiProvider> providers() {
        return chatClients.available();
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        AiProvider provider = request.provider() != null ? request.provider() : defaultProvider();
        if (chatClients.get(provider) == null) {
            return ResponseEntity.badRequest().body(new ChatResponse(
                    provider + " is not enabled. Available: " + chatClients.available(), provider));
        }
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(new ChatResponse("Question must not be empty.", provider));
        }
        String answer = chatClients.get(provider).prompt()
                .user(request.question())
                .call()
                .content();
        return ResponseEntity.ok(new ChatResponse(answer, provider));
    }

    /** First enabled provider in AiProvider order (Gemini first). */
    private AiProvider defaultProvider() {
        return chatClients.available().iterator().next();
    }
}
