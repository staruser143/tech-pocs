package com.example.demo.docgen.mapper;

import com.example.demo.docgen.aspect.LogExecutionTime;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JSONPath mapping strategy
 * Powerful querying for complex JSON structures. Good for filtering, wildcards, and expressions.
 * 
 * Supports both standard JSONPath syntax and simplified clean syntax:
 * - Standard: "$.applicants[?(@.type=='PRIMARY')].demographic.firstName"
 * - Clean: "applicants[type='PRIMARY'].demographic.firstName"
 * 
 * Clean syntax is automatically converted to JSONPath for better readability.
 */
@Slf4j
@Component
public class JsonPathMappingStrategy implements FieldMappingStrategy {
    
    @Override
    public Map<String, String> mapData(Map<String, Object> sourceData, 
                                      Map<String, String> fieldMappings) {
        return mapFromContext(sourceData, fieldMappings);
    }

    @Override
    @LogExecutionTime("JSONPath Mapping")
    public Map<String, String> mapFromContext(Object context, Map<String, String> fieldMappings) {
        Map<String, String> result = new HashMap<>();
        
        for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
            String formFieldName = mapping.getKey();
            String expression = mapping.getValue();
            
            // Convert clean syntax to JSONPath if needed
            String jsonPathExpression = normalizeToJsonPath(expression);
            
            try {
                Object value = JsonPath.read(context, jsonPathExpression);
                result.put(formFieldName, convertToString(value));
                log.debug("JSONPATH mapping: {} = {} (expression: {})", 
                         formFieldName, value, jsonPathExpression);
            } catch (PathNotFoundException e) {
                log.warn("JSONPath not found for field '{}': {}", formFieldName, jsonPathExpression);
                result.put(formFieldName, "");
            } catch (Exception e) {
                log.error("Failed to evaluate JSONPath for field '{}': {}", 
                         formFieldName, e.getMessage());
                result.put(formFieldName, "");
            }
        }
        
