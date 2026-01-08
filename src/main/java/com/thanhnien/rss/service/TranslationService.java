package com.thanhnien.rss.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service để dịch văn bản sử dụng OpenAI API
 */
@Service
public class TranslationService {

    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${translation.cache.enabled:true}")
    private boolean cacheEnabled;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Cache translations
    private final ConcurrentHashMap<String, String> translationCache = new ConcurrentHashMap<>();

    // Supported languages
    public static final String VI = "vi";
    public static final String EN = "en";
    public static final String ZH = "zh";
    public static final String JA = "ja";
    public static final String KO = "ko";
    public static final String TH = "th";
    public static final String FR = "fr";
    public static final String ES = "es";

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "vi", "Vietnamese",
            "en", "English",
            "zh", "Chinese",
            "ja", "Japanese",
            "ko", "Korean",
            "th", "Thai",
            "fr", "French",
            "es", "Spanish");

    public TranslationService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Translate text using OpenAI API
     */
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        if (sourceLang.equals(targetLang)) {
            return text;
        }

        // Check cache first
        String cacheKey = generateCacheKey(text, sourceLang, targetLang);
        if (cacheEnabled && translationCache.containsKey(cacheKey)) {
            return translationCache.get(cacheKey);
        }

        // Check if OpenAI is configured
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("OpenAI API key not configured, returning original text");
            return text;
        }

        try {
            String translated = callOpenAI(text, sourceLang, targetLang);

            // Cache the result
            if (cacheEnabled && translated != null) {
                cacheTranslation(cacheKey, translated);
            }

            return translated;
        } catch (Exception e) {
            logger.error("Translation failed for '{}' ({} -> {}): {}",
                    text.substring(0, Math.min(50, text.length())),
                    sourceLang, targetLang, e.getMessage());
            return text; // Return original on error
        }
    }

    /**
     * Call OpenAI API to translate text
     */
    private String callOpenAI(String text, String sourceLang, String targetLang) throws Exception {
        String sourceLanguage = LANGUAGE_NAMES.getOrDefault(sourceLang, sourceLang);
        String targetLanguage = LANGUAGE_NAMES.getOrDefault(targetLang, targetLang);

        // Build request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "gpt-3.5-turbo");

        ArrayNode messages = requestBody.putArray("messages");

        // System message
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content",
                "You are a professional translator. Translate the following text from "
                        + sourceLanguage + " to " + targetLanguage
                        + ". Only return the translated text, nothing else. Keep the same formatting.");

        // User message with text to translate
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", text);

        requestBody.put("max_tokens", 2000);
        requestBody.put("temperature", 0.3); // Lower temperature for more consistent translations

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_API_URL))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API returned status " + response.statusCode() + ": " + response.body());
        }

        // Parse response
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        JsonNode choices = jsonResponse.get("choices");

        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message != null && message.has("content")) {
                return message.get("content").asText().trim();
            }
        }

        throw new RuntimeException("No translation in OpenAI response");
    }

    /**
     * Batch translate multiple texts - OpenAI can handle this efficiently
     */
    public List<String> batchTranslate(List<String> texts, String sourceLang, String targetLang) {
        if (texts == null || texts.isEmpty()) {
            return texts;
        }

        if (sourceLang.equals(targetLang)) {
            return texts;
        }

        List<String> results = new ArrayList<>();
        for (String text : texts) {
            results.add(translate(text, sourceLang, targetLang));
        }
        return results;
    }

    /**
     * Translate asynchronously
     */
    public CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang) {
        return CompletableFuture.supplyAsync(() -> translate(text, sourceLang, targetLang));
    }

    /**
     * Cache translation with size limit
     */
    private void cacheTranslation(String key, String value) {
        translationCache.put(key, value);

        if (translationCache.size() > 10000) {
            // Clear 20% oldest entries
            Iterator<String> iterator = translationCache.keySet().iterator();
            int toRemove = 2000;
            while (iterator.hasNext() && toRemove > 0) {
                iterator.next();
                iterator.remove();
                toRemove--;
            }
            logger.info("Translation cache pruned to {} entries", translationCache.size());
        }
    }

    /**
     * Generate cache key
     */
    private String generateCacheKey(String text, String sourceLang, String targetLang) {
        return sourceLang + ":" + targetLang + ":" + text.hashCode();
    }

    /**
     * Clear translation cache
     */
    public void clearCache() {
        translationCache.clear();
        logger.info("Translation cache cleared");
    }

    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        return String.format("Translation cache: %d entries (OpenAI-powered)", translationCache.size());
    }

    /**
     * Check if language is supported
     */
    public boolean isLanguageSupported(String langCode) {
        return LANGUAGE_NAMES.containsKey(langCode);
    }

    /**
     * Pre-warm cache with common translations
     */
    public void prewarmCache() {
        logger.info("Pre-warming translation cache with common terms...");

        String[] commonTerms = {
                "Giải trí", "Thể thao", "Công nghệ", "Xe", "Thời trang trẻ", "Video",
                "Thời sự", "Chính trị", "Thế giới", "Kinh tế", "Đời sống", "Sức khỏe",
                "Giới trẻ", "Tiêu dùng", "Giáo dục", "Du lịch", "Văn hóa"
        };

        for (String term : commonTerms) {
            try {
                translate(term, VI, EN);
            } catch (Exception e) {
                logger.warn("Failed to prewarm term '{}': {}", term, e.getMessage());
            }
        }

        logger.info("Cache prewarming complete: {} entries", translationCache.size());
    }

    // ==================== PERSISTENT CACHE ====================

    private static final String CACHE_FILE = "translation_cache.json";

    @jakarta.annotation.PostConstruct
    public void init() {
        loadCacheFromFile();
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        saveCacheToFile();
    }

    private void loadCacheFromFile() {
        if (!cacheEnabled)
            return;
        try {
            java.io.File file = new java.io.File(CACHE_FILE);
            if (file.exists()) {
                com.fasterxml.jackson.core.type.TypeReference<ConcurrentHashMap<String, String>> typeRef = new com.fasterxml.jackson.core.type.TypeReference<>() {
                };
                Map<String, String> loaded = objectMapper.readValue(file, typeRef);
                translationCache.putAll(loaded);
                logger.info("Loaded {} translations from disk cache", loaded.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load translation cache from disk: {}", e.getMessage());
        }
    }

    private void saveCacheToFile() {
        if (!cacheEnabled)
            return;
        try {
            objectMapper.writeValue(new java.io.File(CACHE_FILE), translationCache);
            logger.info("Saved {} translations to disk cache", translationCache.size());
        } catch (Exception e) {
            logger.error("Failed to save translation cache to disk: {}", e.getMessage());
        }
    }
}