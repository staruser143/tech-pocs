package com.example.demo.docgen.model;

/**
 * Types of document sections that can be rendered
 */
public enum SectionType {
    /**
     * FreeMarker template rendered to HTML then converted to PDF
     */
    FREEMARKER,
    
    /**
     * PDF form with AcroForm fields to be filled
     */
    ACROFORM,
    
    /**
     * Direct PDFBox rendering using custom components
     */
    PDFBOX_COMPONENT,
    
    /**
     * Excel template to be filled and converted to PDF
     */
    EXCEL
}
