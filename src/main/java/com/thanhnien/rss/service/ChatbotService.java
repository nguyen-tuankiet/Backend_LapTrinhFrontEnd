package com.thanhnien.rss.service;

import com.thanhnien.rss.model.Article;
import com.thanhnien.rss.model.Category;
import com.thanhnien.rss.model.ChatResponse;
import com.thanhnien.rss.model.RssFeed;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatbotService {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotService.class);

    @Autowired
    private RssService rssService;

    @Autowired
    private OpenAIService openAIService;

    // Keyword to category mapping
    private static final Map<String, String> KEYWORD_TO_CATEGORY = new LinkedHashMap<>();

    // Stop words - các từ không nên dùng làm search keyword
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "tin", "tức", "hôm", "nay", "có", "gì", "về", "của", "và", "là", "cho",
            "trong", "ngày", "mới", "nhất", "bản", "đọc", "xem", "tìm", "kiếm",
            "hỏi", "nào", "những", "các", "được", "với", "này", "đó", "kể", "từ"));

    // Greeting keywords
    private static final Set<String> GREETING_KEYWORDS = new HashSet<>(Arrays.asList(
            "hello", "hi", "xin chào", "chào", "chào bạn", "chào bot", "tạm biệt", "bye"));

    static {
        // Thời sự
        KEYWORD_TO_CATEGORY.put("thời sự", "thoi-su");
        KEYWORD_TO_CATEGORY.put("thoi su", "thoi-su");
        KEYWORD_TO_CATEGORY.put("tin tức", "thoi-su");
        KEYWORD_TO_CATEGORY.put("sự kiện", "thoi-su");
        KEYWORD_TO_CATEGORY.put("pháp luật", "thoi-su");

        // Công nghệ
        KEYWORD_TO_CATEGORY.put("công nghệ", "cong-nghe");
        KEYWORD_TO_CATEGORY.put("cong nghe", "cong-nghe");
        KEYWORD_TO_CATEGORY.put("tech", "cong-nghe");
        KEYWORD_TO_CATEGORY.put("game", "cong-nghe");
        KEYWORD_TO_CATEGORY.put("smartphone", "cong-nghe");
        KEYWORD_TO_CATEGORY.put("điện thoại", "cong-nghe");
        KEYWORD_TO_CATEGORY.put("iphone", "cong-nghe");
        KEYWORD_TO_CATEGORY.put("samsung", "cong-nghe");
        KEYWORD_TO_CATEGORY.put("apple", "cong-nghe");
        KEYWORD_TO_CATEGORY.put("laptop", "cong-nghe");

        // Thể thao
        KEYWORD_TO_CATEGORY.put("thể thao", "the-thao");
        KEYWORD_TO_CATEGORY.put("the thao", "the-thao");
        KEYWORD_TO_CATEGORY.put("bóng đá", "the-thao");
        KEYWORD_TO_CATEGORY.put("bong da", "the-thao");
        KEYWORD_TO_CATEGORY.put("football", "the-thao");
        KEYWORD_TO_CATEGORY.put("world cup", "the-thao");

        // Kinh tế
        KEYWORD_TO_CATEGORY.put("kinh tế", "kinh-te");
        KEYWORD_TO_CATEGORY.put("kinh te", "kinh-te");
        KEYWORD_TO_CATEGORY.put("tài chính", "kinh-te");
        KEYWORD_TO_CATEGORY.put("chứng khoán", "kinh-te");
        KEYWORD_TO_CATEGORY.put("ngân hàng", "kinh-te");
        KEYWORD_TO_CATEGORY.put("bất động sản", "kinh-te");

        // Giải trí
        KEYWORD_TO_CATEGORY.put("giải trí", "giai-tri");
        KEYWORD_TO_CATEGORY.put("giai tri", "giai-tri");
        KEYWORD_TO_CATEGORY.put("phim", "giai-tri");
        KEYWORD_TO_CATEGORY.put("nghệ sĩ", "giai-tri");
        KEYWORD_TO_CATEGORY.put("ca sĩ", "giai-tri");
        KEYWORD_TO_CATEGORY.put("showbiz", "giai-tri");

        // Giáo dục
        KEYWORD_TO_CATEGORY.put("giáo dục", "giao-duc");
        KEYWORD_TO_CATEGORY.put("giao duc", "giao-duc");
        KEYWORD_TO_CATEGORY.put("tuyển sinh", "giao-duc");
        KEYWORD_TO_CATEGORY.put("đại học", "giao-duc");
        KEYWORD_TO_CATEGORY.put("học sinh", "giao-duc");
        KEYWORD_TO_CATEGORY.put("sinh viên", "giao-duc");

        // Du lịch
        KEYWORD_TO_CATEGORY.put("du lịch", "du-lich");
        KEYWORD_TO_CATEGORY.put("du lich", "du-lich");
        KEYWORD_TO_CATEGORY.put("travel", "du-lich");
        KEYWORD_TO_CATEGORY.put("nghỉ dưỡng", "du-lich");

        // Sức khỏe
        KEYWORD_TO_CATEGORY.put("sức khỏe", "suc-khoe");
        KEYWORD_TO_CATEGORY.put("suc khoe", "suc-khoe");
        KEYWORD_TO_CATEGORY.put("y tế", "suc-khoe");
        KEYWORD_TO_CATEGORY.put("bệnh viện", "suc-khoe");
        KEYWORD_TO_CATEGORY.put("thuốc", "suc-khoe");

        // Thế giới
        KEYWORD_TO_CATEGORY.put("thế giới", "the-gioi");
        KEYWORD_TO_CATEGORY.put("the gioi", "the-gioi");
        KEYWORD_TO_CATEGORY.put("quốc tế", "the-gioi");
        KEYWORD_TO_CATEGORY.put("world", "the-gioi");

        // Đời sống
        KEYWORD_TO_CATEGORY.put("đời sống", "doi-song");
        KEYWORD_TO_CATEGORY.put("doi song", "doi-song");
        KEYWORD_TO_CATEGORY.put("gia đình", "doi-song");

        // Xe
        KEYWORD_TO_CATEGORY.put("xe", "xe");
        KEYWORD_TO_CATEGORY.put("ô tô", "xe");
        KEYWORD_TO_CATEGORY.put("xe máy", "xe");
        KEYWORD_TO_CATEGORY.put("xe điện", "xe");

        // Chính trị
        KEYWORD_TO_CATEGORY.put("chính trị", "chinh-tri");
        KEYWORD_TO_CATEGORY.put("chinh tri", "chinh-tri");
        KEYWORD_TO_CATEGORY.put("đảng", "chinh-tri");
        KEYWORD_TO_CATEGORY.put("nhà nước", "chinh-tri");
        KEYWORD_TO_CATEGORY.put("chính phủ", "chinh-tri");

        // Tiêu dùng
        KEYWORD_TO_CATEGORY.put("tiêu dùng", "tieu-dung");
        KEYWORD_TO_CATEGORY.put("tieu dung", "tieu-dung");
        KEYWORD_TO_CATEGORY.put("thị trường", "tieu-dung");
        KEYWORD_TO_CATEGORY.put("mua sắm", "tieu-dung");
        KEYWORD_TO_CATEGORY.put("giá cả", "tieu-dung");

        // Văn hóa
        KEYWORD_TO_CATEGORY.put("văn hóa", "van-hoa");
        KEYWORD_TO_CATEGORY.put("van hoa", "van-hoa");
        KEYWORD_TO_CATEGORY.put("di sản", "van-hoa");
        KEYWORD_TO_CATEGORY.put("lễ hội", "van-hoa");
    }

    /**
     * Process chat message and return response
     */
    /**
     * Process chat message and return response
     */
    public ChatResponse processMessage(String message, jakarta.servlet.http.HttpSession session) {
        logger.info("Processing chat message: {}", message);

        // Check for navigation intent (e.g., "mở tin số 1", "xem tin 2", "read article
        // 3")
        Integer articleIndex = extractNavigationIndex(message);
        if (articleIndex != null) {
            return processNavigation(articleIndex, session);
        }

        // Check for greeting
        if (isGreeting(message)) {
            return ChatResponse.builder()
                    .message(
                            "Xin chào! Tôi có thể giúp gì cho bạn? Bạn có thể hỏi về tin tức thời sự, thể thao, công nghệ...")
                    .articleCount(0)
                    .articles(Collections.emptyList())
                    .build();
        }

        // Extract search keywords from message
        List<String> searchKeywords = extractSearchKeywords(message);
        logger.info("Extracted search keywords: {}", searchKeywords);

        // Detect category from message
        String categorySlug = detectCategory(message);

        // Only fetch category if we actually detected one
        Category category = null;
        if (categorySlug != null) {
            category = rssService.findCategoryBySlug(categorySlug);
        }

        List<Article> matchedArticles;
        String searchContext;

        if (!searchKeywords.isEmpty()) {
            // Search across multiple categories if we have specific keywords
            matchedArticles = searchArticlesAcrossCategories(searchKeywords);
            searchContext = String.join(", ", searchKeywords);

            // If keywords found nothing, but we have a category, look in that category
            if (matchedArticles.isEmpty() && category != null) {
                // Fallback: get today's news from detected category
                RssFeed feed = rssService.getArticlesByCategory(categorySlug, 30);
                matchedArticles = filterTodayArticles(feed.getArticles());
                searchContext = category.getName();
            }
        } else if (category != null) {
            // No specific keywords, get category news
            RssFeed feed = rssService.getArticlesByCategory(categorySlug, 30);
            matchedArticles = filterTodayArticles(feed.getArticles());
            searchContext = category.getName();
        } else {
            // Neither keywords nor category found -> Help message
            return ChatResponse.builder()
                    .message("Xin lỗi, tôi không hiểu bạn đang hỏi về mục tin nào.\n" +
                            "Bạn có thể hỏi về các chủ đề như: thời sự, công nghệ, thể thao, kinh tế, giải trí...\n" +
                            "Hoặc hỏi cụ thể như: 'tin về iPhone 16', 'kết quả bóng đá'...")
                    .articleCount(0)
                    .articles(Collections.emptyList())
                    .build();
        }

        // Decode HTML entities in articles
        matchedArticles = decodeArticles(matchedArticles);

        if (matchedArticles.isEmpty()) {
            return ChatResponse.builder()
                    .message(String.format("Không tìm thấy tin tức về '%s' trong ngày hôm nay.", searchContext))
                    .category(category != null ? category.getName() : null)
                    .articleCount(0)
                    .articles(Collections.emptyList())
                    .build();
        }

        // Save matched articles to session for later navigation
        session.setAttribute("LAST_ARTICLES", matchedArticles);

        // Generate summary with OpenAI
        String summary = generateSummary(searchContext, matchedArticles);

        return ChatResponse.builder()
                .message(summary)
                .category(category != null ? category.getName() : searchContext)
                .articleCount(matchedArticles.size())
                .articles(matchedArticles)
                .build();
    }

    /**
     * Extract article index from navigation command
     */
    private Integer extractNavigationIndex(String message) {
        String lowerMessage = message.toLowerCase().trim();
        // Regex for patterns like: "mở tin 1", "xem tin số 2", "open article 3", "tin
        // 4"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:mở|xem|đọc|chi tiết|open|read|view)?\\s*(?:tin|bài|báo|số|article|news)?\\s*(\\d+)",
                java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);
        java.util.regex.Matcher m = p.matcher(lowerMessage);

        if (m.find()) {
            // Check if it's likely a navigation command and not just a number in text
            // If the message is short or starts with a verb, it's likely a command
            if (lowerMessage.length() < 20 ||
                    lowerMessage.startsWith("mở") || lowerMessage.startsWith("xem") ||
                    lowerMessage.startsWith("đọc") || lowerMessage.startsWith("chi tiết")) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Process navigation request
     */
    private ChatResponse processNavigation(int index, jakarta.servlet.http.HttpSession session) {
        @SuppressWarnings("unchecked")
        List<Article> lastArticles = (List<Article>) session.getAttribute("LAST_ARTICLES");

        if (lastArticles == null || lastArticles.isEmpty()) {
            return ChatResponse.builder()
                    .message("Tôi không nhớ danh sách tin vừa rồi. Bạn vui lòng hỏi lại tin tức trước nhé.")
                    .articleCount(0)
                    .articles(Collections.emptyList())
                    .build();
        }

        if (index < 1 || index > lastArticles.size()) {
            return ChatResponse.builder()
                    .message(String.format("Vui lòng chọn số thứ tự từ 1 đến %d.", lastArticles.size()))
                    .articleCount(0)
                    .articles(Collections.emptyList())
                    .build();
        }

        // Get the article (index is 1-based from user)
        Article article = lastArticles.get(index - 1);

        return ChatResponse.builder()
                .message(String.format("Đang mở tin: %s", article.getTitle()))
                .navigationUrl(article.getLink())
                .articleCount(0)
                .articles(Collections.emptyList())
                .build();
    }

    /**
     * Check if message is a greeting
     */
    private boolean isGreeting(String message) {
        String lowerMessage = message.toLowerCase().trim();
        return GREETING_KEYWORDS.stream().anyMatch(lowerMessage::contains);
    }

    /**
     * Extract search keywords from user message
     */
    private List<String> extractSearchKeywords(String message) {
        String lowerMessage = message.toLowerCase()
                .replaceAll("[?!.,]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        List<String> keywords = new ArrayList<>();

        // Check for specific product/entity names (case insensitive patterns)
        String[] specificPatterns = {
                "iphone \\d+", "iphone\\d+", "samsung s\\d+", "galaxy s\\d+",
                "macbook", "ipad", "airpods", "pixel \\d+",
                "world cup", "sea games", "euro \\d+",
                "covid", "corona"
        };

        for (String pattern : specificPatterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(lowerMessage);
            while (m.find()) {
                keywords.add(m.group().trim());
            }
        }

        // If no specific patterns found, extract meaningful words
        if (keywords.isEmpty()) {
            String[] words = lowerMessage.split("\\s+");
            for (String word : words) {
                // Skip stop words and short words
                if (word.length() > 2 && !STOP_WORDS.contains(word) && !isCategory(word)) {
                    // Check if it looks like a product/brand name (has numbers or capitals in
                    // original)
                    if (word.matches(".*\\d+.*")
                            || message.contains(word.substring(0, 1).toUpperCase() + word.substring(1))) {
                        keywords.add(word);
                    }
                }
            }
        }

        return keywords;
    }

    /**
     * Check if a word is a category keyword
     */
    private boolean isCategory(String word) {
        return KEYWORD_TO_CATEGORY.containsKey(word);
    }

    /**
     * Search articles across multiple categories
     */
    private List<Article> searchArticlesAcrossCategories(List<String> keywords) {
        List<Article> allArticles = new ArrayList<>();

        // Search in main categories
        String[] categoriesToSearch = { "cong-nghe", "thoi-su", "the-gioi", "kinh-te", "the-thao", "chinh-tri",
                "tieu-dung", "van-hoa" };

        for (String cat : categoriesToSearch) {
            try {
                RssFeed feed = rssService.getArticlesByCategory(cat, 50);
                if (feed != null && feed.getArticles() != null) {
                    allArticles.addAll(feed.getArticles());
                }
            } catch (Exception e) {
                logger.warn("Error fetching category {}: {}", cat, e.getMessage());
            }
        }

        // Filter articles that match keywords
        List<Article> matchedArticles = allArticles.stream()
                .filter(article -> matchesKeywords(article, keywords))
                .collect(Collectors.toList());

        // Filter for today only
        return filterTodayArticles(matchedArticles);
    }

    /**
     * Check if article matches any of the keywords
     */
    private boolean matchesKeywords(Article article, List<String> keywords) {
        String title = decodeHtmlEntities(article.getTitle()).toLowerCase();
        String description = decodeHtmlEntities(article.getDescription()).toLowerCase();

        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();
            if (title.contains(lowerKeyword) || description.contains(lowerKeyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decode HTML entities in articles
     */
    private List<Article> decodeArticles(List<Article> articles) {
        return articles.stream()
                .map(article -> Article.builder()
                        .title(decodeHtmlEntities(article.getTitle()))
                        .description(decodeHtmlEntities(article.getDescription()))
                        .link(article.getLink())
                        .pubDate(article.getPubDate())
                        .imageUrl(article.getImageUrl())
                        .videoUrl(article.getVideoUrl())
                        .category(article.getCategory())
                        .author(article.getAuthor())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Decode HTML entities using Jsoup
     */
    private String decodeHtmlEntities(String text) {
        if (text == null)
            return "";
        return Jsoup.parse(text).text();
    }

    /**
     * Get quick summary for a category (no AI, just formatted list)
     */
    public ChatResponse getQuickSummary(String categorySlug) {
        Category category = rssService.findCategoryBySlug(categorySlug);

        if (category == null) {
            return ChatResponse.builder()
                    .message("Không tìm thấy category: " + categorySlug)
                    .articleCount(0)
                    .articles(Collections.emptyList())
                    .build();
        }

        RssFeed feed = rssService.getArticlesByCategory(categorySlug, 20);
        List<Article> todayArticles = filterTodayArticles(feed.getArticles());
        todayArticles = decodeArticles(todayArticles);

        if (todayArticles.isEmpty()) {
            return ChatResponse.builder()
                    .message(String.format("Chưa có tin %s mới trong ngày hôm nay.", category.getName()))
                    .category(category.getName())
                    .articleCount(0)
                    .articles(Collections.emptyList())
                    .build();
        }

        // Simple formatted list
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📰 **Tin %s hôm nay** (%d tin):\n\n",
                category.getName(), todayArticles.size()));

        for (int i = 0; i < todayArticles.size(); i++) {
            Article article = todayArticles.get(i);
            sb.append(String.format("%d. **%s**\n   %s\n\n",
                    i + 1, article.getTitle(),
                    truncateDescription(article.getDescription(), 100)));
        }

        return ChatResponse.builder()
                .message(sb.toString())
                .category(category.getName())
                .articleCount(todayArticles.size())
                .articles(todayArticles)
                .build();
    }

    /**
     * Detect category from user message
     */
    private String detectCategory(String message) {
        String lowerMessage = message.toLowerCase();

        for (Map.Entry<String, String> entry : KEYWORD_TO_CATEGORY.entrySet()) {
            if (lowerMessage.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Default to tech if message contains tech-related keywords
        if (lowerMessage.matches(".*(iphone|samsung|apple|google|microsoft|laptop|điện thoại).*")) {
            return "cong-nghe";
        }

        // Return null if no category detected (don't default to thoi-su anymore)
        return null;
    }

    /**
     * Filter articles published today
     */
    private List<Article> filterTodayArticles(List<Article> articles) {
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        return articles.stream()
                .filter(article -> article.getPubDate() != null &&
                        article.getPubDate().startsWith(todayStr))
                .collect(Collectors.toList());
    }

    /**
     * Generate AI summary using OpenAI
     */
    private String generateSummary(String context, List<Article> articles) {
        if (!openAIService.isConfigured()) {
            // Fallback to simple list if OpenAI not configured
            return generateSimpleSummary(context, articles);
        }

        // Prepare articles text for OpenAI
        StringBuilder articlesText = new StringBuilder();
        for (int i = 0; i < Math.min(articles.size(), 10); i++) {
            Article article = articles.get(i);
            articlesText.append(String.format("%d. Tiêu đề: %s\n   Mô tả: %s\n\n",
                    i + 1, article.getTitle(), article.getDescription()));
        }

        String aiSummary = openAIService.summarizeNews(context, articlesText.toString());

        if (aiSummary != null) {
            return aiSummary;
        }

        // Fallback if AI fails
        return generateSimpleSummary(context, articles);
    }

    /**
     * Generate simple summary without AI
     */
    private String generateSimpleSummary(String context, List<Article> articles) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📰 **Tin về %s hôm nay** (%d tin):\n\n", context, articles.size()));

        for (int i = 0; i < Math.min(articles.size(), 10); i++) {
            Article article = articles.get(i);
            sb.append(String.format("%d. %s\n", i + 1, article.getTitle()));
        }

        if (articles.size() > 10) {
            sb.append(String.format("\n...và %d tin khác", articles.size() - 10));
        }

        return sb.toString();
    }

    /**
     * Truncate description to specified length
     */
    private String truncateDescription(String description, int maxLength) {
        if (description == null || description.length() <= maxLength) {
            return description != null ? description : "";
        }
        return description.substring(0, maxLength) + "...";
    }
}
