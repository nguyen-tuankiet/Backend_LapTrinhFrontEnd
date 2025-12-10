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

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RssService {

        private static final Logger logger = LoggerFactory.getLogger(RssService.class);
        private static final String BASE_RSS_URL = "https://thanhnien.vn/rss/";
        private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        // Predefined categories from thanhnien.vn
        private final List<Category> categories = Arrays.asList(
                        Category.builder()
                                        .name("Trang chủ")
                                        .slug("home")
                                        .rssUrl(BASE_RSS_URL + "home.rss")
                                        .build(),
                        Category.builder()
                                        .name("Thời sự")
                                        .slug("thoi-su")
                                        .rssUrl(BASE_RSS_URL + "thoi-su.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Pháp luật").slug("phap-luat")
                                                                        .rssUrl(BASE_RSS_URL + "thoi-su/phap-luat.rss")
                                                                        .build(),
                                                        Category.builder().name("Dân sinh").slug("dan-sinh")
                                                                        .rssUrl(BASE_RSS_URL + "thoi-su/dan-sinh.rss")
                                                                        .build(),
                                                        Category.builder().name("Quốc phòng").slug("quoc-phong")
                                                                        .rssUrl(BASE_RSS_URL + "thoi-su/quoc-phong.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Chính trị")
                                        .slug("chinh-tri")
                                        .rssUrl(BASE_RSS_URL + "chinh-tri.rss")
                                        .build(),
                        Category.builder()
                                        .name("Thế giới")
                                        .slug("the-gioi")
                                        .rssUrl(BASE_RSS_URL + "the-gioi.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Quân sự").slug("quan-su")
                                                                        .rssUrl(BASE_RSS_URL + "the-gioi/quan-su.rss")
                                                                        .build(),
                                                        Category.builder().name("Hồ sơ").slug("ho-so")
                                                                        .rssUrl(BASE_RSS_URL + "the-gioi/ho-so.rss")
                                                                        .build(),
                                                        Category.builder().name("Chuyện lạ").slug("chuyen-la")
                                                                        .rssUrl(BASE_RSS_URL + "the-gioi/chuyen-la.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Kinh tế")
                                        .slug("kinh-te")
                                        .rssUrl(BASE_RSS_URL + "kinh-te.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Ngân hàng").slug("ngan-hang")
                                                                        .rssUrl(BASE_RSS_URL + "kinh-te/ngan-hang.rss")
                                                                        .build(),
                                                        Category.builder().name("Chứng khoán").slug("chung-khoan")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "kinh-te/chung-khoan.rss")
                                                                        .build(),
                                                        Category.builder().name("Địa ốc").slug("dia-oc")
                                                                        .rssUrl(BASE_RSS_URL + "kinh-te/dia-oc.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Đời sống")
                                        .slug("doi-song")
                                        .rssUrl(BASE_RSS_URL + "doi-song.rss")
                                        .build(),
                        Category.builder()
                                        .name("Giáo dục")
                                        .slug("giao-duc")
                                        .rssUrl(BASE_RSS_URL + "giao-duc.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Tuyển sinh").slug("tuyen-sinh")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "giao-duc/tuyen-sinh.rss")
                                                                        .build(),
                                                        Category.builder().name("Du học").slug("du-hoc")
                                                                        .rssUrl(BASE_RSS_URL + "giao-duc/du-hoc.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Giải trí")
                                        .slug("giai-tri")
                                        .rssUrl(BASE_RSS_URL + "giai-tri.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Phim").slug("phim")
                                                                        .rssUrl(BASE_RSS_URL + "giai-tri/phim.rss")
                                                                        .build(),
                                                        Category.builder().name("Âm nhạc").slug("am-nhac")
                                                                        .rssUrl(BASE_RSS_URL + "giai-tri/am-nhac.rss")
                                                                        .build(),
                                                        Category.builder().name("Thời trang").slug("thoi-trang")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "giai-tri/thoi-trang.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Thể thao")
                                        .slug("the-thao")
                                        .rssUrl(BASE_RSS_URL + "the-thao.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Bóng đá Việt Nam")
                                                                        .slug("bong-da-viet-nam")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-thao/bong-da-viet-nam.rss")
                                                                        .build(),
                                                        Category.builder().name("Bóng đá quốc tế")
                                                                        .slug("bong-da-quoc-te")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-thao/bong-da-quoc-te.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Công nghệ")
                                        .slug("cong-nghe")
                                        .rssUrl(BASE_RSS_URL + "cong-nghe.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Sản phẩm").slug("san-pham")
                                                                        .rssUrl(BASE_RSS_URL + "cong-nghe/san-pham.rss")
                                                                        .build(),
                                                        Category.builder().name("Thủ thuật").slug("thu-thuat")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "cong-nghe/thu-thuat.rss")
                                                                        .build(),
                                                        Category.builder().name("Games").slug("games")
                                                                        .rssUrl(BASE_RSS_URL + "cong-nghe/games.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Sức khỏe")
                                        .slug("suc-khoe")
                                        .rssUrl(BASE_RSS_URL + "suc-khoe.rss")
                                        .build(),
                        Category.builder()
                                        .name("Xe")
                                        .slug("xe")
                                        .rssUrl(BASE_RSS_URL + "xe.rss")
                                        .build());

        /**
         * Get all available categories
         */
        public List<Category> getAllCategories() {
                return categories;
        }

        /**
         * Find category by slug
         */
        public Category findCategoryBySlug(String slug) {
                for (Category category : categories) {
                        if (category.getSlug().equals(slug)) {
                                return category;
                        }
                        if (category.getSubCategories() != null) {
                                for (Category sub : category.getSubCategories()) {
                                        if (sub.getSlug().equals(slug)) {
                                                return sub;
                                        }
                                }
                        }
                }
                return null;
        }

        /**
         * Fetch RSS feed from URL
         */
        public RssFeed fetchRss(String rssUrl) {
                try {
                        logger.info("Fetching RSS from: {}", rssUrl);
                        URL url = java.net.URI.create(rssUrl).toURL();
                        SyndFeedInput input = new SyndFeedInput();
                        SyndFeed syndFeed = input.build(new XmlReader(url));

                        List<Article> articles = new ArrayList<>();
                        for (SyndEntry entry : syndFeed.getEntries()) {
                                Article article = Article.builder()
                                                .title(entry.getTitle())
                                                .link(entry.getLink())
                                                .description(cleanDescription(
                                                                entry.getDescription() != null
                                                                                ? entry.getDescription().getValue()
                                                                                : ""))
                                                .pubDate(entry.getPublishedDate() != null
                                                                ? dateFormat.format(entry.getPublishedDate())
                                                                : "")
                                                .imageUrl(extractImageUrl(entry))
                                                .author(entry.getAuthor())
                                                .build();
                                articles.add(article);
                        }

                        return RssFeed.builder()
                                        .title(syndFeed.getTitle())
                                        .description(syndFeed.getDescription())
                                        .link(syndFeed.getLink())
                                        .language(syndFeed.getLanguage())
                                        .articles(articles)
                                        .build();

                } catch (Exception e) {
                        logger.error("Error fetching RSS from {}: {}", rssUrl, e.getMessage());
                        return RssFeed.builder()
                                        .title("Error")
                                        .description("Failed to fetch RSS: " + e.getMessage())
                                        .articles(new ArrayList<>())
                                        .build();
                }
        }

        /**
         * Get home page articles
         */
        public RssFeed getHomeArticles() {
                return fetchRss(BASE_RSS_URL + "home.rss");
        }

        /**
         * Get articles by category slug
         */
        public RssFeed getArticlesByCategory(String slug) {
                Category category = findCategoryBySlug(slug);
                if (category != null) {
                        RssFeed feed = fetchRss(category.getRssUrl());
                        feed.getArticles().forEach(article -> article.setCategory(category.getName()));
                        return feed;
                }
                return RssFeed.builder()
                                .title("Not Found")
                                .description("Category not found: " + slug)
                                .articles(new ArrayList<>())
                                .build();
        }

        /**
         * Get all articles from all categories
         */
        public List<RssFeed> getAllFeeds() {
                List<RssFeed> allFeeds = new ArrayList<>();
                for (Category category : categories) {
                        RssFeed feed = fetchRss(category.getRssUrl());
                        feed.getArticles().forEach(article -> article.setCategory(category.getName()));
                        allFeeds.add(feed);
                }
                return allFeeds;
        }

        /**
         * Extract image URL from RSS entry
         */
        private String extractImageUrl(SyndEntry entry) {
                // Try to extract from description
                if (entry.getDescription() != null) {
                        String desc = entry.getDescription().getValue();
                        Pattern pattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']");
                        Matcher matcher = pattern.matcher(desc);
                        if (matcher.find()) {
                                return matcher.group(1);
                        }
                }

                // Try to extract from enclosures
                if (entry.getEnclosures() != null && !entry.getEnclosures().isEmpty()) {
                        return entry.getEnclosures().get(0).getUrl();
                }

                return "";
        }

        /**
         * Clean HTML from description
         */
        private String cleanDescription(String description) {
                if (description == null)
                        return "";
                // Remove HTML tags but keep text
                return description.replaceAll("<[^>]*>", "").trim();
        }
}
