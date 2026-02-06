package com.example.demo.docgen.model;

import com.example.demo.docgen.mapper.MappingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a group of field mappings using a specific mapping strategy.
 * Allows multiple mapping strategies within a single section.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMappingGroup {
    /**
     * The mapping strategy to use for this group of fields
     */
    private MappingType mappingType;
    
    /**
     * Optional base path to evaluate ONCE and use as context for all field mappings.
     * This optimizes performance when multiple fields need to access the same filtered/nested object.
     * 
     * Example:
     * - basePath: "applicants[type='PRIMARY']"
     * - fields.firstName: "demographic.firstName" (relative to basePath result)
     * 
     * Without basePath, you would write:
     * - fields.firstName: "applicants[type='PRIMARY'].demographic.firstName" (filter runs every time)
     */
    private String basePath;
    
    /**
     * Field mappings for this group
     * Key: PDF form field name
     * Value: Source data path/expression (interpretation depends on mappingType)
     * 
     * If basePath is specified:
     * - Paths are relative to the basePath result
     * - basePath is evaluated ONCE, then all field paths are applied to that result
     * 
     * If basePath is null:
     * - Paths are absolute from the root data object
     */
    @Builder.Default
    private Map<String, String> fields = new HashMap<>();

    /**
     * Optional configuration for repeating a set of fields for each item in a collection.
     * Requires basePath to be specified and to evaluate to a collection.
     */
    private RepeatingGroupConfig repeatingGroup;
}
