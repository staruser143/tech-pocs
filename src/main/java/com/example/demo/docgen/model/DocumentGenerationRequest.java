package com.example.demo.docgen.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Request object for document generation containing template reference and runtime data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGenerationRequest {
    /**
     * Template ID to use for generation
     */
    private String templateId;
    
    /**
     * Runtime data to merge with the template
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
    
    /**
     * Optional generation options
     */
    @Builder.Default
    private Map<String, Object> options = new HashMap<>();
}
