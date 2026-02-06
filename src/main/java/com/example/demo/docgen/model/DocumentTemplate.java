package com.example.demo.docgen.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Document template definition with support for inheritance and composition
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTemplate {
    /**
     * Unique identifier for this template
     */
    private String templateId;

    /**
     * Optional description of the template
     */
    private String description;
    
    /**
     * Optional base template ID for inheritance
     */
    private String baseTemplateId;
    
    /**
     * Sections that make up this document
     */
    @Builder.Default
    private List<PageSection> sections = new ArrayList<>();
    
    /**
     * Section IDs to exclude from base template (for inheritance)
     */
    @Builder.Default
    private List<String> excludedSections = new ArrayList<>();
    
    /**
     * Section overrides: sectionId -> new template path
     */
    @Builder.Default
    private Map<String, String> sectionOverrides = new HashMap<>();
    
    /**
     * Section groups to include (for composition)
     */
    @Builder.Default
    private List<String> includedSectionGroups = new ArrayList<>();
    
    /**
     * Template fragments to include (for composition)
     */
    @Builder.Default
    private List<String> includedFragments = new ArrayList<>();
    
    /**
     * Header and footer configuration
     */
    private HeaderFooterConfig headerFooterConfig;
    
    /**
     * Metadata for template selection (product type, state, market, etc.)
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
