package com.example.demo.docgen.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom mapping strategy for transformations not supported by JSONATA/JSONPATH.
 * Add your own transformation methods here with full Java power!
 * 
 * Field mapping format: "transformationName:arg1,arg2,arg3"
 * Examples:
 * - "formatPhoneUS:demographic.phone"
 * - "calculateAge:demographic.dateOfBirth"
 * - "formatCurrency:totalAmount"
 * - "encryptSSN:demographic.ssn"
 */
@Slf4j
@Component
public class CustomMappingStrategy implements FieldMappingStrategy {
    
    private final DirectMappingStrategy directMapper = new DirectMappingStrategy();
    private final JsonPathMappingStrategy jsonPathMapper = new JsonPathMappingStrategy();
    private final JsonataMappingStrategy jsonataMapper = new JsonataMappingStrategy();
    
    @Override
    public Map<String, String> mapData(Map<String, Object> sourceData, 
                                      Map<String, String> fieldMappings) {
        return mapFromContext(sourceData, fieldMappings);
    }

    @Override
    public Map<String, String> mapFromContext(Object context, Map<String, String> fieldMappings) {
        Map<String, String> result = new HashMap<>();
        
        // Ensure we have a Map for the internal evaluation methods
        Map<String, Object> dataMap;
        if (context instanceof Map) {
            dataMap = (Map<String, Object>) context;
        } else if (context == null) {
            dataMap = Collections.emptyMap();
        } else {
            // Convert POJO to Map using Jackson
            dataMap = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(context, Map.class);
        }
        
        for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
            String formFieldName = mapping.getKey();
            String transformExpression = mapping.getValue();
            
            try {
                String value = applyTransformation(dataMap, transformExpression);
                result.put(formFieldName, value);
                log.debug("CUSTOM mapping: {} = {}", formFieldName, value);
            } catch (Exception e) {
                log.error("Failed to apply custom transformation for field '{}': {}", 
                         formFieldName, e.getMessage());
                result.put(formFieldName, "");
            }
        }
        
