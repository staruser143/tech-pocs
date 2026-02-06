package com.example.demo.docgen.mapper;

import com.api.jsonata4java.expressions.Expressions;
import com.example.demo.docgen.aspect.LogExecutionTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * JSONata mapping strategy using the official JSONata4Java library.
 * Provides full support for JSONata expressions, including:
 * - Complex transformations and filtering
 * - Built-in functions ($sum, $count, $join, etc.)
 * - Conditional logic and variables
 * - Path wildcards and parent references
 */
@Slf4j
@Component
public class JsonataMappingStrategy implements FieldMappingStrategy {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public Map<String, String> mapData(Map<String, Object> sourceData, 
                                      Map<String, String> fieldMappings) {
        return mapFromContext(sourceData, fieldMappings);
    }

    @Override
    @LogExecutionTime("JSONata Mapping")
    public Map<String, String> mapFromContext(Object context, Map<String, String> fieldMappings) {
        Map<String, String> result = new HashMap<>();
        
        if (context == null || fieldMappings == null || fieldMappings.isEmpty()) {
            return result;
        }

        try {
            // Convert context to JsonNode for JSONata4Java
            JsonNode jsonNode = objectMapper.valueToTree(context);
            
            for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
                String formFieldName = mapping.getKey();
                String expressionStr = mapping.getValue().trim();
                
                try {
                    Expressions expression = Expressions.parse(expressionStr);
                    JsonNode evaluated = expression.evaluate(jsonNode);
                    
                    String value = "";
                    if (evaluated != null && !evaluated.isMissingNode() && !evaluated.isNull()) {
                        if (evaluated.isValueNode()) {
                            value = evaluated.asText();
                        } else {
                            value = evaluated.toString();
                        }
                    }
                    
                    result.put(formFieldName, value);
                    log.debug("JSONATA mapping: {} = {}", formFieldName, value);
                } catch (Exception e) {
                    log.error("Failed to evaluate JSONata expression '{}' for field '{}': {}", 
                             expressionStr, formFieldName, e.getMessage());
                    result.put(formFieldName, "");
                }
            }
        } catch (Exception e) {
            log.error("Failed to process JSONata mappings: {}", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public Object evaluatePath(Map<String, Object> sourceData, String path) {
        if (path == null || path.isEmpty()) return null;
        
        try {
            JsonNode jsonNode = objectMapper.valueToTree(sourceData);
            Expressions expression = Expressions.parse(path);
            JsonNode evaluated = expression.evaluate(jsonNode);
            
            if (evaluated == null || evaluated.isMissingNode() || evaluated.isNull()) {
                return null;
            }
            
            if (evaluated.isBoolean()) return evaluated.asBoolean();
            if (evaluated.isNumber()) return evaluated.numberValue();
            if (evaluated.isTextual()) return evaluated.asText();
            
            return evaluated;
        } catch (Exception e) {
            log.error("Failed to evaluate JSONata path '{}': {}", path, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean supports(MappingType type) {
        return type == MappingType.JSONATA;
    }
}
