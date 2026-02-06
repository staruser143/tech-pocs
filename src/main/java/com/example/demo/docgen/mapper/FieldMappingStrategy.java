package com.example.demo.docgen.mapper;

import java.util.Map;

/**
 * Strategy interface for mapping source data to PDF form fields
 */
public interface FieldMappingStrategy {
    /**
     * Map source data to form field values
     * 
     * @param sourceData The source data to map from
     * @param fieldMappings Map of form field names to path/expression strings
     * @return Map of form field names to string values
     */
    Map<String, String> mapData(Map<String, Object> sourceData, 
                                Map<String, String> fieldMappings);
    
    /**
     * Map source data with base path optimization.
     * Evaluates basePath ONCE to get a context object, then maps fields relative to that context.
     * This avoids repeated filter/query executions for better performance.
     * 
     * Default implementation: falls back to standard mapData (no optimization)
     * Strategies can override to provide optimized base path handling.
     * 
     * @param sourceData The source data to map from
     * @param basePath Path/expression to evaluate once as context
     * @param fieldMappings Map of field names to paths (relative to basePath result)
     * @return Map of form field names to string values
     */
    default Map<String, String> mapDataWithBasePath(Map<String, Object> sourceData,
                                                     String basePath,
                                                     Map<String, String> fieldMappings) {
        // Default: no optimization, just use standard mapping
        return mapData(sourceData, fieldMappings);
    }

    /**
     * Map data from a specific context object (not necessarily the root data map).
     * Useful for repeating groups where each item in a collection is used as context.
     * 
     * @param context The context object to map from
     * @param fieldMappings Map of form field names to relative path/expression strings
     * @return Map of form field names to string values
     */
    Map<String, String> mapFromContext(Object context, Map<String, String> fieldMappings);

    /**
     * Evaluate a path/expression against source data and return the result object.
     * 
     * @param sourceData The source data
     * @param path The path/expression to evaluate
     * @return The result object
     */
    Object evaluatePath(Map<String, Object> sourceData, String path);
    
    /**
     * Check if this strategy supports the given mapping type
     */
    boolean supports(MappingType type);
}
