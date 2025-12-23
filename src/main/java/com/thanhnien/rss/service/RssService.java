package com.thanhnien.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.thanhnien.rss.model.Article;
import com.thanhnien.rss.model.Category;
import com.thanhnien.rss.model.RssFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import com.thanhnien.rss.model.HomePageData;
import com.thanhnien.rss.model.CategorySection;
import org.springframework.cache.annotation.Cacheable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

@Service
public class RssService {

        @Autowired
        @Lazy
        private RssService self;

        private static final Logger logger = LoggerFactory.getLogger(RssService.class);
        private static final String BASE_RSS_URL = "https://thanhnien.vn/rss/";
        private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        // Create thread pool with more threads for parallel fetching
        private final Executor executor = Executors.newFixedThreadPool(20);

        // Predefined categories from thanhnien.vn
        private final List<Category> categories = Arrays.asList(
                        Category.builder().name("Trang chủ").slug("home").rssUrl(BASE_RSS_URL + "home.rss").build(),
                        Category.builder().name("Thời sự").slug("thoi-su").rssUrl(BASE_RSS_URL + "thoi-su.rss").build(),
                        Category.builder().name("Chính trị").slug("chinh-tri").rssUrl(BASE_RSS_URL + "chinh-tri.rss")
                                        .build(),
                        Category.builder().name("Thế giới").slug("the-gioi").rssUrl(BASE_RSS_URL + "the-gioi.rss")
                                        .build(),
                        Category.builder().name("Kinh tế").slug("kinh-te").rssUrl(BASE_RSS_URL + "kinh-te.rss").build(),
                        Category.builder().name("Đời sống").slug("doi-song").rssUrl(BASE_RSS_URL + "doi-song.rss")
                                        .build(),
                        Category.builder().name("Sức khỏe").slug("suc-khoe").rssUrl(BASE_RSS_URL + "suc-khoe.rss")
                                        .build(),
                        Category.builder().name("Giới trẻ").slug("gioi-tre").rssUrl(BASE_RSS_URL + "gioi-tre.rss")
                                        .build(),
                        Category.builder().name("Tiêu dùng").slug("tieu-dung-thong-minh")
                                        .rssUrl(BASE_RSS_URL + "tieu-dung-thong-minh.rss").build(),
                        Category.builder().name("Giáo dục").slug("giao-duc").rssUrl(BASE_RSS_URL + "giao-duc.rss")
                                        .build(),
                        Category.builder().name("Du lịch").slug("du-lich").rssUrl(BASE_RSS_URL + "du-lich.rss").build(),
                        Category.builder().name("Văn hóa").slug("van-hoa").rssUrl(BASE_RSS_URL + "van-hoa.rss").build(),
                        Category.builder().name("Giải trí").slug("giai-tri").rssUrl(BASE_RSS_URL + "giai-tri.rss")
                                        .build(),
                        Category.builder().name("Thể thao").slug("the-thao").rssUrl(BASE_RSS_URL + "the-thao.rss")
                                        .build(),
                        Category.builder().name("Công nghệ").slug("cong-nghe").rssUrl(BASE_RSS_URL + "cong-nghe.rss")
                                        .build(),
                        Category.builder().name("Xe").slug("xe").rssUrl(BASE_RSS_URL + "xe.rss").build(),
                        Category.builder().name("Thời trang trẻ").slug("thoi-trang-tre")
                                        .rssUrl(BASE_RSS_URL + "thoi-trang-tre.rss").build(),
                        Category.builder().name("Video").slug("video").rssUrl(BASE_RSS_URL + "video.rss").build());

        public List<Category> getAllCategories() {
                return categories;
        }

        public Category findCategoryBySlug(String slug) {
                return categories.stream()
                                .filter(cat -> cat.getSlug().equals(slug))
                                .findFirst()
                                .orElse(null);
        }

