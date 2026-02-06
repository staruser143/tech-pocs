package com.example.demo.docgen.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to enable/disable caching based on application properties.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "docgen.templates.cache-enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig {
}
