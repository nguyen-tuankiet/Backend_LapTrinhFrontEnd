package com.thanhnien.rss.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class OpenAIService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:gpt-3.5-turbo}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAIService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if OpenAI API is configured
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Generate content using OpenAI Chat API
     */
    public String generateContent(String prompt) {
        if (!isConfigured()) {
            logger.warn("OpenAI API key not configured");
            return null;
        }

        try {
            // Build request body for Chat Completions API
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content",
                    "Bạn là trợ lý AI tóm tắt tin tức. Trả lời bằng tiếng Việt, ngắn gọn và dễ hiểu.");

            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);

            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.7);

            String response = webClient.post()
                    .uri(OPENAI_API_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse response
            JsonNode responseJson = objectMapper.readTree(response);
            JsonNode choices = responseJson.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }

            logger.warn("Unexpected OpenAI response format: {}", response);
            return null;

        } catch (Exception e) {
            logger.error("Error calling OpenAI API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Summarize news articles using OpenAI
     */
    public String summarizeNews(String categoryName, String articlesText) {
        String prompt = String.format("""
                Hãy tóm tắt các tin tức %s sau một cách ngắn gọn, dễ hiểu.
                Trả lời theo format:

                📰 **Tóm tắt tin tức %s hôm nay:**

                [Liệt kê các điểm chính của từng tin, mỗi tin 1-2 câu]

                ---
                Các tin tức cần tóm tắt:
                %s
                """, categoryName, categoryName, articlesText);

        return generateContent(prompt);
    }
}
