package com.hellfire.DocumentAssistant.config;

import com.hellfire.DocumentAssistant.model.AiProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Builds one ChatClient per enabled provider. All share the same vector store (RAG) and system prompt.
 * A provider whose chat model isn't configured (e.g. OpenAI without the "openai" profile) is skipped.
 */
@Configuration
public class AiConfig {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that answers questions about the user's documents.
            Use the provided context to answer. If the answer is not in the context,
            say you don't know instead of making something up.
            """;

    @Bean
    public ChatClients chatClients(ObjectProvider<OpenAiChatModel> openAi,
                                   ObjectProvider<GoogleGenAiChatModel> gemini,
                                   VectorStore vectorStore) {
        Map<AiProvider, ChatClient> clients = new EnumMap<>(AiProvider.class);
        openAi.ifAvailable(model -> clients.put(AiProvider.OPENAI, buildClient(model, vectorStore)));
        gemini.ifAvailable(model -> clients.put(AiProvider.GEMINI, buildClient(model, vectorStore)));
        if (clients.isEmpty()) {
            throw new IllegalStateException("No chat model configured. Set GEMINI_API_KEY in .env, "
                    + "or enable the 'openai' profile with OPENAI_API_KEY.");
        }
        return new ChatClients(Collections.unmodifiableMap(clients));
    }

    private ChatClient buildClient(ChatModel model, VectorStore vectorStore) {
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(4).build())
                        .build())
                .build();
    }
}