        /**
         * OPTIMIZED: Fetch RSS with reduced timeout and retry
         */
        @Cacheable(value = "rssFeed", key = "#rssUrl", unless = "#result == null || #result.articles.isEmpty()")
        public RssFeed fetchRss(String rssUrl) {
                HttpURLConnection connection = null;
                try {
                        logger.debug("Fetching RSS from: {}", rssUrl);
                        URL url = java.net.URI.create(rssUrl).toURL();

                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(3000); // 3 seconds (reduced from 15s)
                        connection.setReadTimeout(5000); // 5 seconds (reduced from 30s)

                        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml");

                        int responseCode = connection.getResponseCode();
                        if (responseCode != HttpURLConnection.HTTP_OK) {
                                logger.warn("HTTP {} from {}", responseCode, rssUrl);
                                return createEmptyFeed("Error " + responseCode);
                        }

                        try (InputStream inputStream = connection.getInputStream()) {
                                SyndFeedInput input = new SyndFeedInput();
                                SyndFeed syndFeed = input.build(new XmlReader(inputStream));

                                List<Article> articles = syndFeed.getEntries().stream()
                                                .limit(10) // Limit to 10 articles per feed for faster processing
                                                .map(entry -> Article.builder()
                                                                .title(entry.getTitle())
                                                                .link(entry.getLink())
                                                                .description(cleanDescription(
                                                                                entry.getDescription() != null ? entry
                                                                                                .getDescription()
                                                                                                .getValue() : ""))
                                                                .pubDate(entry.getPublishedDate() != null
                                                                                ? dateFormat.format(entry
                                                                                                .getPublishedDate())
                                                                                : "")
                                                                .imageUrl(extractImageUrl(entry))
                                                                .videoUrl(null) // Skip video extraction for speed
                                                                .author(extractAuthor(entry))
                                                                .category(extractCategory(entry))
                                                                .build())
                                                .collect(Collectors.toList());

                                return RssFeed.builder()
                                                .title(syndFeed.getTitle())
                                                .description(syndFeed.getDescription())
                                                .link(syndFeed.getLink())
                                                .language(syndFeed.getLanguage())
                                                .articles(articles)
                                                .build();
                        }

                } catch (Exception e) {
                        logger.error("Error fetching RSS from {}: {}", rssUrl, e.getMessage());
                        return createEmptyFeed("Failed to fetch");
                } finally {
                        if (connection != null) {
                                connection.disconnect();
                        }
                }
        }

        private RssFeed createEmptyFeed(String description) {
                return RssFeed.builder()
                                .title("Error")
                                .description(description)
                                .articles(new ArrayList<>())
                                .build();
        }

        public RssFeed getHomeArticles() {
                return self.fetchRss(BASE_RSS_URL + "home.rss");
        }

        public RssFeed getArticlesByCategory(String slug) {
                Category category = findCategoryBySlug(slug);
                if (category != null) {
                        RssFeed feed = self.fetchRss(category.getRssUrl());
                        feed.getArticles().forEach(article -> article.setCategory(category.getName()));
                        return feed;
                }
                return createEmptyFeed("Category not found: " + slug);
        }

        public List<RssFeed> getAllFeeds() {
                List<RssFeed> allFeeds = new ArrayList<>();
                for (Category category : categories) {
                        RssFeed feed = self.fetchRss(category.getRssUrl());
                        feed.getArticles().forEach(article -> article.setCategory(category.getName()));
                        allFeeds.add(feed);
                }
                return allFeeds;
        }

