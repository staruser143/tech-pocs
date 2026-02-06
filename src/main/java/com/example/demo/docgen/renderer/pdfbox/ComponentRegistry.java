package com.example.demo.docgen.renderer.pdfbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for PDFBox-based rendering components
 */
@Component
public class ComponentRegistry {
    private final Map<String, PdfComponent> components;

    @Autowired
    public ComponentRegistry(Map<String, PdfComponent> components) {
        this.components = components;
    }
    
    /**
     * Get a component by ID
     *
     * @param componentId Component ID
     * @return The component, or null if not found
     */
    public PdfComponent get(String componentId) {
        return components.get(componentId);
    }
}
