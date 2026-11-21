package com.hellfire.DocumentAssistant.dto;

import com.hellfire.DocumentAssistant.model.AiProvider;

/**
 * Body of POST /api/chat. {@code provider} is optional; defaults to the first enabled provider (Gemini).
 */
public record ChatRequest(String question, AiProvider provider) {
}
