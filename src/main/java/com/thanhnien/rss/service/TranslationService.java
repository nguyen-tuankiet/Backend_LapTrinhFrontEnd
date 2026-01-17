package com.thanhnien.rss.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Service để dịch văn bản - sử dụng OpenAI (primary) hoặc MyMemory (fallback)
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

    @Autowired
    private DeepLService deepLService;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Cache translations
    private final ConcurrentHashMap<String, String> translationCache = new ConcurrentHashMap<>();

    // Rate limiter for MyMemory fallback only
    private final Semaphore rateLimiter;
    // Rate limiter for DeepL (max 3 concurrent)
    private final Semaphore deepLRateLimiter;
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
        this.rateLimiter = new Semaphore(20); // MyMemory: Allow 20 concurrent
        this.deepLRateLimiter = new Semaphore(2); // DeepL: Allow 2 concurrent safely

        // Refill 2 permits every second (allows bursts but maintains reasonable rate)
        scheduler.scheduleAtFixedRate(() -> {
            int current = rateLimiter.availablePermits();
            if (current < 20) {
                rateLimiter.release(Math.min(2, 20 - current));
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Translate text - uses OpenAI (primary) or MyMemory (fallback)
     */
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        if (sourceLang.equals(targetLang)) {
            return text;
        }

        // Validate target language format (must be 2 chars or supported code)
        if (targetLang == null || targetLang.length() > 2 || !isLanguageSupported(targetLang)) {
            logger.warn("Invalid target language: '{}'. Skeeping translation.", targetLang);
            return text;
        }

        // Check cache first
        String cacheKey = generateCacheKey(text, sourceLang, targetLang);
        if (cacheEnabled && translationCache.containsKey(cacheKey)) {
            return translationCache.get(cacheKey);
        }

        String translated = null;

        // Try DeepL first (High quality, faster)
        if (deepLService != null && deepLService.isConfigured()) {
            boolean acquired = false;
            try {
                // Strict rate limiting for DeepL Free tier (avoid parallel 429s)
                acquired = deepLRateLimiter.tryAcquire(5, TimeUnit.SECONDS);
                if (acquired) {
                    logger.debug("Using DeepL for translation: {} -> {}", sourceLang, targetLang);
                    translated = deepLService.translate(text, sourceLang, targetLang);
                    if (translated != null && !translated.equals(text)) {
                        logger.debug("DeepL translation successful");
                        // Cache successful translation
                        if (cacheEnabled) {
                            cacheTranslation(cacheKey, translated);
                        }
                        return translated;
                    }
                } else {
                    logger.warn("DeepL rate limit hit (local semaphore), falling back to MyMemory");
                }
            } catch (Exception e) {
                logger.warn("DeepL translation failed, falling back to MyMemory: {}", e.getMessage());
            } finally {
                if (acquired) {
                    deepLRateLimiter.release();
                }
            }
        } else {
            logger.debug("DeepL not configured, using MyMemory fallback");
        }

        // Fallback to MyMemory (with rate limiting)
        try {
            // Truncate for MyMemory limit
            String textToTranslate = text.length() > 500 ? text.substring(0, 500) : text;

            if (rateLimitEnabled) {
                rateLimiter.acquire();
            }

            translated = callMyMemoryAPI(textToTranslate, sourceLang, targetLang);

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
            String errorMsg = jsonResponse.has("responseDetails") ? jsonResponse.get("responseDetails").asText()
                    : "Unknown error";
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
    public java.util.List<String> translateBatch(java.util.List<String> texts, String sourceLang, String targetLang) {
        // Validate target language format (must be 2 chars or supported code)
        if (targetLang == null || targetLang.length() > 2 || !isLanguageSupported(targetLang)) {
            logger.warn("Batch translation skipped: Invalid target language '{}'", targetLang);
            return texts;
        }

        if (deepLService != null && deepLService.isConfigured()) {
            boolean acquired = false;
            try {
                acquired = deepLRateLimiter.tryAcquire(10, TimeUnit.SECONDS); // 10s timeout for bigger batch
                if (acquired) {
                    logger.debug("Batch translating {} items with DeepL", texts.size());
                    java.util.List<String> translated = deepLService.translateBatch(texts, sourceLang, targetLang);
                    // Cache results
                    if (cacheEnabled && translated != null && translated.size() == texts.size()) {
                        for (int i = 0; i < texts.size(); i++) {
                            String original = texts.get(i);
                            String trans = translated.get(i);
                            if (trans != null) {
                                cacheTranslation(generateCacheKey(original, sourceLang, targetLang), trans);
                            }
                        }
                    }
                    return translated;
                }
            } catch (Exception e) {
                logger.error("Batch translation failed: {}", e.getMessage());
            } finally {
                if (acquired) {
                    deepLRateLimiter.release();
                }
            }
        }
        // Fallback or if not configured: translate individually (slow but safe)
        java.util.List<String> results = new java.util.ArrayList<>();
        for (String t : texts) {
            results.add(translate(t, sourceLang, targetLang));
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