package com.example.demo.docgen.renderer;

import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.SectionType;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Interface for rendering a specific type of section
 */
public interface SectionRenderer {
    /**
     * Render a section to a PDDocument
     *
     * @param section The section to render
     * @param context Rendering context with data and state
     * @return Rendered PDF document
     */
    PDDocument render(PageSection section, RenderContext context);
    
    /**
     * Check if this renderer supports the given section type
     *
     * @param type Section type
     * @return true if supported
     */
    boolean supports(SectionType type);
}
