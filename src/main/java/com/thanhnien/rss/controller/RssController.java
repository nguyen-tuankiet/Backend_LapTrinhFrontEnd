package com.thanhnien.rss.controller;

import com.thanhnien.rss.model.ArticleDetail;
import com.thanhnien.rss.model.Category;
import com.thanhnien.rss.model.HomePageData;
import com.thanhnien.rss.model.RssFeed;
import com.thanhnien.rss.service.ArticleScraperService;
import com.thanhnien.rss.service.ArticleTranslationService;
import com.thanhnien.rss.service.HomePageCacheService;
import com.thanhnien.rss.service.RssService;
import com.thanhnien.rss.service.TranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified RSS Controller with built-in translation support
 * Default language: Vietnamese (vi)
 * Add ?lang=en for English, ?lang=ja for Japanese, etc.
 */
@RestController
@RequestMapping("/api/rss")
@CrossOrigin(origins = "*")
public class RssController {

    @Autowired
    private RssService rssService;

    @Autowired
    private HomePageCacheService cacheService;

    @Autowired
    private ArticleScraperService articleScraperService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private ArticleTranslationService articleTranslationService;

    /**
     * Get home page articles
     * GET /api/rss/home
     * GET /api/rss/home?lang=en (English)
     */
    @GetMapping("/home")
    public ResponseEntity<RssFeed> getHomeArticles(
            @RequestParam(defaultValue = "vi") String lang) {

        lang = sanitizeLang(lang);
        RssFeed feed = rssService.getHomeArticles();

        // Translate if not Vietnamese
        if (!lang.equals("vi")) {
            feed = articleTranslationService.translateRssFeed(feed, lang);
        }

        return ResponseEntity.ok(feed);
    }

    /**
     * Get aggregated home page data from cache
     * GET /api/rss/home-page
     * GET /api/rss/home-page?lang=en (English)
     */
    @GetMapping("/home-page")
    public ResponseEntity<HomePageData> getHomePageData(
            @RequestParam(defaultValue = "vi") String lang) {

        lang = sanitizeLang(lang);
        // Check translated cache first for non-Vietnamese requests
        if (!lang.equals("vi")) {
            HomePageData cachedTranslated = cacheService.getTranslatedHomePageData(lang);
            if (cachedTranslated != null) {
                return ResponseEntity.ok(cachedTranslated); // Instant response from cache!
            }
        }

        // Get Vietnamese data from cache or fetch directly
        HomePageData data = cacheService.hasCachedData()
                ? cacheService.getCachedData()
                : rssService.getHomePageData();

        // Translate on-demand if not Vietnamese and no cached translation
        if (!lang.equals("vi")) {
            data = articleTranslationService.translateHomePageData(data, lang);
            // Cache for future requests
            cacheService.cacheTranslatedHomePageData(data, lang);
        }

        return ResponseEntity.ok(data);
    }

    /**
     * Get article detail by URL
     * GET /api/rss/article?url=...
     * GET /api/rss/article?url=...&lang=en (English)
     */
    @GetMapping("/article")
    public ResponseEntity<ArticleDetail> getArticleDetail(
            @RequestParam String url,
            @RequestParam(defaultValue = "vi") String lang) {

        lang = sanitizeLang(lang);
        ArticleDetail article = articleScraperService.scrapeArticle(url);

        // Translate if not Vietnamese
        if (!lang.equals("vi")) {
            article = articleTranslationService.translateArticleDetail(article, lang);
        }

        return ResponseEntity.ok(article);
    }

    /**
     * Get all categories
     * GET /api/rss/categories
     * GET /api/rss/categories?lang=en
     */
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getAllCategories(
            @RequestParam(defaultValue = "vi") String lang) {
        lang = sanitizeLang(lang);
        List<Category> categories = rssService.getAllCategories();

        // Translate if not Vietnamese
        if (!lang.equals("vi")) {
            categories = articleTranslationService.translateCategories(categories, lang);
        }

        return ResponseEntity.ok(categories);
    }

    /**
     * Get articles by category slug
     * GET /api/rss/category/{slug}
     * GET /api/rss/category/{slug}?lang=en (English)
     */
    @GetMapping("/category/{slug}")
    public ResponseEntity<RssFeed> getArticlesByCategory(
            @PathVariable String slug,
            @RequestParam(defaultValue = "vi") String lang) {

        lang = sanitizeLang(lang);
        // Check translated cache first for non-Vietnamese requests
        if (!lang.equals("vi")) {
            RssFeed cachedTranslated = cacheService.getTranslatedCategoryFeed(slug, lang);
            if (cachedTranslated != null) {
                return ResponseEntity.ok(cachedTranslated); // Instant response from cache!
            }
        }

        // Get Vietnamese data from cache or fetch directly
        RssFeed feed = cacheService.hasCachedCategoryFeed(slug)
                ? cacheService.getCachedCategoryFeed(slug)
                : rssService.getArticlesByCategory(slug);

        // Translate on-demand if not Vietnamese and no cached translation
        if (!lang.equals("vi")) {
            feed = articleTranslationService.translateRssFeed(feed, lang);
            // Cache for future requests
            cacheService.cacheTranslatedCategoryFeed(slug, lang, feed);
        }

        return ResponseEntity.ok(feed);
    }

