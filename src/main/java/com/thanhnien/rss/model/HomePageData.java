package com.thanhnien.rss.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomePageData {
    private List<Article> featuredArticles;
    private List<CategorySection> categorySections;
    private List<Article> trendingArticles;
    private List<Article> mostReadArticles;
}
