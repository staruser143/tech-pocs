package com.example.demo.docgen.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for document headers and footers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeaderFooterConfig {
    /**
     * Header template configurations
     */
    @Builder.Default
    private List<HeaderTemplate> headers = new ArrayList<>();
    
    /**
     * Footer template configurations
     */
    @Builder.Default
    private List<FooterTemplate> footers = new ArrayList<>();
    
    /**
     * Whether to apply headers/footers to all pages
     */
    @Builder.Default
    private boolean applyToAllPages = true;
    
    /**
     * Page numbers to exclude from header/footer (0-based)
     */
    @Builder.Default
    private Set<Integer> excludePages = new HashSet<>();
}