        return result;
    }
    
    /**
     * Optimized mapping with basePath - evaluates base path ONCE, then maps fields relative to it.
     * This significantly improves performance when multiple fields need the same filtered/nested object.
     * 
     * Example:
     * - basePath: "applicants[type='PRIMARY']" → evaluated once
     * - fields: { "firstName": "demographic.firstName", "lastName": "demographic.lastName" }
     * - Result: filter runs 1 time instead of 2 times
     */
    @Override
    public Map<String, String> mapDataWithBasePath(Map<String, Object> sourceData,
                                                    String basePath,
                                                    Map<String, String> fieldMappings) {
        Map<String, String> result = new HashMap<>();
        
        try {
            // Evaluate basePath ONCE to get the context object
            String normalizedBasePath = normalizeToJsonPath(basePath);
            Object baseContext = JsonPath.read(sourceData, normalizedBasePath);
            
            log.debug("BasePath '{}' evaluated to: {}", normalizedBasePath, 
                     baseContext != null ? baseContext.getClass().getSimpleName() : "null");
            
            if (baseContext == null) {
                log.warn("BasePath '{}' returned null - all fields will be empty", basePath);
                fieldMappings.keySet().forEach(key -> result.put(key, ""));
                return result;
            }
            
            // Handle array results - keep as array if it returns multiple elements
            // This allows accessing array elements with $[0], $[1], etc. in field mappings
            if (baseContext instanceof List) {
                List<?> list = (List<?>) baseContext;
                if (list.isEmpty()) {
                    log.warn("BasePath '{}' returned empty array - all fields will be empty", basePath);
                    fieldMappings.keySet().forEach(key -> result.put(key, ""));
                    return result;
                }
                // For single-element arrays (e.g., PRIMARY, SPOUSE), extract the element
                // For multi-element arrays (e.g., CHILD), keep as array for indexing
                if (list.size() == 1) {
                    baseContext = list.get(0);
                    log.debug("Using single element from array as base context");
                } else {
                    log.debug("Keeping array of {} elements as base context for indexing", list.size());
                }
            }
            
            // Now map each field relative to the base context
            for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
                String formFieldName = mapping.getKey();
                String relativePath = mapping.getValue();
                
                try {
                    // Normalize and apply relative path to base context
                    String normalizedPath = normalizeToJsonPath(relativePath);
                    Object value = JsonPath.read(baseContext, normalizedPath);
                    result.put(formFieldName, convertToString(value));
                    log.debug("JSONPATH basePath mapping: {} = {}", formFieldName, value);
                } catch (PathNotFoundException e) {
                    log.warn("Relative path '{}' not found in base context for field '{}'", 
                            relativePath, formFieldName);
                    result.put(formFieldName, "");
                } catch (Exception e) {
                    log.error("Failed to evaluate relative path '{}' for field '{}': {}", 
                             relativePath, formFieldName, e.getMessage());
                    result.put(formFieldName, "");
                }
            }
            
        } catch (PathNotFoundException e) {
            log.error("BasePath '{}' not found - all fields will be empty", basePath);
            fieldMappings.keySet().forEach(key -> result.put(key, ""));
        } catch (Exception e) {
            log.error("Failed to evaluate basePath '{}': {}", basePath, e.getMessage());
            fieldMappings.keySet().forEach(key -> result.put(key, ""));
        }
        
        return result;
    }
    
    /**
     * Normalizes clean syntax to JSONPath format
     * Examples:
     * - "applicants[type='PRIMARY'].name" → "$.applicants[?(@.type=='PRIMARY')].name"
     * - "items[0].name" → "$.items[0].name"
     * - "[0].name" → "$[0].name" (array indexing without prefix)
     * - "customer.name" → "$.customer.name"
     * - "$.already.jsonpath" → "$.already.jsonpath" (unchanged)
     */
    private String normalizeToJsonPath(String expression) {
        // Already valid JSONPath - return as-is
        if (expression.startsWith("$.") || expression.startsWith("$[")) {
            return expression;
        }
        
        // Handle array indexing at start: [0].field → $[0].field
        if (expression.startsWith("[")) {
            return "$" + expression;
        }
        
        // Add root prefix for object paths
        String normalized = "$." + expression;
        
        // Convert clean filter syntax: [field='value'] → [?(@.field=='value')]
        // Pattern: [word='value'] or [word="value"]
        normalized = normalized.replaceAll(
            "\\[([a-zA-Z_][a-zA-Z0-9_]*)=(['\"])([^'\"]+)\\2\\]",
            "[?(@.$1=='$3')]"
        );
        
        return normalized;
    }
    
    @Override
    public Object evaluatePath(Map<String, Object> sourceData, String path) {
        if (path == null || path.isEmpty()) return null;
        
        try {
            // Handle simple equality: path == 'value'
            if (path.contains(" == ")) {
                String[] parts = path.split(" == ");
                if (parts.length == 2) {
                    String leftPath = parts[0].trim();
                    String rightValue = parts[1].trim();
                    
                    // Remove quotes from right value if present
                    if ((rightValue.startsWith("'") && rightValue.endsWith("'")) || 
                        (rightValue.startsWith("\"") && rightValue.endsWith("\""))) {
                        rightValue = rightValue.substring(1, rightValue.length() - 1);
                    }
                    
                    Object actualValue = evaluatePath(sourceData, leftPath);
                    return rightValue.equals(actualValue != null ? actualValue.toString() : null);
                }
            }
            
            return JsonPath.read(sourceData, normalizeToJsonPath(path));
        } catch (Exception e) {
            log.warn("Failed to evaluate JSONPath '{}': {}", path, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean supports(MappingType type) {
        return type == MappingType.JSONPATH;
    }
    
    private String convertToString(Object value) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof List) {
            // Handle multi-value results by joining with comma
            return ((List<?>) value).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        }
        
        return value.toString();
    }
}
