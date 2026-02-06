package com.example.demo.docgen.processor;

import com.example.demo.docgen.core.PageContext;
import com.example.demo.docgen.model.FooterTemplate;
import com.example.demo.docgen.model.HeaderTemplate;
import com.example.demo.docgen.model.RenderType;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * PDFBox implementation for simple text headers and footers
 */
@Slf4j
@Component
public class PdfBoxHeaderFooterRenderer implements HeaderFooterRenderer {

    @Override
    public void renderHeader(PDDocument document, PDPage page, HeaderTemplate template, PageContext context) {
        if (template.getContent() == null || template.getContent().isEmpty()) return;

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
            
            String[] lines = template.getContent().split("\n");
            float y = page.getMediaBox().getHeight() - template.getMarginTop();
            
            for (String line : lines) {
                contentStream.beginText();
                float x = calculateX(page, template.getAlignment(), line);
                contentStream.newLineAtOffset(x, y);
                contentStream.showText(line);
                contentStream.endText();
                y -= 12; // Line height
            }
        } catch (IOException e) {
            log.error("Failed to render PDFBox header", e);
        }
    }

    @Override
    public void renderFooter(PDDocument document, PDPage page, FooterTemplate template, PageContext context) {
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            contentStream.setFont(PDType1Font.HELVETICA, 9);
            
            float y = template.getMarginBottom();
            
            // Render main footer content
            if (template.getContent() != null && !template.getContent().isEmpty()) {
                String[] lines = template.getContent().split("\n");
                // For footer, we might want to render lines upwards or downwards. 
                // Usually downwards from the margin.
                for (String line : lines) {
                    contentStream.beginText();
                    float x = calculateX(page, template.getAlignment(), line);
                    contentStream.newLineAtOffset(x, y);
                    contentStream.showText(line);
                    contentStream.endText();
                    y -= 11; // Line height
                }
            }
            
            // Render page numbers (reset Y if needed, but usually page numbers are on the same line as the first footer line)
            if (template.isIncludePageNumbers()) {
                String pageText = template.getPageNumberFormat()
                        .replace("{page}", String.valueOf(context.getPageNumber()))
                        .replace("{total}", String.valueOf(context.getTotalPages()));
                
                float x = calculateX(page, "RIGHT", pageText) - 20;
                contentStream.beginText();
                contentStream.newLineAtOffset(x, template.getMarginBottom());
                contentStream.showText(pageText);
                contentStream.endText();
            }
        } catch (IOException e) {
            log.error("Failed to render PDFBox footer", e);
        }
    }

    @Override
    public boolean supports(RenderType type) {
        return type == RenderType.PDFBOX;
    }

    private float calculateX(PDPage page, String alignment, String text) {
        float width = page.getMediaBox().getWidth();
        float textWidth = 100; // Rough estimate, in real app use font.getStringWidth
        
        switch (alignment.toUpperCase()) {
            case "LEFT": return 50;
            case "RIGHT": return width - textWidth - 50;
            case "CENTER":
            default: return (width - textWidth) / 2;
        }
    }
}