        return result;
    }

    @Override
    public Object evaluatePath(Map<String, Object> sourceData, String path) {
        // Custom strategy usually delegates to JsonPath for path evaluation
        return jsonPathMapper.evaluatePath(sourceData, path);
    }
    
    /**
     * Parse and apply transformation: "functionName:arg1,arg2"
     * Supports strategy prefixes: "functionName:jsonpath:expression" or "functionName:jsonata:expression"
     * Also supports direct strategy usage: "jsonpath:expression" (no transformation)
     */
    private String applyTransformation(Map<String, Object> data, String expression) {
        // Check if it's a transformation expression (contains colon)
        if (!expression.contains(":")) {
            // Simple field access - no transformation, just extraction
            // Use JSONPATH for array notation, DIRECT otherwise
            String strategy = expression.contains("[") ? "jsonpath" : "direct";
            Map<String, String> simpleMapping = Map.of("temp", expression);
            if ("jsonpath".equals(strategy)) {
                return jsonPathMapper.mapData(data, simpleMapping).get("temp");
            } else {
                return directMapper.mapData(data, simpleMapping).get("temp");
            }
        }
        
        // Check if it's JUST a strategy prefix without a transformation function
        // Format: "jsonpath:expression" or "jsonata:expression" or "direct:expression"
        if (expression.startsWith("jsonpath:") || 
            expression.startsWith("jsonata:") || 
            expression.startsWith("direct:")) {
            // No transformation - just extract using specified strategy
            return resolveArgument(data, expression).toString();
        }
        
        // Parse transformation: "functionName:args..."
        // Handle cases like "formatPhone:jsonpath:expression" or "formatPhone:field"
        int firstColon = expression.indexOf(':');
        String functionName = expression.substring(0, firstColon).trim();
        String argsString = expression.substring(firstColon + 1);
        
        // Split arguments by comma (but be careful with commas inside strategy expressions)
        String[] args = argsString.isEmpty() ? new String[0] : argsString.split(",");
        
        // Resolve arguments (support both literal values and field paths with strategy prefixes)
        Object[] resolvedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            resolvedArgs[i] = resolveArgument(data, arg);
        }
        
        // Apply the transformation
        return executeTransformation(functionName, resolvedArgs);
    }
    
    /**
     * Resolve argument with optional strategy prefix
     * Formats:
     * - "direct:applicants.0.name" → uses DIRECT mapping (dot notation for arrays)
     * - "jsonpath:applicants[type='PRIMARY'].name" → uses JSONPATH mapping
     * - "jsonata:applicants[type='PRIMARY'].firstName & ' ' & applicants[type='PRIMARY'].lastName" → uses JSONATA
     * - "applicants[0].name" → defaults to JSONPATH mapping (handles array notation)
     * - "'literal value'" → literal string (quotes removed)
     * - Numbers like "6", "10" → treated as literal values
     * - Date formats like "MM/dd/yyyy" with slashes → treated as literal values
     */
    private Object resolveArgument(Map<String, Object> data, String arg) {
        // Check for literal value (starts with quote)
        if (arg.startsWith("'") || arg.startsWith("\"")) {
            return arg.replaceAll("^['\"]|['\"]$", "");
        }
        
        // Check if it's a number - treat as literal
        if (arg.matches("\\d+")) {
            return arg;
        }
        
        // Check if it looks like a date format pattern (has slashes but no dots or brackets)
        // "MM/dd/yyyy", "dd/MM/yy", "MMMM dd yyyy", etc.
        if (!arg.contains(".") && !arg.contains("[") && !arg.startsWith("direct:") && 
            !arg.startsWith("jsonpath:") && !arg.startsWith("jsonata:")) {
            // Date formats have slashes, hyphens, or repeated letters like MM, dd, yyyy
            if (arg.contains("/") || arg.contains("-") || arg.matches(".*([MDYHmsEaZ])\\1.*")) {
                return arg;
            }
        }
        
        // Check for strategy prefix
        String strategy = null;
        String expression = arg;
        
        if (arg.startsWith("direct:")) {
            strategy = "direct";
            expression = arg.substring(7);
        } else if (arg.startsWith("jsonpath:")) {
            strategy = "jsonpath";
            expression = arg.substring(9);
        } else if (arg.startsWith("jsonata:")) {
            strategy = "jsonata";
            expression = arg.substring(8);
        } else {
            // Default: Use JSONPATH if expression contains array notation [], otherwise DIRECT
            strategy = arg.contains("[") ? "jsonpath" : "direct";
        }
        
        // Resolve using appropriate strategy
        Map<String, String> tempMapping = Map.of("temp", expression);
        
        switch (strategy) {
            case "jsonpath":
                return jsonPathMapper.mapData(data, tempMapping).get("temp");
            case "jsonata":
                return jsonataMapper.mapData(data, tempMapping).get("temp");
            default:
                return directMapper.mapData(data, tempMapping).get("temp");
        }
    }
    
    /**
     * Execute the named transformation function
     * ADD YOUR CUSTOM TRANSFORMATIONS HERE!
     */
    private String executeTransformation(String functionName, Object[] args) {
        switch (functionName.toLowerCase()) {
            case "identity":
            case "passthrough":
                return identity(getString(args, 0));
            
            case "formatphoneus":
                return formatPhoneUS(getString(args, 0));
            
            case "calculateage":
                return calculateAge(getString(args, 0));
            
            case "formatcurrency":
                return formatCurrency(getString(args, 0));
            
            case "encryptssn":
                return encryptSSN(getString(args, 0));
            
            case "generaterandom":
                return generateRandom(getInt(args, 0, 6));
            
            case "formatdate":
                return formatDate(getString(args, 0), getString(args, 1, "MM/dd/yyyy"));
            
            case "calculatedays":
            case "calculatedaysbetween":
                return calculateDaysBetween(getString(args, 0), getString(args, 1));
            
            case "removespaces":
                return removeSpaces(getString(args, 0));
            
            case "capitalize":
                return capitalizeWords(getString(args, 0));
            
            case "truncate":
                return truncate(getString(args, 0), getInt(args, 1, 50));
            
            case "hash":
                return hashValue(getString(args, 0));
            
            default:
                throw new IllegalArgumentException("Unknown custom transformation: " + functionName);
        }
    }
    
    // ====================================================================
    // CUSTOM TRANSFORMATION METHODS - Add your own here!
    // ====================================================================
    
    /**
     * Identity/passthrough - returns value unchanged.
     * Use this to select extraction strategy without transformation.
     */
    private String identity(String value) {
        return value == null ? "" : value;
    }
    
    /**
     * Format phone number to US standard: (XXX) XXX-XXXX
     */
    private String formatPhoneUS(String phone) {
        if (phone == null || phone.isEmpty()) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() != 10) return phone; // Return as-is if not 10 digits
        return String.format("(%s) %s-%s", 
            digits.substring(0, 3), 
            digits.substring(3, 6), 
            digits.substring(6));
    }
    
    /**
     * Calculate age from date of birth (ISO format: YYYY-MM-DD)
     */
    private String calculateAge(String dob) {
        if (dob == null || dob.isEmpty()) return "0";
        try {
            LocalDate birthDate = LocalDate.parse(dob);
            LocalDate now = LocalDate.now();
            return String.valueOf(ChronoUnit.YEARS.between(birthDate, now));
        } catch (Exception e) {
            log.error("Failed to calculate age from: {}", dob);
            return "0";
        }
    }
    
    /**
     * Format number as currency: $1,234.56
     */
    private String formatCurrency(String amount) {
        if (amount == null || amount.isEmpty()) return "$0.00";
        try {
            double value = Double.parseDouble(amount);
            return String.format("$%,.2f", value);
        } catch (Exception e) {
            return amount;
        }
    }
    
    /**
     * Encrypt SSN with simple algorithm (replace with real encryption in production!)
     */
    private String encryptSSN(String ssn) {
        if (ssn == null || ssn.isEmpty()) return "";
        // Simple ROT13-like transformation (NOT secure - use real encryption!)
        return Base64.getEncoder().encodeToString(ssn.getBytes());
    }
    
    /**
     * Generate random alphanumeric string of specified length
     */
    private String generateRandom(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * Format date from ISO (YYYY-MM-DD) to custom format
     */
    private String formatDate(String date, String format) {
        if (date == null || date.isEmpty()) return "";
        try {
            LocalDate localDate = LocalDate.parse(date);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return localDate.format(formatter);
        } catch (Exception e) {
            log.error("Failed to format date: {} with format: {}", date, format);
            return date;
        }
    }
    
    /**
     * Calculate days between two dates
     */
    private String calculateDaysBetween(String date1, String date2) {
        if (date1 == null || date2 == null) return "0";
        try {
            LocalDate d1 = LocalDate.parse(date1);
            LocalDate d2 = LocalDate.parse(date2);
            return String.valueOf(Math.abs(ChronoUnit.DAYS.between(d1, d2)));
        } catch (Exception e) {
            return "0";
        }
    }
    
    /**
     * Remove all spaces from string
     */
    private String removeSpaces(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
    
    /**
     * Capitalize each word
     */
    private String capitalizeWords(String value) {
        if (value == null || value.isEmpty()) return "";
        String[] words = value.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
    
    /**
     * Truncate string to max length
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
    
    /**
     * Generate hash of value (simple hashCode - use proper hashing in production)
     */
    private String hashValue(String value) {
        if (value == null) return "";
        return String.valueOf(Math.abs(value.hashCode()));
    }
    
    // ====================================================================
    // Helper methods
    // ====================================================================
    
    private String getString(Object[] args, int index) {
        return getString(args, index, "");
    }
    
    private String getString(Object[] args, int index, String defaultValue) {
        if (args.length <= index || args[index] == null) return defaultValue;
        return args[index].toString();
    }
    
    private int getInt(Object[] args, int index, int defaultValue) {
        if (args.length <= index || args[index] == null) return defaultValue;
        try {
            return Integer.parseInt(args[index].toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    @Override
    public boolean supports(MappingType type) {
        return type == MappingType.CUSTOM;
    }
}
