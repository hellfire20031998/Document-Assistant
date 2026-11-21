package com.hellfire.DocumentAssistant.dto;

import com.hellfire.DocumentAssistant.model.AiProvider;

/** Response of POST /api/chat: the answer and which provider produced it. */
public record ChatResponse(String answer, AiProvider provider) {
}
