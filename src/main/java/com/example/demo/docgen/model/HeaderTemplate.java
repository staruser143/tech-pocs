package com.example.demo.docgen.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Header template configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeaderTemplate {
    /**
     * Render type for the header
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
     * Top margin in points
     */
    @Builder.Default
    private float marginTop = 50f;
    
    /**
     * Data for FreeMarker templates
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
}
