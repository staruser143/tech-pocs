package com.example.demo.docgen.service;

import com.example.demo.docgen.aspect.LogExecutionTime;
import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.mapper.FieldMappingStrategy;
import com.example.demo.docgen.mapper.JsonPathMappingStrategy;
import com.example.demo.docgen.mapper.MappingType;
import com.example.demo.docgen.model.DocumentGenerationRequest;
import com.example.demo.docgen.model.DocumentTemplate;
import com.example.demo.docgen.model.OverflowConfig;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.SectionType;
import com.example.demo.docgen.renderer.SectionRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main orchestrator for document generation
 * Coordinates template loading, section rendering, and PDF assembly
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentComposer {
    private final List<SectionRenderer> renderers;
    private final List<FieldMappingStrategy> mappingStrategies;
    private final TemplateLoader templateLoader;
    private final com.example.demo.docgen.processor.HeaderFooterProcessor headerFooterProcessor;
    
    /**
     * Generate a PDF document from a template and data
     *
     * @param request Generation request with template ID and data
     * @return PDF document as byte array
     */
    @LogExecutionTime("Total Document Generation")
    public byte[] generateDocument(DocumentGenerationRequest request) {
        log.info("Generating document with template: {}", request.getTemplateId());
        
        try {
            // Load template structure (reusable, no data)
            DocumentTemplate template = templateLoader.loadTemplate(request.getTemplateId());
            
            // Create context with template structure + runtime data
            RenderContext context = new RenderContext(template, request.getData());
            
            // Render each section
            List<PDDocument> sectionDocuments = new ArrayList<>();
            List<PageSection> sections = new ArrayList<>(template.getSections());
            sections.sort(Comparator.comparingInt(PageSection::getOrder));
            
            for (PageSection section : sections) {
                // Evaluate condition if present
                if (section.getCondition() != null && !section.getCondition().isEmpty()) {
                    FieldMappingStrategy strategy = findMappingStrategy(section.getMappingType());
                    Object result = strategy.evaluatePath(context.getData(), section.getCondition());
                    
                    boolean shouldRender = false;
                    if (result instanceof Boolean) {
                        shouldRender = (Boolean) result;
                    } else if (result != null) {
                        // If it's not a boolean, we consider it true if it's not null/empty
                        shouldRender = !result.toString().isEmpty() && !result.toString().equalsIgnoreCase("false");
                    }
                    
                    if (!shouldRender) {
                        log.info("Skipping section {} due to condition: {}", section.getSectionId(), section.getCondition());
                        continue;
                    }
                }

                log.info("Rendering section: {} (type: {})", section.getSectionId(), section.getType());
                context.setCurrentSectionId(section.getSectionId());
                
                SectionRenderer renderer = findRenderer(section.getType());
                PDDocument sectionDoc = renderer.render(section, context);
                sectionDocuments.add(sectionDoc);

                // Handle overflows if configured
                if (section.getOverflowConfigs() != null && !section.getOverflowConfigs().isEmpty()) {
                    for (OverflowConfig config : section.getOverflowConfigs()) {
                        List<PDDocument> overflowDocs = handleOverflow(section, config, context);
                        sectionDocuments.addAll(overflowDocs);
                    }
                }
            }
            
            // Merge all sections
            PDDocument mergedDocument = mergeSections(sectionDocuments);
            
            // Apply headers and footers
            headerFooterProcessor.apply(
                mergedDocument,
                template.getHeaderFooterConfig(),
                request.getData()
            );
            
            // Convert to bytes
            byte[] pdfBytes = convertToBytes(mergedDocument);
            
            // Cleanup
            mergedDocument.close();
            for (PDDocument doc : sectionDocuments) {
                doc.close();
            }
            
            log.info("Document generation complete. Size: {} bytes", pdfBytes.length);
            return pdfBytes;
            
        } catch (Exception e) {
            log.error("Document generation failed", e);
            throw new RuntimeException("Failed to generate document", e);
        }
    }

    /**
     * Handles data overflow by generating addendum pages
     */
    private List<PDDocument> handleOverflow(PageSection section, OverflowConfig config, RenderContext context) throws IOException {
        List<PDDocument> overflowDocs = new ArrayList<>();
        
        if (config.getArrayPath() == null || config.getAddendumTemplatePath() == null) {
            return overflowDocs;
        }

        // 1. Evaluate arrayPath using the specified strategy
        FieldMappingStrategy strategy = findMappingStrategy(config.getMappingType());
        Object collection = strategy.evaluatePath(context.getData(), config.getArrayPath());
        
        if (!(collection instanceof List)) {
            log.debug("Overflow arrayPath '{}' did not evaluate to a List", config.getArrayPath());
            return overflowDocs;
        }
        
        List<?> allItems = (List<?>) collection;
        if (allItems.size() <= config.getMaxItemsInMain()) {
            log.debug("No overflow detected for section {}. Items: {}, Max: {}", 
                     section.getSectionId(), allItems.size(), config.getMaxItemsInMain());
            return overflowDocs;
        }
        
        log.info("Overflow detected for section {}. Total items: {}, Max in main: {}", 
                 section.getSectionId(), allItems.size(), config.getMaxItemsInMain());
        
        // 2. Get overflow items
        List<?> overflowItems = allItems.subList(config.getMaxItemsInMain(), allItems.size());
        
        // 3. Partition into pages and render
        int pageSize = config.getItemsPerOverflowPage() > 0 ? config.getItemsPerOverflowPage() : overflowItems.size();
        for (int i = 0; i < overflowItems.size(); i += pageSize) {
            List<?> chunk = overflowItems.subList(i, Math.min(i + pageSize, overflowItems.size()));
            int pageNum = (i / pageSize) + 1;
            
            log.info("Rendering addendum page {} for section {} with {} items", 
                     pageNum, section.getSectionId(), chunk.size());

            // Create a temporary section for the addendum
            PageSection addendumSection = PageSection.builder()
                .sectionId(section.getSectionId() + "_addendum_" + pageNum)
                .type(SectionType.FREEMARKER)
                .templatePath(config.getAddendumTemplatePath())
                .build();
            
            // Create a temporary data map for this addendum page
            Map<String, Object> addendumData = new HashMap<>(context.getData());
            addendumData.put("overflowItems", chunk);
            addendumData.put("isAddendum", true);
            addendumData.put("addendumPageNumber", pageNum);
            addendumData.put("totalAddendumPages", (int) Math.ceil((double) overflowItems.size() / pageSize));
            
            RenderContext addendumContext = new RenderContext(context.getTemplate(), addendumData);
            
            SectionRenderer renderer = findRenderer(SectionType.FREEMARKER);
            overflowDocs.add(renderer.render(addendumSection, addendumContext));
        }
        
        return overflowDocs;
    }

    private FieldMappingStrategy findMappingStrategy(MappingType type) {
        return mappingStrategies.stream()
            .filter(s -> s.supports(type))
            .findFirst()
            .orElseThrow(() -> new UnsupportedOperationException("No strategy found for type: " + type));
    }
    
    private SectionRenderer findRenderer(com.example.demo.docgen.model.SectionType type) {
        return renderers.stream()
            .filter(r -> r.supports(type))
            .findFirst()
            .orElseThrow(() -> new UnsupportedOperationException(
                "No renderer found for section type: " + type));
    }
    
    @LogExecutionTime("Merging PDF Sections")
    private PDDocument mergeSections(List<PDDocument> sections) throws IOException {
        if (sections.isEmpty()) {
            return new PDDocument();
        }
        
        if (sections.size() == 1) {
            return sections.get(0);
        }
        
        PDDocument result = new PDDocument();
        PDFMergerUtility merger = new PDFMergerUtility();
        
        for (PDDocument section : sections) {
            merger.appendDocument(result, section);
        }
        
        return result;
    }
    
    private byte[] convertToBytes(PDDocument document) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        document.save(baos);
        return baos.toByteArray();
    }
}
