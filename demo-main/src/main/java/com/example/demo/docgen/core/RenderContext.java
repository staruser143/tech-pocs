package com.example.demo.docgen.core;

import com.example.demo.docgen.model.DocumentTemplate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Context object that carries shared state and resources throughout the rendering pipeline
 */
public class RenderContext {
    private final DocumentTemplate template;
    private final Map<String, Object> data;
    private final Map<String, Object> metadata;
    private int currentPage;
    private String currentSectionId;
    
    // Resource caching to avoid reloading fonts/images
    private final Map<String, PDFont> loadedFonts;
    private final Map<String, PDImageXObject> loadedImages;
    
    /**
     * Constructor takes template structure AND runtime data separately
     */
    public RenderContext(DocumentTemplate template, Map<String, Object> data) {
        this.template = template;
        this.data = data != null ? data : new HashMap<>();
        this.metadata = new HashMap<>();
        this.currentPage = 0;
        this.loadedFonts = new HashMap<>();
        this.loadedImages = new HashMap<>();
    }
    
    // Access template and data
    public DocumentTemplate getTemplate() {
        return template;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    // State management for tracking rendering progress
    public void setCurrentSectionId(String sectionId) {
        this.currentSectionId = sectionId;
    }
    
    public String getCurrentSectionId() {
        return currentSectionId;
    }
    
    public void incrementPage() {
        currentPage++;
    }
    
    public int getCurrentPage() {
        return currentPage;
    }
    
    // Metadata for conditional rendering and custom logic
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    // Resource caching - load fonts/images once and reuse across sections
    public PDFont getOrLoadFont(String fontPath, PDDocument doc) throws IOException {
        return loadedFonts.computeIfAbsent(fontPath, path -> {
            try {
                return PDType0Font.load(doc, new File(path));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load font: " + path, e);
            }
        });
    }
    
    public PDImageXObject getOrLoadImage(String imagePath, PDDocument doc) throws IOException {
        return loadedImages.computeIfAbsent(imagePath, path -> {
            try {
                return PDImageXObject.createFromFile(imagePath, doc);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load image: " + path, e);
            }
        });
    }
}
