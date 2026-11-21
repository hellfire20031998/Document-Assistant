package com.hellfire.DocumentAssistant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Answers questions using the documents stored in the vector store (RAG),
 * with any enabled provider (OpenAI and/or Gemini) as the chat model.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** provider is optional; defaults to the first enabled provider (Gemini). */
    public record ChatRequest(String question, AiProvider provider) {}

    public record ChatResponse(String answer, AiProvider provider) {}

    private final AiConfig.ChatClients chatClients;

    public ChatController(AiConfig.ChatClients chatClients) {
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
