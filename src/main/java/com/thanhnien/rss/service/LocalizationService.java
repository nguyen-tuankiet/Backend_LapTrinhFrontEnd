package com.thanhnien.rss.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class LocalizationService {

    @Autowired
    private MessageSource messageSource;

    public String getMessage(String key, String lang) {
        Locale locale = Locale.forLanguageTag(lang);
        return messageSource.getMessage(key, null, locale);
    }

    public String getCategoryName(String slug, String lang) {
        try {
            Locale locale = Locale.forLanguageTag(lang);
            return messageSource.getMessage("category." + slug, null, locale);
        } catch (Exception e) {
            // Fallback to null if key not found
            return null;
        }
    }
}
