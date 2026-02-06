package com.example.demo.docgen.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct access mapping strategy using Java Map/List navigation
 * Simple, fast, no dependencies. Best for flat or simple nested structures.
 */
@Slf4j
@Component
public class DirectMappingStrategy implements FieldMappingStrategy {
    
    private static final SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
    
    @Override
    public Map<String, String> mapData(Map<String, Object> sourceData, 
                                      Map<String, String> fieldMappings) {
        return mapFromContext(sourceData, fieldMappings);
    }

    @Override
    public Map<String, String> mapFromContext(Object context, Map<String, String> fieldMappings) {
        Map<String, String> result = new HashMap<>();
        
        for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
            String formFieldName = mapping.getKey();
            String sourceFieldPath = mapping.getValue();
            
            try {
                Object value = getNestedValue(context, sourceFieldPath);
                result.put(formFieldName, convertToString(value));
                log.debug("DIRECT mapping: {} = {}", formFieldName, value);
            } catch (Exception e) {
                log.warn("Failed to map field '{}' from path '{}': {}", 
                        formFieldName, sourceFieldPath, e.getMessage());
                result.put(formFieldName, "");
            }
        }
        
        return result;
    }
    
    @Override
    public Object evaluatePath(Map<String, Object> sourceData, String path) {
        return getNestedValue(sourceData, path);
    }

    @Override
    public boolean supports(MappingType type) {
        return type == MappingType.DIRECT;
    }
    
    /**
     * Navigate nested maps and lists using dot notation
     * Examples: "customerName", "address.street", "items.0.description"
     */
    private Object getNestedValue(Object data, String path) {
        String[] parts = path.split("\\.");
        Object current = data;
        
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else if (current instanceof List && part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                List<?> list = (List<?>) current;
                current = index < list.size() ? list.get(index) : null;
            } else {
                log.warn("Cannot navigate path '{}' at part '{}' - object is not a Map or List", path, part);
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * Convert various types to string representation
     */
    private String convertToString(Object value) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof String) {
            return (String) value;
        }
        
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        
        if (value instanceof Date) {
            return DEFAULT_DATE_FORMAT.format((Date) value);
        }
        
        if (value instanceof List) {
            // Join list items with comma
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(convertToString(list.get(i)));
            }
            return sb.toString();
        }
        
        // Default: toString()
        return value.toString();
    }
}
