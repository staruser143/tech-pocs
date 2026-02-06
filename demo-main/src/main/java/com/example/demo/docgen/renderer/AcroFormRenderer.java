package com.example.demo.docgen.renderer;

import com.example.demo.docgen.aspect.LogExecutionTime;
import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.mapper.FieldMappingStrategy;
import com.example.demo.docgen.model.FieldMappingGroup;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.SectionType;
import com.example.demo.docgen.service.TemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renderer for AcroForm PDF templates
 * Fills form fields with data from the context using configurable mapping strategies
 * Supports both single mapping strategy and multiple mapping groups
 */
@Slf4j
@Component
public class AcroFormRenderer implements SectionRenderer {
    
    private final List<FieldMappingStrategy> mappingStrategies;
    private final TemplateLoader templateLoader;
    
    public AcroFormRenderer(List<FieldMappingStrategy> mappingStrategies, TemplateLoader templateLoader) {
        this.mappingStrategies = mappingStrategies;
        this.templateLoader = templateLoader;
    }
    
    @Override
    @LogExecutionTime("AcroForm Rendering")
    public PDDocument render(PageSection section, RenderContext context) {
        try {
            // Load the PDF form template
            PDDocument document = loadTemplate(section.getTemplatePath());
            
            // Get the form
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                log.warn("No AcroForm found in template: {}", section.getTemplatePath());
                return document;
            }
            
            // Map data to field values (supports both single and multiple groups)
            Map<String, String> fieldValues = mapFieldValues(section, context);
            log.info("Mapped field values: {}", fieldValues);
            
            // Fill form fields
            fillFormFields(acroForm, fieldValues);
            
            // Flatten the form to make it read-only (optional)
            // acroForm.flatten();
            
            return document;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to render AcroForm section: " + section.getSectionId(), e);
        }
    }
    
    @Override
    public boolean supports(SectionType type) {
        return type == SectionType.ACROFORM;
    }
    
    /**
     * Map field values using either single strategy or multiple groups
     */
    private Map<String, String> mapFieldValues(PageSection section, RenderContext context) {
        if (section.hasMultipleMappingGroups()) {
            return mapWithMultipleGroups(section, context);
        } else {
            return mapWithSingleStrategy(section, context);
        }
    }
    
    /**
     * Traditional single-strategy mapping (backward compatible)
     */
    private Map<String, String> mapWithSingleStrategy(PageSection section, RenderContext context) {
        log.info("Rendering AcroForm section: {} with single mapping type: {}", 
                section.getSectionId(), section.getMappingType());
        
        FieldMappingStrategy strategy = findMappingStrategy(section.getMappingType());
        return strategy.mapData(context.getData(), section.getFieldMappings());
    }
    
    /**
     * New multi-group mapping - merges results from multiple strategies
     * Supports basePath optimization to avoid repeated filter executions
     */
    private Map<String, String> mapWithMultipleGroups(PageSection section, RenderContext context) {
        log.info("Rendering AcroForm section: {} with {} mapping groups", 
                section.getSectionId(), section.getFieldMappingGroups().size());
        
        Map<String, String> allFieldValues = new HashMap<>();
        
        for (FieldMappingGroup group : section.getFieldMappingGroups()) {
            log.debug("Processing mapping group with type: {}, fields: {}, basePath: {}", 
                     group.getMappingType(), group.getFields().size(), group.getBasePath());
            
            FieldMappingStrategy strategy = findMappingStrategy(group.getMappingType());
            
            Map<String, String> groupValues;
            if (group.getRepeatingGroup() != null) {
                // Handle repeating group (e.g., children)
                groupValues = mapRepeatingGroup(group, context, strategy);
            } else if (group.getBasePath() != null && !group.getBasePath().isEmpty()) {
                // Optimize: evaluate basePath ONCE, then map fields relative to that result
                groupValues = strategy.mapDataWithBasePath(
                    context.getData(), 
                    group.getBasePath(), 
                    group.getFields()
                );
            } else {
                // Standard mapping without basePath optimization
                groupValues = strategy.mapData(context.getData(), group.getFields());
            }
            
            // Merge into results (later groups override earlier ones for same field)
            allFieldValues.putAll(groupValues);
            
            log.debug("Mapped {} fields using {} strategy", 
                     groupValues.size(), group.getMappingType());
        }
        
        log.info("Total fields mapped: {}", allFieldValues.size());
        return allFieldValues;
    }

    /**
     * Maps a repeating group of data (e.g., an array of children) to numbered PDF fields.
     */
    private Map<String, String> mapRepeatingGroup(FieldMappingGroup group, RenderContext context, FieldMappingStrategy strategy) {
        Map<String, String> result = new HashMap<>();
        com.example.demo.docgen.model.RepeatingGroupConfig config = group.getRepeatingGroup();
        
        if (group.getBasePath() == null || group.getBasePath().isEmpty()) {
            log.warn("Repeating group specified without basePath in section {}", group);
            return result;
        }
        
        // 1. Evaluate basePath to get the collection
        Object collection = strategy.evaluatePath(context.getData(), group.getBasePath());
        
        if (!(collection instanceof List)) {
            log.warn("Repeating group basePath '{}' did not evaluate to a List. Got: {}", 
                    group.getBasePath(), collection != null ? collection.getClass().getName() : "null");
            return result;
        }
        
        List<?> items = (List<?>) collection;
        int startIndex = config.getStartIndex();
        int maxItems = config.getMaxItems() != null ? config.getMaxItems() : items.size();
        int count = Math.min(items.size(), maxItems);
        
        log.debug("Mapping repeating group with {} items (max: {})", count, maxItems);
        
        // 2. Iterate over items and map fields
        for (int i = 0; i < count; i++) {
            Object item = items.get(i);
            int displayIndex = startIndex + i;
            
            // Construct PDF field names for this item
            Map<String, String> itemFieldMappings = new HashMap<>();
            for (Map.Entry<String, String> fieldEntry : config.getFields().entrySet()) {
                String baseFieldName = fieldEntry.getKey();
                String dataPath = fieldEntry.getValue();
                
                // Construct PDF field name based on position and separator
                StringBuilder pdfFieldName = new StringBuilder();
                if (config.getPrefix() != null) pdfFieldName.append(config.getPrefix());
                
                if (config.getIndexPosition() == com.example.demo.docgen.model.RepeatingGroupConfig.IndexPosition.BEFORE_FIELD) {
                    pdfFieldName.append(displayIndex);
                    if (config.getIndexSeparator() != null) pdfFieldName.append(config.getIndexSeparator());
                    pdfFieldName.append(baseFieldName);
                } else {
                    pdfFieldName.append(baseFieldName);
                    if (config.getIndexSeparator() != null) pdfFieldName.append(config.getIndexSeparator());
                    pdfFieldName.append(displayIndex);
                }
                
                if (config.getSuffix() != null) pdfFieldName.append(config.getSuffix());
                
                itemFieldMappings.put(pdfFieldName.toString(), dataPath);
            }
            
            // Map fields for this single item
            Map<String, String> itemValues = strategy.mapFromContext(item, itemFieldMappings);
            result.putAll(itemValues);
        }
        
        return result;
    }
    
    private FieldMappingStrategy findMappingStrategy(com.example.demo.docgen.mapper.MappingType mappingType) {
        return mappingStrategies.stream()
            .filter(strategy -> strategy.supports(mappingType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No mapping strategy found for type: " + mappingType));
    }
    
    @LogExecutionTime("Loading PDF Template")
    private PDDocument loadTemplate(String templatePath) throws IOException {
        byte[] pdfBytes = templateLoader.getResourceBytes(templatePath);
        return PDDocument.load(pdfBytes);
    }
    
    @LogExecutionTime("Filling AcroForm Fields")
    private void fillFormFields(PDAcroForm acroForm, Map<String, String> fieldValues) {
        for (Map.Entry<String, String> entry : fieldValues.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();
            
            try {
                PDField field = acroForm.getField(fieldName);
                if (field != null) {
                    field.setValue(value);
                    log.debug("Set field '{}' = '{}'", fieldName, value);
                } else {
                    log.warn("Field not found in form: {}", fieldName);
                }
            } catch (Exception e) {
                log.error("Failed to set field '{}': {}", fieldName, e.getMessage());
            }
        }
    }
}

