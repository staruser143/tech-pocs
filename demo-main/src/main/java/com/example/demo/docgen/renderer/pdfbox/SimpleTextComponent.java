package com.example.demo.docgen.renderer.pdfbox;

import com.example.demo.docgen.core.RenderContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A simple PDFBox component that renders a "Hello World" style message
 */
@Component("simpleTextComponent")
public class SimpleTextComponent implements PdfComponent {

    @Override
    public void render(PDDocument document, PDPage page, RenderContext context, Object data) throws IOException {
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
            contentStream.newLineAtOffset(100, 700);
            
            String title = "PDFBox Component Rendering";
            if (data instanceof java.util.Map) {
                java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                title = (String) dataMap.getOrDefault("title", title);
            }
            
            contentStream.showText(title);
            
            contentStream.setFont(PDType1Font.HELVETICA, 10);
            contentStream.newLineAtOffset(0, -20);
            contentStream.showText("This section was rendered directly using PDFBox components.");
            
            contentStream.endText();
        }
    }
}
