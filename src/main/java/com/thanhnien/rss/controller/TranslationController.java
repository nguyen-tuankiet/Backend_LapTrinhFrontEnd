package com.thanhnien.rss.controller;

import com.thanhnien.rss.model.ArticleDetail;
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
import java.util.Map;

/**
 * Controller for translation endpoints
 */
@RestController
@RequestMapping("/api/translate")
@CrossOrigin(origins = "*")
public class TranslationController {

    @Autowired
    private TranslationService translationService;

    @Autowired
    private ArticleTranslationService articleTranslationService;

    @Autowired
    private RssService rssService;

    @Autowired
    private HomePageCacheService cacheService;

    @Autowired
    private ArticleScraperService articleScraperService;

    /**
     * Translate plain text
     * GET /api/translate/text?text=...&from=vi&to=en
     */
    @GetMapping("/text")
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
     * Get home page data with translation
     * GET /api/translate/home?lang=en
     */
    @GetMapping("/home")
    public ResponseEntity<HomePageData> getHomePageTranslated(
            @RequestParam(defaultValue = "vi") String lang) {

        HomePageData data = cacheService.hasCachedData()
                ? cacheService.getCachedData()
                : rssService.getHomePageData();

        if (!lang.equals("vi")) {
            data = articleTranslationService.translateHomePageData(data, lang);
        }

        return ResponseEntity.ok(data);
    }

    /**
     * Get home articles with translation
     * GET /api/translate/home-articles?lang=en
     */
    @GetMapping("/home-articles")
    public ResponseEntity<RssFeed> getHomeArticlesTranslated(
            @RequestParam(defaultValue = "vi") String lang) {

        RssFeed feed = rssService.getHomeArticles();

        if (!lang.equals("vi")) {
            feed = articleTranslationService.translateRssFeed(feed, lang);
        }

        return ResponseEntity.ok(feed);
    }

    /**
     * Get category articles with translation
     * GET /api/translate/category/{slug}?lang=en
     */
    @GetMapping("/category/{slug}")
    public ResponseEntity<RssFeed> getCategoryArticlesTranslated(
            @PathVariable String slug,
            @RequestParam(defaultValue = "vi") String lang) {

        RssFeed feed = cacheService.hasCachedCategoryFeed(slug)
                ? cacheService.getCachedCategoryFeed(slug)
                : rssService.getArticlesByCategory(slug);

        if (!lang.equals("vi")) {
            feed = articleTranslationService.translateRssFeed(feed, lang);
        }

        return ResponseEntity.ok(feed);
    }

    /**
     * Get article detail with translation
     * GET /api/translate/article?url=...&lang=en
     */
    @GetMapping("/article")
    public ResponseEntity<ArticleDetail> getArticleDetailTranslated(
            @RequestParam String url,
            @RequestParam(defaultValue = "vi") String lang) {

        ArticleDetail article = articleScraperService.scrapeArticle(url);

        if (!lang.equals("vi")) {
            article = articleTranslationService.translateArticleDetail(article, lang);
        }

        return ResponseEntity.ok(article);
    }

    /**
     * Get supported languages
     * GET /api/translate/languages
     */
    @GetMapping("/languages")
    public ResponseEntity<Map<String, String>> getSupportedLanguages() {
        Map<String, String> languages = new HashMap<>();
        languages.put("vi", "Tiếng Việt (Vietnamese)");
        languages.put("en", "English");
        languages.put("zh", "中文 (Chinese)");
        languages.put("ja", "日本語 (Japanese)");
        languages.put("ko", "한국어 (Korean)");
        languages.put("th", "ไทย (Thai)");
        languages.put("fr", "Français (French)");
        languages.put("es", "Español (Spanish)");

        return ResponseEntity.ok(languages);
    }

    /**
     * Clear translation cache
     * POST /api/translate/cache/clear
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearCache() {
        translationService.clearCache();
        return ResponseEntity.ok("Translation cache cleared successfully");
    }

    /**
     * Get translation cache statistics
     * GET /api/translate/cache/stats
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<String> getCacheStats() {
        return ResponseEntity.ok(translationService.getCacheStats());
    }
}