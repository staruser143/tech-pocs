package com.example.demo.docgen.mapper;

/**
 * Types of field mapping strategies
 */
public enum MappingType {
    /**
     * Direct Java Map/List access - simple nested property access
     */
    DIRECT,
    
    /**
     * JSONPath expressions - powerful querying and filtering
     */
    JSONPATH,
    
    /**
     * JSONata transformations - complex calculations and transformations
     */
    JSONATA,
    
    /**
     * Custom transformations - write your own Java code for any transformation
     * Use this when JSONATA/JSONPATH don't support what you need
     */
    CUSTOM
}
