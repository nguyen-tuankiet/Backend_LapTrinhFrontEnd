package com.thanhnien.rss.service;

import com.thanhnien.rss.model.ArticleDetail;
import com.thanhnien.rss.model.Article;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ArticleScraperService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleScraperService.class);
    private static final int TIMEOUT = 10000; // 10 seconds

    @Autowired
    private HomePageCacheService cacheService;

    /**
     * Scrape full article content from URL.
     * Uses cache to avoid re-scraping the same article.
     */
    public ArticleDetail scrapeArticle(String articleUrl) {
        // Check cache first
        if (cacheService.hasCachedArticle(articleUrl)) {
            logger.debug("Article cache HIT: {}", articleUrl);
            return cacheService.getCachedArticle(articleUrl);
        }

        logger.debug("Article cache MISS: {}", articleUrl);

        try {
            logger.info("Scraping article from: {}", articleUrl);
            long startTime = System.currentTimeMillis();

            Document doc = Jsoup.connect(articleUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(TIMEOUT)
                    .get();

            ArticleDetail article = ArticleDetail.builder()
                    .url(articleUrl)
                    .title(extractTitle(doc))
                    .relatedNews(extractRelatedNews(doc)) // Extract first before content clean-up
                    .description(extractDescription(doc))
                    .content(extractContent(doc))
                    .author(extractAuthor(doc))
                    .pubDate(extractPubDate(doc))
                    .category(extractCategory(doc))
                    .imageUrl(extractMainImage(doc))
                    .images(extractAllImages(doc))
                    .tags(extractTags(doc))
                    .build();

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Article scraped in {}ms: {}", duration, articleUrl);

            // Save to cache
            cacheService.cacheArticle(articleUrl, article);

            return article;

        } catch (Exception e) {
            logger.error("Error scraping article from {}: {}", articleUrl, e.getMessage());
            return ArticleDetail.builder()
                    .url(articleUrl)
                    .title("Error")
                    .content("Failed to scrape article: " + e.getMessage())
                    .build();
        }
    }

    private List<com.thanhnien.rss.model.Article> extractRelatedNews(Document doc) {
        List<com.thanhnien.rss.model.Article> relatedArticles = new ArrayList<>();

        // 1. Find all potential containers
        Elements boxes = doc.select(".box-category");

        Element relatedBox = null;
        for (Element box : boxes) {
            Element titleEl = box.selectFirst(".box-category-title");
            if (titleEl != null && titleEl.text().trim().equalsIgnoreCase("Tin liên quan")) {
                relatedBox = box;
                break;
            }
        }

        // Fallback: try searching for header directly if box structure is different
        if (relatedBox == null) {
            Elements headers = doc.select("h2, h3, h4, div, span, a");
            for (Element header : headers) {
                if (header.text().trim().equalsIgnoreCase("Tin liên quan")) {
                    // Try to find a container parent
                    relatedBox = header.closest(".box-category");
                    if (relatedBox == null) {
                        // Or maybe the parent of the parent is the container
                        relatedBox = header.parent().parent();
                    }
                    if (relatedBox != null)
                        break;
                }
            }
        }

        if (relatedBox != null) {
            Elements items = relatedBox.select(".box-category-item");

            for (Element item : items) {
                try {
                    Element titleLinkEl = item.selectFirst(".box-category-link-title");
                    if (titleLinkEl == null)
                        continue;

                    String title = titleLinkEl.attr("title");
                    if (title.isEmpty()) {
                        title = titleLinkEl.text().trim();
                    }

                    String link = titleLinkEl.attr("href");
                    if (!link.startsWith("http")) {
                        link = "https://thanhnien.vn" + link;
                    }

                    String imageUrl = "";
                    Element imgEl = item.selectFirst(".box-category-avatar");
                    if (imgEl != null) {
                        imageUrl = imgEl.attr("data-src");
                        if (imageUrl.isEmpty())
                            imageUrl = imgEl.attr("src");
                    }

                    if (!title.isEmpty()) {
                        relatedArticles.add(com.thanhnien.rss.model.Article.builder()
                                .title(title)
                                .link(link)
                                .imageUrl(imageUrl)
                                .build());
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing related news item: {}", e.getMessage());
                }
            }

            // Remove related section from doc to avoid duplicating in content
            relatedBox.remove();
        }

        return relatedArticles;
    }

    private String extractTitle(Document doc) {
        // Try multiple selectors for title
        Element titleEl = doc.selectFirst("h1.detail-title");
        if (titleEl == null)
            titleEl = doc.selectFirst("h1.title");
        if (titleEl == null)
            titleEl = doc.selectFirst("h1");
        if (titleEl == null)
            titleEl = doc.selectFirst("title");
        return titleEl != null ? titleEl.text() : "";
    }

    private String extractDescription(Document doc) {
        // Try meta description first
        Element metaDesc = doc.selectFirst("meta[name=description]");
        if (metaDesc != null) {
            return metaDesc.attr("content");
        }

        // Try article description/sapo
        Element descEl = doc.selectFirst(".detail-sapo");
        if (descEl == null)
            descEl = doc.selectFirst(".sapo");
        if (descEl == null)
            descEl = doc.selectFirst(".description");
        return descEl != null ? descEl.text() : "";
    }

    private String extractContent(Document doc) {
        // Main content selectors for thanhnien.vn
        Element contentEl = doc.selectFirst(".detail-content");
        if (contentEl == null)
            contentEl = doc.selectFirst(".content-detail");
        if (contentEl == null)
            contentEl = doc.selectFirst("article");
        if (contentEl == null)
            contentEl = doc.selectFirst(".article-body");

        if (contentEl != null) {
            // Remove ads, scripts, styles
            contentEl.select("script, style, .ads, .advertisement, .related-news").remove();

            // Get text with paragraph breaks
            StringBuilder content = new StringBuilder();
            for (Element p : contentEl.select("p")) {
                String text = p.text().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n\n");
                }
            }
            return content.toString().trim();
        }
        return "";
    }

    private String extractAuthor(Document doc) {
        Element authorEl = doc.selectFirst(".detail-author");
        if (authorEl == null)
            authorEl = doc.selectFirst(".author");
        if (authorEl == null)
            authorEl = doc.selectFirst("[class*=author]");
        return authorEl != null ? authorEl.text() : "";
    }

    private String extractPubDate(Document doc) {
        Element dateEl = doc.selectFirst(".detail-time");
        if (dateEl == null)
            dateEl = doc.selectFirst(".time");
        if (dateEl == null)
            dateEl = doc.selectFirst("time");
        if (dateEl == null)
            dateEl = doc.selectFirst("[class*=date]");
        return dateEl != null ? dateEl.text() : "";
    }

    private String extractCategory(Document doc) {
        Element catEl = doc.selectFirst(".detail-cate");
        if (catEl == null)
            catEl = doc.selectFirst(".breadcrumb a");
        if (catEl == null)
            catEl = doc.selectFirst("[class*=category]");
        return catEl != null ? catEl.text() : "";
    }

    private String extractMainImage(Document doc) {
        Element imgEl = doc.selectFirst(".detail-content img");
        if (imgEl == null)
            imgEl = doc.selectFirst("article img");
        if (imgEl == null)
            imgEl = doc.selectFirst("meta[property=og:image]");

        if (imgEl != null) {
            if (imgEl.tagName().equals("meta")) {
                return imgEl.attr("content");
            }
            String src = imgEl.attr("data-src");
            if (src.isEmpty())
                src = imgEl.attr("src");
            return src;
        }
        return "";
    }

    private List<String> extractAllImages(Document doc) {
        List<String> images = new ArrayList<>();
        Elements imgElements = doc.select(".detail-content img, article img");

        for (Element img : imgElements) {
            String src = img.attr("data-src");
            if (src.isEmpty())
                src = img.attr("src");
            if (!src.isEmpty() && !src.contains("icon") && !src.contains("logo")) {
                images.add(src);
            }
        }
        return images;
    }

    private List<String> extractTags(Document doc) {
        Elements tagElements = doc.select(".detail-tags a, .tags a, [class*=tag] a");
        return tagElements.stream()
                .map(Element::text)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.toList());
    }
}
