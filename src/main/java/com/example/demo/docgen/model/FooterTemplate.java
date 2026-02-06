package com.example.demo.docgen.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Footer template configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FooterTemplate {
    /**
     * Render type for the footer
     */
    private RenderType renderType;
    
    /**
     * Content: simple text or template path depending on renderType
     */
    private String content;
    
    /**
     * Alignment: LEFT, CENTER, RIGHT
     */
    @Builder.Default
    private String alignment = "CENTER";
    
    /**
     * Bottom margin in points
     */
    @Builder.Default
    private float marginBottom = 50f;
    
    /**
     * Whether to include page numbers
     */
    @Builder.Default
    private boolean includePageNumbers = true;
    
    /**
     * Page number format (e.g., "Page {page} of {total}")
     */
    @Builder.Default
    private String pageNumberFormat = "Page {page} of {total}";
    
    /**
     * Data for FreeMarker templates
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
}
