package com.thanhnien.rss.service;

import com.thanhnien.rss.model.Category;
import com.thanhnien.rss.model.HomePageData;
import com.thanhnien.rss.model.RssFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Scheduled component để tự động fetch data mỗi 5 phút.
 * Pre-fetch: HomePageData và tất cả Category Feeds.
 */
@Component
public class HomePageScheduler {

    private static final Logger logger = LoggerFactory.getLogger(HomePageScheduler.class);

    @Autowired
    private RssService rssService;

    @Autowired
    private HomePageCacheService cacheService;

    // Thread pool riêng cho background fetching
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(10);

    /**
     * Fetch data ngay khi server khởi động để có data sẵn
     */
    @PostConstruct
    public void initializeCache() {
        logger.info("=== Initializing all caches on startup ===");
        // Run in background thread để không block startup
        CompletableFuture.runAsync(() -> {
            refreshHomePageCache();
            refreshCategoryFeedsCache();
            logger.info("=== Cache initialization complete. {} ===", cacheService.getCacheStats());
        }, backgroundExecutor);
    }

    /**
     * Scheduled job chạy mỗi 5 phút (300,000 ms)
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledRefresh() {
        logger.info("=== Scheduled refresh started ===");
        refreshHomePageCache();
        refreshCategoryFeedsCache();
        logger.info("=== Scheduled refresh complete. {} ===", cacheService.getCacheStats());
    }

    /**
     * Refresh HomePageData cache
     */
    private void refreshHomePageCache() {
        try {
            long startTime = System.currentTimeMillis();
            HomePageData data = rssService.getHomePageData();
            long duration = System.currentTimeMillis() - startTime;

            if (data != null && data.getFeaturedArticles() != null) {
                cacheService.updateHomePageCache(data);
                logger.info("HomePageData refreshed in {}ms. Featured: {}, Categories: {}",
                        duration,
                        data.getFeaturedArticles().size(),
                        data.getCategorySections() != null ? data.getCategorySections().size() : 0);
            } else {
                logger.warn("Failed to refresh HomePageData - received null or empty data");
            }
        } catch (Exception e) {
            logger.error("Error refreshing HomePageData cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Refresh tất cả Category Feeds song song
     */
    private void refreshCategoryFeedsCache() {
        try {
            long startTime = System.currentTimeMillis();
            List<Category> allCategories = rssService.getAllCategories();

            // Collect tất cả categories và subcategories
            List<Category> flatCategories = allCategories.stream()
                    .flatMap(cat -> {
                        List<Category> list = new java.util.ArrayList<>();
                        list.add(cat);
                        if (cat.getSubCategories() != null) {
                            list.addAll(cat.getSubCategories());
                        }
                        return list.stream();
                    })
                    .filter(cat -> !"home".equals(cat.getSlug())) // Skip home
                    .collect(Collectors.toList());

            logger.info("Pre-fetching {} category feeds...", flatCategories.size());

            // Fetch song song
            List<CompletableFuture<Map.Entry<String, RssFeed>>> futures = flatCategories.stream()
                    .map(cat -> CompletableFuture.supplyAsync(() -> {
                        try {
                            RssFeed feed = rssService.getArticlesByCategory(cat.getSlug());
                            return Map.entry(cat.getSlug(), feed);
                        } catch (Exception e) {
                            logger.warn("Failed to fetch category {}: {}", cat.getSlug(), e.getMessage());
                            return null;
                        }
                    }, backgroundExecutor))
                    .collect(Collectors.toList());

            // Wait for all và collect results
            Map<String, RssFeed> feedsMap = new HashMap<>();
            for (CompletableFuture<Map.Entry<String, RssFeed>> future : futures) {
                try {
                    Map.Entry<String, RssFeed> entry = future.join();
                    if (entry != null && entry.getValue() != null) {
                        feedsMap.put(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    logger.warn("Error joining future: {}", e.getMessage());
                }
            }

            cacheService.updateAllCategoryFeeds(feedsMap);
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Category feeds refreshed in {}ms. Cached {} categories.", duration, feedsMap.size());

        } catch (Exception e) {
            logger.error("Error refreshing category feeds cache: {}", e.getMessage(), e);
        }
    }
}
