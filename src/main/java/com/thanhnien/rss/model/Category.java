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
public class Category {
    private String name;
    private String slug;
    private String rssUrl;
    private List<Category> subCategories;
}
