package com.example.demo.docgen.renderer.pdfbox;

import com.example.demo.docgen.core.RenderContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.IOException;

/**
 * Interface for custom PDFBox-based rendering components
 */
public interface PdfComponent {
    /**
     * Render the component onto a PDF page
     *
     * @param document The PDF document being generated
     * @param page The current page to render on
     * @param context Rendering context with shared resources
     * @param data The data or ViewModel for this component
     * @throws IOException If rendering fails
     */
    void render(PDDocument document, PDPage page, RenderContext context, Object data) throws IOException;
}
