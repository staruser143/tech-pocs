package com.example.demo.docgen.renderer;

import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.SectionType;
import com.example.demo.docgen.renderer.pdfbox.ComponentRegistry;
import com.example.demo.docgen.renderer.pdfbox.PdfComponent;
import com.example.demo.docgen.viewmodel.ViewModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renderer for direct PDFBox components.
 * Lookups a registered PdfComponent and executes its rendering logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfBoxDirectSectionRenderer implements SectionRenderer {

    private final ComponentRegistry componentRegistry;
    private final ViewModelFactory viewModelFactory;

    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        log.info("Rendering PDFBox component section: {}", section.getSectionId());
        
        String componentId = section.getTemplatePath();
        PdfComponent component = componentRegistry.get(componentId);
        
        if (component == null) {
            throw new RuntimeException("No PDFBox component registered with ID: " + componentId);
        }

        // Build ViewModel from raw data if viewModelType is specified
        Object dataToUse = viewModelFactory.createViewModel(section.getViewModelType(), context.getData());

        PDDocument document = new PDDocument();
        try {
            // Create a default page for the component to render on
            // Components can add more pages if needed
            PDPage page = new PDPage();
            document.addPage(page);
            
            component.render(document, page, context, dataToUse);
            
            return document;
        } catch (IOException e) {
            log.error("Failed to render PDFBox component: {}", componentId, e);
            try {
                document.close();
            } catch (IOException ex) {
                // Ignore
            }
            throw new RuntimeException("Failed to render PDFBox component: " + componentId, e);
        }
    }

    @Override
    public boolean supports(SectionType type) {
        return type == SectionType.PDFBOX_COMPONENT;
    }
}
