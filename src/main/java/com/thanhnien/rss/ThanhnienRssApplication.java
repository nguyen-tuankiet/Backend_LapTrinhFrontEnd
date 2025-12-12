package com.thanhnien.rss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ThanhnienRssApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThanhnienRssApplication.class, args);
    }
}