        /**
         * HOME PAGE DATA - Fetch ALL categories like thanhnien.vn homepage
         * Structure:
         * - Featured articles (from home.rss)
         * - Trending articles (from tno.rss - Tin mới)
         * - Most read articles (from tin-24h.rss - Đọc nhiều)
         * - ALL category sections (Thời sự, Thế giới, Kinh tế, Giải trí, etc.)
         */
        @Cacheable(value = "homePageData", unless = "#result == null")
        public HomePageData getHomePageData() {
                long startTime = System.currentTimeMillis();
                logger.info("Starting home page data fetch (ALL categories)...");

                // 1. Featured articles from home.rss
                CompletableFuture<List<Article>> featuredFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                                RssFeed feed = self.fetchRss(BASE_RSS_URL + "home.rss");
                                return feed.getArticles().stream().limit(10).collect(Collectors.toList());
                        } catch (Exception e) {
                                logger.error("Featured articles error: {}", e.getMessage());
                                return new ArrayList<>();
                        }
                }, executor);

                // 2. Trending from tno.rss (Tin mới)
                CompletableFuture<List<Article>> trendingFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                                RssFeed feed = self.fetchRss(BASE_RSS_URL + "tno.rss");
                                return feed.getArticles().stream().limit(10).collect(Collectors.toList());
                        } catch (Exception e) {
                                logger.error("Trending error: {}", e.getMessage());
                                return new ArrayList<>();
                        }
                }, executor);

                // 3. Most Read from tin-24h.rss (Đọc nhiều)
                CompletableFuture<List<Article>> mostReadFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                                RssFeed feed = self.fetchRss(BASE_RSS_URL + "tin-24h.rss");
                                return feed.getArticles().stream().limit(10).collect(Collectors.toList());
                        } catch (Exception e) {
                                logger.error("Most read error: {}", e.getMessage());
                                return new ArrayList<>();
                        }
                }, executor);

                // 4. ALL Categories (excluding home) - like thanhnien.vn homepage
                List<CompletableFuture<CategorySection>> categoryFutures = categories.stream()
                                .filter(cat -> !cat.getSlug().equals("home"))
                                .map(cat -> CompletableFuture.supplyAsync(() -> {
                                        try {
                                                RssFeed feed = self.fetchRss(cat.getRssUrl());
                                                return CategorySection.builder()
                                                                .categoryName(cat.getName())
                                                                .categorySlug(cat.getSlug())
                                                                .articles(feed.getArticles().stream().limit(5)
                                                                                .collect(Collectors.toList()))
                                                                .build();
                                        } catch (Exception e) {
                                                logger.warn("Category {} error: {}", cat.getSlug(), e.getMessage());
                                                return null;
                                        }
                                }, executor))
                                .collect(Collectors.toList());

                // Wait with timeout (20 seconds for all categories)
                try {
                        CompletableFuture.allOf(
                                        featuredFuture,
                                        trendingFuture,
                                        mostReadFuture,
                                        CompletableFuture.allOf(categoryFutures.toArray(new CompletableFuture[0])))
                                        .get(20, TimeUnit.SECONDS);

                        // Build category sections
                        List<CategorySection> sections = categoryFutures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(section -> section != null && !section.getArticles().isEmpty())
                                        .collect(Collectors.toList());

                        long duration = System.currentTimeMillis() - startTime;
                        logger.info("✅ Home page data fetched in {}ms ({} categories)", duration, sections.size());

                        return HomePageData.builder()
                                        .featuredArticles(featuredFuture.get())
                                        .categorySections(sections)
                                        .trendingArticles(trendingFuture.get())
                                        .mostReadArticles(mostReadFuture.get())
                                        .build();

                } catch (Exception e) {
                        long duration = System.currentTimeMillis() - startTime;
                        logger.error("❌ Home page data error after {}ms: {}", duration, e.getMessage());

                        // Return partial data if available
                        List<CategorySection> sections = categoryFutures.stream()
                                        .filter(CompletableFuture::isDone)
                                        .map(f -> {
                                                try {
                                                        return f.get();
                                                } catch (Exception ex) {
                                                        return null;
                                                }
                                        })
                                        .filter(s -> s != null)
                                        .collect(Collectors.toList());

                        return HomePageData.builder()
                                        .featuredArticles(featuredFuture.isDone() ? featuredFuture.join()
                                                        : new ArrayList<>())
                                        .categorySections(sections)
                                        .trendingArticles(trendingFuture.isDone() ? trendingFuture.join()
                                                        : new ArrayList<>())
                                        .mostReadArticles(mostReadFuture.isDone() ? mostReadFuture.join()
                                                        : new ArrayList<>())
                                        .build();
                }
        }

        private String extractImageUrl(SyndEntry entry) {
                if (entry.getDescription() != null) {
                        String desc = entry.getDescription().getValue();
                        Pattern pattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']");
                        Matcher matcher = pattern.matcher(desc);
                        if (matcher.find()) {
                                return matcher.group(1);
                        }
                }
                if (entry.getEnclosures() != null && !entry.getEnclosures().isEmpty()) {
                        return entry.getEnclosures().get(0).getUrl();
                }
                return "";
        }

        private String cleanDescription(String description) {
                if (description == null)
                        return "";
                return description.replaceAll("<[^>]*>", "").trim();
        }

        private String extractAuthor(SyndEntry entry) {
                if (entry.getAuthor() != null && !entry.getAuthor().isEmpty()) {
                        return entry.getAuthor();
                }
                if (entry.getAuthors() != null && !entry.getAuthors().isEmpty()) {
                        return entry.getAuthors().get(0).getName();
                }
                return "";
        }

        private String extractCategory(SyndEntry entry) {
                if (entry.getCategories() != null && !entry.getCategories().isEmpty()) {
                        return entry.getCategories().get(0).getName();
                }
                return null;
        }
}
