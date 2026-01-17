package com.thanhnien.rss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class ThanhnienRssApplication {

    public static void main(String[] args) {
        // Load .env file
        try {
            System.out.println("--- Attempting to load .env file ---");
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            dotenv.entries().forEach(entry -> {
                System.out.println("Loaded env key: " + entry.getKey());
                System.setProperty(entry.getKey(), entry.getValue());
            });
            System.out.println("--- .env file loaded successfully ---");
        } catch (Exception e) {
            System.err.println("--- Error loading .env file: " + e.getMessage() + " ---");
            e.printStackTrace();
        }
        SpringApplication.run(ThanhnienRssApplication.class, args);
    }
}