    /**
     * Get articles from subcategory
     * GET /api/rss/category/{category}/{subcategory}
     * GET /api/rss/category/{category}/{subcategory}?lang=en
     */
    @GetMapping("/category/{category}/{subcategory}")
    public ResponseEntity<RssFeed> getArticlesBySubCategory(
            @PathVariable String category,
            @PathVariable String subcategory,
            @RequestParam(defaultValue = "vi") String lang) {

        lang = sanitizeLang(lang);
        // Check translated cache first for non-Vietnamese requests
        if (!lang.equals("vi")) {
            RssFeed cachedTranslated = cacheService.getTranslatedCategoryFeed(subcategory, lang);
            if (cachedTranslated != null) {
                return ResponseEntity.ok(cachedTranslated);
            }
        }

        // Get Vietnamese data from cache or fetch directly
        RssFeed feed = cacheService.hasCachedCategoryFeed(subcategory)
                ? cacheService.getCachedCategoryFeed(subcategory)
                : rssService.getArticlesByCategory(subcategory);

        // Translate on-demand if not Vietnamese
        if (!lang.equals("vi")) {
            feed = articleTranslationService.translateRssFeed(feed, lang);
            cacheService.cacheTranslatedCategoryFeed(subcategory, lang, feed);
        }

        return ResponseEntity.ok(feed);
    }

    /**
     * Fetch RSS from custom URL
     * GET /api/rss/fetch?url=...
     * GET /api/rss/fetch?url=...&lang=en
     */
    @GetMapping("/fetch")
    public ResponseEntity<RssFeed> fetchRss(
            @RequestParam String url,
            @RequestParam(defaultValue = "vi") String lang) {

        lang = sanitizeLang(lang);
        RssFeed feed = rssService.fetchRss(url);

        // Translate if not Vietnamese
        if (!lang.equals("vi")) {
            feed = articleTranslationService.translateRssFeed(feed, lang);
        }

        return ResponseEntity.ok(feed);
    }

    /**
     * Get all feeds from all categories
     * GET /api/rss/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<RssFeed>> getAllFeeds() {
        return ResponseEntity.ok(rssService.getAllFeeds());
    }

    // ==================== TRANSLATION UTILITIES ====================

    /**
     * Translate plain text
     * GET /api/rss/translate?text=...&from=vi&to=en
     */
    @GetMapping("/translate")
    public ResponseEntity<Map<String, String>> translateText(
            @RequestParam String text,
            @RequestParam(defaultValue = "vi") String from,
            @RequestParam(defaultValue = "en") String to) {

        String translated = translationService.translate(text, from, to);

        Map<String, String> response = new HashMap<>();
        response.put("original", text);
        response.put("translated", translated);
        response.put("sourceLang", from);
        response.put("targetLang", to);

        return ResponseEntity.ok(response);
    }

    /**
     * Get supported languages for translation
     * GET /api/rss/languages
     */
    @GetMapping("/languages")
    public ResponseEntity<Map<String, String>> getSupportedLanguages() {
        Map<String, String> languages = new HashMap<>();
        languages.put("vi", "🇻🇳 Tiếng Việt (Vietnamese)");
        languages.put("en", "🇬🇧 English");
        languages.put("zh", "🇨🇳 中文 (Chinese)");
        languages.put("ja", "🇯🇵 日本語 (Japanese)");
        languages.put("ko", "🇰🇷 한국어 (Korean)");
        languages.put("th", "🇹🇭 ไทย (Thai)");
        languages.put("fr", "🇫🇷 Français (French)");
        languages.put("es", "🇪🇸 Español (Spanish)");

        return ResponseEntity.ok(languages);
    }

    /**
     * Clear translation cache
     * POST /api/rss/cache/clear-translation
     */
    @PostMapping("/cache/clear-translation")
    public ResponseEntity<String> clearTranslationCache() {
        translationService.clearCache();
        return ResponseEntity.ok("Translation cache cleared successfully");
    }

    /**
     * Get cache statistics (RSS + Translation)
     * GET /api/rss/cache-stats
     */
    @GetMapping("/cache-stats")
    public ResponseEntity<Map<String, String>> getCacheStats() {
        Map<String, String> stats = new HashMap<>();
        stats.put("rssCache", cacheService.getCacheStats());
        stats.put("translationCache", translationService.getCacheStats());
        return ResponseEntity.ok(stats);
    }

    // ==================== HELPERS ====================

    /**
     * Sanitize and extract valid language code from potentially malformed input
     * Handles cases like "vihome-page?lang=en" -> extracts "en"
     */
    private String sanitizeLang(String lang) {
        if (lang == null || lang.isEmpty()) {
            return "vi";
        }

        // precise match
        if (lang.length() == 2 && translationService.isLanguageSupported(lang)) {
            return lang;
        }

        // Try to find "lang=xx" inside the string (e.g. junk?lang=en)
        if (lang.contains("lang=")) {
            int index = lang.indexOf("lang=");
            if (index + 7 <= lang.length()) { // lang=xx check length
                String code = lang.substring(index + 5, index + 7);
                if (translationService.isLanguageSupported(code)) {
                    return code;
                }
            }
        }

        // Try to just take the first 2 chars if they are valid
        if (lang.length() >= 2) {
            String code = lang.substring(0, 2);
            if (translationService.isLanguageSupported(code)) {
                return code;
            }
        }

        return "vi";
    }
}