package com.thanhnien.rss.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
public class DeepLService {

    private static final Logger logger = LoggerFactory.getLogger(DeepLService.class);

    @Value("${deepl.api.key:}")
    private String apiKey;

    @Value("${deepl.api.url}")
    private String apiUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DeepLService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public String translate(String text, String sourceLang, String targetLang) {
        if (!isConfigured()) {
            logger.warn("DeepL API key not configured");
            return text;
        }
        if (text == null || text.trim().isEmpty())
            return text;
        java.util.List<String> result = translateBatch(java.util.Collections.singletonList(text), sourceLang,
                targetLang);
        return result.isEmpty() ? text : result.get(0);
    }

    public java.util.List<String> translateBatch(java.util.List<String> texts, String sourceLang, String targetLang) {
        if (!isConfigured() || texts == null || texts.isEmpty()) {
            return texts != null ? texts : java.util.Collections.emptyList();
        }

        // Filter out empty strings to avoid 400 Bad Request
        java.util.Map<Integer, String> nonEmptyTexts = new java.util.TreeMap<>();
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t != null && !t.trim().isEmpty()) {
                nonEmptyTexts.put(i, t);
            }
        }

        if (nonEmptyTexts.isEmpty()) {
            return texts; // All empty
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            for (String t : nonEmptyTexts.values()) {
                formData.add("text", t);
            }
            formData.add("target_lang", targetLang.toUpperCase());
            if (sourceLang != null) {
                formData.add("source_lang", sourceLang.toUpperCase());
            }

            String response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(formData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode translations = rootNode.get("translations");

            java.util.List<String> result = new java.util.ArrayList<>(texts);
            if (translations != null && translations.isArray()) {
                int index = 0;
                for (Integer originalIndex : nonEmptyTexts.keySet()) {
                    if (index < translations.size()) {
                        result.set(originalIndex, translations.get(index).get("text").asText());
                        index++;
                    }
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Error translating batch with DeepL: {}", e.getMessage());
            // Fallback: Try individually if batch fails and size > 1
            if (texts.size() > 1) {
                logger.info("Retrying individually due to batch failure...");
                java.util.List<String> fallbackResult = new java.util.ArrayList<>();
                for (String t : texts) {
                    // CAUTION: This calls 'translate' which calls 'translateBatch' (singleton).
                    // But singleton failure returns 'texts', so infinite loop is avoided.
                    fallbackResult.add(translate(t, sourceLang, targetLang));
                }
                return fallbackResult;
            }
            return texts;
        }
    }
}
