package com.example.demo.docgen.model;

/**
 * Rendering types for headers and footers
 */
public enum RenderType {
    /**
     * Direct PDFBox rendering with simple text
     */
    PDFBOX,
    
    /**
     * FreeMarker template to HTML to PDF
     */
    FREEMARKER
}
