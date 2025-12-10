package com.thanhnien.rss.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    private String title;
    private String link;
    private String description;
    private String pubDate;
    private String imageUrl;
    private String category;
    private String author;
}
