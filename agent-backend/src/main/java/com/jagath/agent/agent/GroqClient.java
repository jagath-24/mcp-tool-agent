package com.jagath.agent.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Minimal client for Groq's OpenAI-compatible chat completions endpoint.
 * Uses raw Maps instead of typed request/response classes so it stays
 * resilient to minor field additions on Groq's side.
 */
@Component
public class GroqClient {

    private final RestClient restClient;

    @Value("${groq.model}")
    private String model;

    public GroqClient(@Value("${groq.base-url}") String baseUrl,
                       @Value("${groq.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "tools", tools,
                "tool_choice", "auto",
                "temperature", 0.2
        );

        return restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);
    }
}
