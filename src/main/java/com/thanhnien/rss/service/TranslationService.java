package com.thanhnien.rss.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service để dịch văn bản sử dụng MyMemory Translation API (miễn phí, 1000 req/day)
 */
@Service
public class TranslationService {

    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);

    // MyMemory API - Free, no API key required, 1000 requests/day
    private static final String MYMEMORY_API_URL = "https://api.mymemory.translated.net/get";

    @Value("${translation.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${translation.rate.limit.enabled:true}")
    private boolean rateLimitEnabled;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Cache translations
    private final ConcurrentHashMap<String, String> translationCache = new ConcurrentHashMap<>();

    // Rate limiter: 1000 requests per day = ~1 per 90 seconds, but we can burst
    // Using semaphore for simple rate limiting
    private final Semaphore rateLimiter;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Supported languages
    public static final String VI = "vi";
    public static final String EN = "en";
    public static final String ZH = "zh";
    public static final String JA = "ja";
    public static final String KO = "ko";
    public static final String TH = "th";
    public static final String FR = "fr";
    public static final String ES = "es";

    public TranslationService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.rateLimiter = new Semaphore(20); // Allow 20 concurrent requests

        // Refill 2 permits every second (allows bursts but maintains reasonable rate)
        scheduler.scheduleAtFixedRate(() -> {
            int current = rateLimiter.availablePermits();
            if (current < 20) {
                rateLimiter.release(Math.min(2, 20 - current));
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Translate text using MyMemory API
     */
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        if (sourceLang.equals(targetLang)) {
            return text;
        }

        // Truncate very long text to avoid API limits
        String textToTranslate = text;
        if (text.length() > 500) {
            textToTranslate = text.substring(0, 500);
            logger.debug("Truncated text from {} to 500 chars", text.length());
        }

        // Check cache first
        String cacheKey = generateCacheKey(textToTranslate, sourceLang, targetLang);
        if (cacheEnabled && translationCache.containsKey(cacheKey)) {
            return translationCache.get(cacheKey);
        }

        try {
            // Acquire rate limit permit
            if (rateLimitEnabled) {
                rateLimiter.acquire();
            }

            String translated = callMyMemoryAPI(textToTranslate, sourceLang, targetLang);

            // Cache the result
            if (cacheEnabled && translated != null) {
                cacheTranslation(cacheKey, translated);
            }

            return translated;
        } catch (Exception e) {
            logger.error("Translation failed for '{}' ({} -> {}): {}",
                    textToTranslate.substring(0, Math.min(50, textToTranslate.length())),
                    sourceLang, targetLang, e.getMessage());
            return text; // Return original on error
        }
    }

    /**
     * Call MyMemory Translation API
     * API Docs: https://mymemory.translated.net/doc/spec.php
     */
    private String callMyMemoryAPI(String text, String sourceLang, String targetLang) throws Exception {
        // Construct language pair (e.g., "vi|en")
        String langPair = sourceLang + "|" + targetLang;

        // Build URL with query parameters
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String encodedLangPair = URLEncoder.encode(langPair, StandardCharsets.UTF_8);
        String url = String.format("%s?q=%s&langpair=%s", MYMEMORY_API_URL, encodedText, encodedLangPair);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API returned status " + response.statusCode());
        }

        // Parse response
        JsonNode jsonResponse = objectMapper.readTree(response.body());

        // Check for errors
        if (jsonResponse.has("responseStatus") && jsonResponse.get("responseStatus").asInt() != 200) {
            String errorMsg = jsonResponse.has("responseDetails") ?
                    jsonResponse.get("responseDetails").asText() : "Unknown error";
            throw new RuntimeException("Translation API error: " + errorMsg);
        }

        // Extract translated text
        if (jsonResponse.has("responseData") && jsonResponse.get("responseData").has("translatedText")) {
            return jsonResponse.get("responseData").get("translatedText").asText();
        }

        throw new RuntimeException("No translation in response");
    }

    /**
     * Batch translate
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
        return String.format("Translation cache: %d entries, Rate limiter permits: %d",
                translationCache.size(), rateLimiter.availablePermits());
    }

    /**
     * Check if language is supported
     */
    public boolean isLanguageSupported(String langCode) {
        return langCode.matches("^(vi|en|zh|ja|ko|th|fr|es|de|it|pt|ru|ar)$");
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
                Thread.sleep(200); // Small delay between requests
            } catch (Exception e) {
                logger.warn("Failed to prewarm term '{}': {}", term, e.getMessage());
            }
        }

        logger.info("Cache prewarming complete: {} entries", translationCache.size());
    }
}