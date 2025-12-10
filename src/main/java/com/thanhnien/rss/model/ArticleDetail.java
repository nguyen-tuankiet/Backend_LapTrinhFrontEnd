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
public class ArticleDetail {
    private String title;
    private String url;
    private String description;
    private String content;
    private String author;
    private String pubDate;
    private String category;
    private String imageUrl;
    private List<String> images;
    private List<String> tags;
}
