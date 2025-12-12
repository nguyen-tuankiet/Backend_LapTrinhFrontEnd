package com.thanhnien.rss.controller;

import com.thanhnien.rss.model.Category;
import com.thanhnien.rss.model.RssFeed;
import com.thanhnien.rss.model.HomePageData;
import com.thanhnien.rss.service.RssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rss")
@CrossOrigin(origins = "*")
public class RssController {

    @Autowired
    private RssService rssService;

    @Autowired
    private com.thanhnien.rss.service.ArticleScraperService articleScraperService;

    /**
     * Get home page articles
     * GET /api/rss/home
     */
    @GetMapping("/home")
    public ResponseEntity<RssFeed> getHomeArticles() {
        return ResponseEntity.ok(rssService.getHomeArticles());
    }

    /**
     * Get aggregated home page data
     * GET /api/rss/home-page
     */
    @GetMapping("/home-page")
    public ResponseEntity<HomePageData> getHomePageData() {
        return ResponseEntity.ok(rssService.getHomePageData());
    }

    /**
     * Get article detail by URL
     * GET /api/rss/article?url=...
     */
    @GetMapping("/article")
    public ResponseEntity<com.thanhnien.rss.model.ArticleDetail> getArticleDetail(@RequestParam String url) {
        return ResponseEntity.ok(articleScraperService.scrapeArticle(url));
    }

    /**
     * Get all categories
     * GET /api/rss/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(rssService.getAllCategories());
    }

    /**
     * Get articles by category slug
     * GET /api/rss/category/{slug}
     */
    @GetMapping("/category/{slug}")
    public ResponseEntity<RssFeed> getArticlesByCategory(@PathVariable String slug) {
        return ResponseEntity.ok(rssService.getArticlesByCategory(slug));
    }

    /**
     * Get articles from subcategory
     * GET /api/rss/category/{category}/{subcategory}
     */
    @GetMapping("/category/{category}/{subcategory}")
    public ResponseEntity<RssFeed> getArticlesBySubCategory(
            @PathVariable String category,
            @PathVariable String subcategory) {
        return ResponseEntity.ok(rssService.getArticlesByCategory(subcategory));
    }

    /**
     * Fetch RSS from custom URL
     * GET /api/rss/fetch?url=...
     */
    @GetMapping("/fetch")
    public ResponseEntity<RssFeed> fetchRss(@RequestParam String url) {
        return ResponseEntity.ok(rssService.fetchRss(url));
    }

    /**
     * Get all feeds from all categories
     * GET /api/rss/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<RssFeed>> getAllFeeds() {
        return ResponseEntity.ok(rssService.getAllFeeds());
    }
}
