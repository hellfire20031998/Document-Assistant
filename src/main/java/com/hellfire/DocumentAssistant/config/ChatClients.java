package com.hellfire.DocumentAssistant.config;

import com.hellfire.DocumentAssistant.model.AiProvider;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;
import java.util.Set;

/**
 * Holds the ChatClient for each enabled provider (built in {@link AiConfig}).
 */
public record ChatClients(Map<AiProvider, ChatClient> byProvider) {

    public ChatClient get(AiProvider provider) {
        return byProvider.get(provider);
    }

    public Set<AiProvider> available() {
        return byProvider.keySet();
    }
}
