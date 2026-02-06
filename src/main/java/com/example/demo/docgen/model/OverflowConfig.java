package com.example.demo.docgen.model;

import com.example.demo.docgen.mapper.MappingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for handling data overflow when an array of items exceeds the capacity of a form section.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverflowConfig {
    /**
     * Path to the array that may overflow
     */
    private String arrayPath;

    /**
     * Mapping type for the arrayPath (DIRECT, JSONPATH, JSONATA)
     */
    @Builder.Default
    private MappingType mappingType = MappingType.JSONPATH;
    
    /**
     * Maximum items to include in main document
     */
    private int maxItemsInMain;
    
    /**
     * Number of items per overflow/addendum page
     */
    private int itemsPerOverflowPage;
    
    /**
     * Template path for addendum pages
     */
    private String addendumTemplatePath;
    
    /**
     * Field name for overflow indicator (Strategy 4)
     */
    private String overflowIndicatorField;
}
