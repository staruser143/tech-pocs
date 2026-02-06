package com.example.demo.docgen.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Configuration for repeating a set of field mappings for each item in a collection.
 * Useful for mapping arrays of data (like children) to numbered PDF fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepeatingGroupConfig {
    /**
     * Prefix for the PDF field name (e.g., "child" for "child1FirstName")
     */
    private String prefix;
    
    /**
     * Suffix for the PDF field name (optional)
     */
    private String suffix;
    
    /**
     * Starting index for the PDF field name (defaults to 1)
     */
    @Builder.Default
    private int startIndex = 1;
    
    /**
     * Separator between the field name and the index (e.g., "." or "_")
     */
    private String indexSeparator;
    
    /**
     * Position of the index relative to the field name
     */
    @Builder.Default
    private IndexPosition indexPosition = IndexPosition.BEFORE_FIELD;
    
    /**
     * Maximum number of items to map (optional)
     */
    private Integer maxItems;
    
    /**
     * Field mappings for a single item.
     * The PDF field name construction depends on indexPosition:
     * - BEFORE_FIELD: prefix + index + indexSeparator + fieldKey + suffix
     * - AFTER_FIELD: prefix + fieldKey + indexSeparator + index + suffix
     */
    private Map<String, String> fields;

    public enum IndexPosition {
        BEFORE_FIELD,
        AFTER_FIELD
    }
}
