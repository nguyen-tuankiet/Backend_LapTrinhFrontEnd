package com.thanhnien.rss.service;

import com.thanhnien.rss.model.ArticleDetail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ArticleScraperService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleScraperService.class);
    private static final int TIMEOUT = 10000; // 10 seconds

    /**
     * Scrape full article content from URL
     */
    public ArticleDetail scrapeArticle(String articleUrl) {
        try {
            logger.info("Scraping article from: {}", articleUrl);

            Document doc = Jsoup.connect(articleUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(TIMEOUT)
                    .get();

            return ArticleDetail.builder()
                    .url(articleUrl)
                    .title(extractTitle(doc))
                    .description(extractDescription(doc))
                    .content(extractContent(doc))
                    .author(extractAuthor(doc))
                    .pubDate(extractPubDate(doc))
                    .category(extractCategory(doc))
                    .imageUrl(extractMainImage(doc))
                    .images(extractAllImages(doc))
                    .tags(extractTags(doc))
                    .build();

        } catch (Exception e) {
            logger.error("Error scraping article from {}: {}", articleUrl, e.getMessage());
            return ArticleDetail.builder()
                    .url(articleUrl)
                    .title("Error")
                    .content("Failed to scrape article: " + e.getMessage())
                    .build();
        }
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
