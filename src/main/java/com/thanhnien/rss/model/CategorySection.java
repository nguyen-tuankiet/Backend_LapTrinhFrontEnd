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
public class CategorySection {
    private String categoryName;
    private String categorySlug;
    private List<Article> articles;
}
