package com.example.demo.docgen.processor;

import com.example.demo.docgen.core.PageContext;
import com.example.demo.docgen.model.FooterTemplate;
import com.example.demo.docgen.model.HeaderTemplate;
import com.example.demo.docgen.model.RenderType;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * FreeMarker implementation of header/footer rendering.
 * Renders the content as a FreeMarker template and then overlays it as text.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreeMarkerHeaderFooterRenderer implements HeaderFooterRenderer {
    private final Configuration freemarkerConfig;

    @Override
    public boolean supports(RenderType type) {
        return type == RenderType.FREEMARKER;
    }

    @Override
    public void renderHeader(PDDocument document, PDPage page, HeaderTemplate template, PageContext context) {
        render(document, page, template.getContent(), template.getAlignment(), template.getMarginTop(), context, true);
    }

    @Override
    public void renderFooter(PDDocument document, PDPage page, FooterTemplate template, PageContext context) {
        render(document, page, template.getContent(), template.getAlignment(), template.getMarginBottom(), context, false);
    }

    private void render(PDDocument document, PDPage page, String content, String alignment, float margin, PageContext context, boolean isHeader) {
        try {
            String renderedContent = processTemplate(content, context);
            String[] lines = renderedContent.split("\n");
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                contentStream.setFont(PDType1Font.HELVETICA, 10);
                
                PDRectangle pageSize = page.getMediaBox();
                float y = isHeader ? pageSize.getHeight() - margin : margin;
                
                for (String line : lines) {
                    contentStream.beginText();
                    float textWidth = PDType1Font.HELVETICA.getStringWidth(line) / 1000 * 10;
                    float x = 50;
                    
                    if ("CENTER".equalsIgnoreCase(alignment)) {
                        x = (pageSize.getWidth() - textWidth) / 2;
                    } else if ("RIGHT".equalsIgnoreCase(alignment)) {
                        x = pageSize.getWidth() - textWidth - 50;
                    }
                    
                    contentStream.newLineAtOffset(x, y);
                    contentStream.showText(line);
                    contentStream.endText();
                    y -= 12; // Line height
                }
            }
        } catch (Exception e) {
            log.error("Failed to render FreeMarker {} text", isHeader ? "header" : "footer", e);
        }
    }

    private String processTemplate(String content, PageContext context) throws Exception {
        Template template = new Template("headerFooter", content, freemarkerConfig);
        
        Map<String, Object> model = new HashMap<>();
        if (context.getData() != null) {
            model.putAll(context.getData());
        }
        model.put("pageNumber", context.getPageNumber());
        model.put("totalPages", context.getTotalPages());
        
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        return writer.toString();
    }
}
