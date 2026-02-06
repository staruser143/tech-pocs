package com.example.demo.docgen.renderer;

import com.example.demo.docgen.aspect.LogExecutionTime;
import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.SectionType;
import com.example.demo.docgen.viewmodel.ViewModelFactory;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Renderer for FreeMarker templates.
 * Processes FTL templates into HTML and converts them to PDF using OpenHtmlToPDF.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreeMarkerRenderer implements SectionRenderer {

    private final Configuration freemarkerConfig;
    private final ViewModelFactory viewModelFactory;

    @Override
    @LogExecutionTime("FreeMarker Rendering")
    public PDDocument render(PageSection section, RenderContext context) {
        log.info("Rendering FreeMarker section: {}", section.getSectionId());
        
        try {
            // 1. Build ViewModel from raw data if viewModelType is specified
            Object viewModel = viewModelFactory.createViewModel(section.getViewModelType(), context.getData());

            // 2. Process FreeMarker template with ViewModel
            Template template = freemarkerConfig.getTemplate(section.getTemplatePath());
            StringWriter writer = new StringWriter();
            
            template.process(viewModel, writer);
            String html = writer.toString();

            // 3. Convert HTML to PDF using OpenHtmlToPDF
            return convertHtmlToPdf(html);
        } catch (Exception e) {
            log.error("Failed to render FreeMarker section: {}", section.getSectionId(), e);
            throw new RuntimeException("FreeMarker rendering failed for section: " + section.getSectionId(), e);
        }
    }

    @LogExecutionTime("HTML to PDF Conversion")
    private PDDocument convertHtmlToPdf(String html) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();

            return PDDocument.load(baos.toByteArray());
        }
    }

    @Override
    public boolean supports(SectionType type) {
        return type == SectionType.FREEMARKER;
    }
}
