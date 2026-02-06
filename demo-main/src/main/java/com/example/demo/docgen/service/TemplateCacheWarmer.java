package com.example.demo.docgen.service;

import com.example.demo.docgen.model.DocumentTemplate;
import com.example.demo.docgen.model.PageSection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Warms the template cache at startup to avoid latency on the first request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateCacheWarmer {

    private final TemplateLoader templateLoader;

    @Value("${docgen.templates.preload-ids:}")
    private List<String> preloadTemplateIds;

    @Value("${docgen.templates.cache-enabled:true}")
    private boolean cacheEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void warmCache() {
        if (!cacheEnabled || preloadTemplateIds == null || preloadTemplateIds.isEmpty()) {
            log.info("Template cache warming skipped (disabled or no IDs configured)");
            return;
        }

        log.info("Starting template cache warming for: {}", preloadTemplateIds);
        long startTime = System.currentTimeMillis();

        for (String templateId : preloadTemplateIds) {
            try {
                // 1. Pre-load and cache the YAML definition (including inheritance/fragments)
                DocumentTemplate template = templateLoader.loadTemplate(templateId);
                log.info("Warmed YAML definition for: {}", templateId);

                // 2. Pre-load and cache the raw resources (PDFs and FTLs) for each section
                if (template.getSections() != null) {
                    for (PageSection section : template.getSections()) {
                        if (section.getTemplatePath() != null && !section.getTemplatePath().isEmpty()) {
                            try {
                                templateLoader.getResourceBytes(section.getTemplatePath());
                                log.info("  Warmed resource: {}", section.getTemplatePath());
                            } catch (Exception e) {
                                log.warn("  Failed to warm resource: {} - {}", section.getTemplatePath(), e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to warm template: {}", templateId, e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Template cache warming completed in {}ms", duration);
    }
}
