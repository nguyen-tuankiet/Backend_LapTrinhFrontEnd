package com.thanhnien.rss.service;

import com.thanhnien.rss.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service để dịch các model - sử dụng sequential translation để tránh rate
 * limit
 */
@Service
public class ArticleTranslationService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleTranslationService.class);

    @Autowired
    private TranslationService translationService;

    @Autowired
    private LocalizationService localizationService;

    private static final String DEFAULT_SOURCE_LANG = TranslationService.VI;

    /**
     * Translate a single Article
     */
    public Article translateArticle(Article article, String targetLang) {
        if (article == null || targetLang.equals(DEFAULT_SOURCE_LANG)) {
            return article;
        }

        try {
            return Article.builder()
                    .title(translationService.translate(article.getTitle(), DEFAULT_SOURCE_LANG, targetLang))
                    .link(article.getLink())
                    .description(
                            translationService.translate(article.getDescription(), DEFAULT_SOURCE_LANG, targetLang))
                    .pubDate(article.getPubDate())
                    .imageUrl(article.getImageUrl())
                    .videoUrl(article.getVideoUrl())
                    .category(article.getCategory() != null && !article.getCategory().isEmpty()
                            ? translateCategoryName(article.getCategory(), targetLang)
                            : article.getCategory())
                    .author(article.getAuthor())
                    .build();
        } catch (Exception e) {
            logger.error("Error translating article '{}': {}", article.getTitle(), e.getMessage());
            return article;
        }
    }

    /**
     * Translate list of Articles efficiently with smart caching
     */
    public List<Article> translateArticles(List<Article> articles, String targetLang) {
        if (articles == null || articles.isEmpty() || targetLang.equals(DEFAULT_SOURCE_LANG)) {
            return articles;
        }

        try {
            logger.info("Translating {} articles to {}", articles.size(), targetLang);
            long startTime = System.currentTimeMillis();

            // Process articles in PARALLEL
            // Note: TranslationService itself has rate limiting (Semaphore), so parallel
            // calls
            // will just queue up and execute as fast as permitted, maximizing throughput.
            List<CompletableFuture<Article>> futures = articles.stream()
                    .map(article -> CompletableFuture.supplyAsync(() -> translateArticle(article, targetLang)))
                    .collect(Collectors.toList());

            List<Article> translatedArticles = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Translated {} articles in {}ms (parallel)", articles.size(), duration);

            return translatedArticles;
        } catch (Exception e) {
            logger.error("Error translating articles: {}", e.getMessage());
            return articles;
        }
    }

    /**
     * Translate ArticleDetail
     */
    public ArticleDetail translateArticleDetail(ArticleDetail article, String targetLang) {
        if (article == null || targetLang.equals(DEFAULT_SOURCE_LANG)) {
            return article;
        }

        try {
            logger.info("Translating article detail: {}", article.getTitle());

            return ArticleDetail.builder()
                    .title(translationService.translate(article.getTitle(), DEFAULT_SOURCE_LANG, targetLang))
                    .url(article.getUrl())
                    .description(
                            translationService.translate(article.getDescription(), DEFAULT_SOURCE_LANG, targetLang))
                    .content(translationService.translate(article.getContent(), DEFAULT_SOURCE_LANG, targetLang))
                    .author(article.getAuthor())
                    .pubDate(article.getPubDate())
                    .category(article.getCategory() != null
                            ? translateCategoryName(article.getCategory(), targetLang)
                            : null)
                    .imageUrl(article.getImageUrl())
                    .images(article.getImages())
                    .tags(article.getTags() != null ? translateTags(article.getTags(), targetLang) : null)
                    .build();
        } catch (Exception e) {
            logger.error("Error translating article detail: {}", e.getMessage());
            return article;
        }
    }

    /**
     * Translate RssFeed
     */
    public RssFeed translateRssFeed(RssFeed feed, String targetLang) {
        if (feed == null || targetLang.equals(DEFAULT_SOURCE_LANG)) {
            return feed;
        }

        try {
            return RssFeed.builder()
                    .title(translationService.translate(feed.getTitle(), DEFAULT_SOURCE_LANG, targetLang))
                    .description(translationService.translate(feed.getDescription(), DEFAULT_SOURCE_LANG, targetLang))
                    .link(feed.getLink())
                    .language(targetLang)
                    .articles(translateArticles(feed.getArticles(), targetLang))
                    .build();
        } catch (Exception e) {
            logger.error("Error translating RSS feed: {}", e.getMessage());
            return feed;
        }
    }

    /**
     * Translate CategorySection
     */
    public CategorySection translateCategorySection(CategorySection section, String targetLang) {
        if (section == null || targetLang.equals(DEFAULT_SOURCE_LANG)) {
            return section;
        }

        try {
            return CategorySection.builder()
                    .categoryName(
                            translateCategoryName(section.getCategoryName(), targetLang))
                    .categorySlug(section.getCategorySlug())
                    .articles(translateArticles(section.getArticles(), targetLang))
                    .build();
        } catch (Exception e) {
            logger.error("Error translating category section '{}': {}", section.getCategoryName(), e.getMessage());
            return section;
        }
    }

    /**
     * Translate HomePageData - Most efficient approach
     */
    public HomePageData translateHomePageData(HomePageData data, String targetLang) {
        if (data == null || targetLang.equals(DEFAULT_SOURCE_LANG)) {
            return data;
        }

        try {
            logger.info("Translating HomePageData to {}", targetLang);
            long startTime = System.currentTimeMillis();

            // 1. Featured Articles
            CompletableFuture<List<Article>> featuredFuture = CompletableFuture.supplyAsync(() -> translateArticles(
                    data.getFeaturedArticles() != null ? data.getFeaturedArticles() : new ArrayList<>(), targetLang));

            // 2. Trending Articles
            CompletableFuture<List<Article>> trendingFuture = CompletableFuture.supplyAsync(() -> translateArticles(
                    data.getTrendingArticles() != null ? data.getTrendingArticles() : new ArrayList<>(), targetLang));

            // 3. Most Read Articles
            CompletableFuture<List<Article>> mostReadFuture = CompletableFuture.supplyAsync(() -> translateArticles(
                    data.getMostReadArticles() != null ? data.getMostReadArticles() : new ArrayList<>(), targetLang));

            // 4. Category Sections
            List<CompletableFuture<CategorySection>> sectionFutures = new ArrayList<>();
            if (data.getCategorySections() != null) {
                for (CategorySection section : data.getCategorySections()) {
                    sectionFutures
                            .add(CompletableFuture.supplyAsync(() -> translateCategorySection(section, targetLang)));
                }
            }

            // Wait for all
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    featuredFuture, trendingFuture, mostReadFuture,
                    CompletableFuture.allOf(sectionFutures.toArray(new CompletableFuture[0])));

            allFutures.join();

            long duration = System.currentTimeMillis() - startTime;
            logger.info("HomePageData translated in {}ms (parallel)", duration);

            return HomePageData.builder()
                    .featuredArticles(featuredFuture.get())
                    .categorySections(sectionFutures.stream().map(CompletableFuture::join).collect(Collectors.toList()))
                    .trendingArticles(trendingFuture.get())
                    .mostReadArticles(mostReadFuture.get())
                    .build();
        } catch (Exception e) {
            logger.error("Error translating home page data: {}", e.getMessage(), e);
            return data;
        }
    }

    /**
     * Translate a list of Categories
     */
    public List<Category> translateCategories(List<Category> categories, String targetLang) {
        if (categories == null || categories.isEmpty() || targetLang.equals(DEFAULT_SOURCE_LANG)) {
            return categories;
        }

        return categories.stream()
                .map(category -> translateCategory(category, targetLang))
                .collect(Collectors.toList());
    }

    /**
     * Translate a single Category recursively
     */
    public Category translateCategory(Category category, String targetLang) {
        if (category == null || targetLang.equals(DEFAULT_SOURCE_LANG)) {
            return category;
        }

        try {
            String localizedName = localizationService.getCategoryName(category.getSlug(), targetLang);
            if (localizedName == null) {
                // If not found in props, fallback to translation service or original
                localizedName = translationService.translate(category.getName(), DEFAULT_SOURCE_LANG, targetLang);
            }

            Category translated = Category.builder()
                    .name(localizedName)
                    .slug(category.getSlug())
                    .rssUrl(category.getRssUrl())
                    .build();

            if (category.getSubCategories() != null && !category.getSubCategories().isEmpty()) {
                translated.setSubCategories(translateCategories(category.getSubCategories(), targetLang));
            }

            return translated;
        } catch (Exception e) {
            logger.error("Error translating category '{}': {}", category.getName(), e.getMessage());
            return category;
        }
    }

    /**
     * Helper to translate category name by slug first, then fallback to translation
     */
    private String translateCategoryName(String name, String targetLang) {
        // We try to find if this 'name' corresponds to any known category to get its
        // slug
        // This is a bit tricky if we only have the name.
        // But for most category sections, we have the slug available in the DTO if we
        // update it.
        // For now, let's try a simple mapping or just use translation as fallback.

        // Let's assume most category names in the system are the ones we localized.
        // We could look up by name in the RssService list, but it's expensive.
        // For now, let's use the translation service as fallback for names.
        return translationService.translate(name, DEFAULT_SOURCE_LANG, targetLang);
    }

    /**
     * Translate tags
     */
    private List<String> translateTags(List<String> tags, String targetLang) {
        if (tags == null || tags.isEmpty()) {
            return tags;
        }

        List<String> translated = new ArrayList<>();
        for (String tag : tags) {
            try {
                translated.add(translationService.translate(tag, DEFAULT_SOURCE_LANG, targetLang));
            } catch (Exception e) {
                logger.warn("Failed to translate tag '{}': {}", tag, e.getMessage());
                translated.add(tag);
            }
        }
        return translated;
    }
}