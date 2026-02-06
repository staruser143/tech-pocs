package com.example.demo.docgen.processor;

import com.example.demo.docgen.core.PageContext;
import com.example.demo.docgen.model.FooterTemplate;
import com.example.demo.docgen.model.HeaderTemplate;
import com.example.demo.docgen.model.RenderType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Strategy interface for rendering headers and footers
 */
public interface HeaderFooterRenderer {
    /**
     * Render header on a page
     */
    void renderHeader(PDDocument document, PDPage page, HeaderTemplate template, PageContext context);
    
    /**
     * Render footer on a page
     */
    void renderFooter(PDDocument document, PDPage page, FooterTemplate template, PageContext context);
    
    /**
     * Check if this renderer supports the given render type
     */
    boolean supports(RenderType type);
}
