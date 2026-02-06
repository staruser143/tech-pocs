package com.example.demo.docgen.processor;

import com.example.demo.docgen.core.PageContext;
import com.example.demo.docgen.model.HeaderFooterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Processor for applying headers and footers to a PDF document
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeaderFooterProcessor {
    private final List<HeaderFooterRenderer> renderers;

    /**
     * Apply headers and footers to all pages of the document
     */
    public void apply(PDDocument document, HeaderFooterConfig config, Map<String, Object> data) {
        if (config == null) return;

        int totalPages = document.getNumberOfPages();
        log.info("Applying headers/footers to {} pages", totalPages);

        for (int i = 0; i < totalPages; i++) {
            int pageNumber = i + 1;
            
            // Check if page is excluded
            if (config.getExcludePages().contains(i)) {
                log.debug("Skipping header/footer for excluded page {}", pageNumber);
                continue;
            }

            PDPage page = document.getPage(i);
            PageContext context = new PageContext(pageNumber, totalPages, data);

            // Apply Headers
            for (com.example.demo.docgen.model.HeaderTemplate header : config.getHeaders()) {
                HeaderFooterRenderer renderer = findRenderer(header.getRenderType());
                renderer.renderHeader(document, page, header, context);
            }

            // Apply Footers
            for (com.example.demo.docgen.model.FooterTemplate footer : config.getFooters()) {
                HeaderFooterRenderer renderer = findRenderer(footer.getRenderType());
                renderer.renderFooter(document, page, footer, context);
            }
        }
    }

    private HeaderFooterRenderer findRenderer(com.example.demo.docgen.model.RenderType type) {
        return renderers.stream()
                .filter(r -> r.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No renderer found for type: " + type));
    }
}
