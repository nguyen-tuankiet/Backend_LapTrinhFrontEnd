package com.thanhnien.rss.service;

import com.thanhnien.rss.model.ArticleDetail;
import com.thanhnien.rss.model.HomePageData;
import com.thanhnien.rss.model.RssFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service quản lý tất cả cache trong RAM.
 * Bao gồm: HomePageData, Category Feeds, và Article Details.
 */
@Service
public class HomePageCacheService {

    private static final Logger logger = LoggerFactory.getLogger(HomePageCacheService.class);

    // Cache cho HomePageData
    private volatile HomePageData cachedHomePageData;
    private volatile Instant homePageLastUpdated;

    // Cache cho Category Feeds (key = category slug)
    private final ConcurrentHashMap<String, RssFeed> categoryFeedsCache = new ConcurrentHashMap<>();
    private volatile Instant categoryFeedsLastUpdated;

    // Cache cho Article Details với LRU eviction (max 500 articles)
    private static final int MAX_ARTICLE_CACHE_SIZE = 500;
    private final Map<String, ArticleDetail> articleCache = new LinkedHashMap<String, ArticleDetail>(
            MAX_ARTICLE_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ArticleDetail> eldest) {
            boolean shouldRemove = size() > MAX_ARTICLE_CACHE_SIZE;
            if (shouldRemove) {
                logger.debug("Evicting oldest article from cache: {}", eldest.getKey());
            }
            return shouldRemove;
        }
    };

    // ==================== HOME PAGE DATA ====================

    /**
     * Cập nhật cache HomePageData
     */
    public void updateHomePageCache(HomePageData data) {
        this.cachedHomePageData = data;
        this.homePageLastUpdated = Instant.now();
        logger.info("HomePageData cached successfully at {}", homePageLastUpdated);
    }

    /**
     * Lấy cached HomePageData từ RAM
     */
    public HomePageData getCachedHomePageData() {
        return cachedHomePageData;
    }

    /**
     * Kiểm tra xem có HomePageData trong cache không
     */
    public boolean hasCachedData() {
        return cachedHomePageData != null;
    }

    /**
     * Lấy thời gian update cuối cùng của HomePageData
     */
    public Instant getHomePageLastUpdated() {
        return homePageLastUpdated;
    }

    // Backward compatibility
    public HomePageData getCachedData() {
        return getCachedHomePageData();
    }

    public void updateCache(HomePageData data) {
        updateHomePageCache(data);
    }

    // ==================== CATEGORY FEEDS ====================

    /**
     * Cập nhật cache cho một category
     */
    public void updateCategoryFeed(String slug, RssFeed feed) {
        categoryFeedsCache.put(slug, feed);
    }

    /**
     * Cập nhật tất cả category feeds cùng lúc
     */
    public void updateAllCategoryFeeds(Map<String, RssFeed> feeds) {
        categoryFeedsCache.clear();
        categoryFeedsCache.putAll(feeds);
        categoryFeedsLastUpdated = Instant.now();
        logger.info("Category feeds cached: {} categories at {}", feeds.size(), categoryFeedsLastUpdated);
    }

    /**
     * Lấy cached feed cho một category
     */
    public RssFeed getCachedCategoryFeed(String slug) {
        return categoryFeedsCache.get(slug);
    }

    /**
     * Kiểm tra xem có cached feed cho category không
     */
    public boolean hasCachedCategoryFeed(String slug) {
        return categoryFeedsCache.containsKey(slug);
    }

    /**
     * Lấy số lượng category feeds đang cache
     */
    public int getCategoryFeedsCacheSize() {
        return categoryFeedsCache.size();
    }

    // ==================== ARTICLE DETAILS ====================

    /**
     * Lưu article detail vào cache (thread-safe với synchronized)
     */
    public void cacheArticle(String url, ArticleDetail article) {
        synchronized (articleCache) {
            articleCache.put(url, article);
        }
        logger.debug("Article cached: {}", url);
    }

    /**
     * Lấy cached article detail
     */
    public ArticleDetail getCachedArticle(String url) {
        synchronized (articleCache) {
            return articleCache.get(url);
        }
    }

    /**
     * Kiểm tra xem article có trong cache không
     */
    public boolean hasCachedArticle(String url) {
        synchronized (articleCache) {
            return articleCache.containsKey(url);
        }
    }

    /**
     * Lấy số lượng articles đang cache
     */
    public int getArticleCacheSize() {
        synchronized (articleCache) {
            return articleCache.size();
        }
    }

    // ==================== STATS ====================

    /**
     * Lấy thống kê cache
     */
    public String getCacheStats() {
        return String.format(
                "Cache Stats: HomePageData=%s, CategoryFeeds=%d, Articles=%d",
                hasCachedData() ? "YES" : "NO",
                getCategoryFeedsCacheSize(),
                getArticleCacheSize());
    }
}
