package com.example.demo.docgen.service;

import com.example.demo.docgen.aspect.LogExecutionTime;
import com.example.demo.docgen.model.DocumentTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads document templates from JSON or YAML files.
 * Supports local classpath, file system, and remote Spring Cloud Config Server.
 */
@Slf4j
@Component
public class TemplateLoader {
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final RestClient restClient = RestClient.create();

    @Value("${spring.cloud.config.uri:}")
    private String configServerUri;

    @Value("${docgen.templates.remote-enabled:true}")
    private boolean remoteEnabled;

    @Value("${spring.application.name:doc-gen-service}")
    private String applicationName;

    @Value("${spring.cloud.config.label:main}")
    private String configLabel;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;
    
    /**
     * Load a document template from file
     *
     * @param templateId Path or ID of the template file
     * @return Loaded template
     */
    @LogExecutionTime("Loading Template Definition")
    @Cacheable(value = "documentTemplates", key = "#templateId")
    public DocumentTemplate loadTemplate(String templateId) {
        return loadTemplate(templateId, Collections.emptyMap());
    }

    /**
     * Load a document template with placeholder resolution against provided variables.
     * Placeholders use the ${path.to.value} syntax and are resolved from the supplied map.
     */
    @LogExecutionTime("Loading Template Definition (with variables)")
    public DocumentTemplate loadTemplate(String templateId, Map<String, Object> variables) {
        log.info("Loading template (resolved): {}", templateId);

        String resolvedTemplateId = resolvePlaceholders(templateId, variables);
        DocumentTemplate template = loadRawTemplate(resolvedTemplateId);

        // Handle inheritance
        if (template.getBaseTemplateId() != null && !template.getBaseTemplateId().isEmpty()) {
            String baseId = template.getBaseTemplateId();
            String resolvedBaseId = resolvePlaceholders(baseId, variables);
            DocumentTemplate baseTemplate = loadTemplate(resolvedBaseId);
            template = mergeTemplates(baseTemplate, template);
        }

        // Handle fragments
        if (template.getIncludedFragments() != null && !template.getIncludedFragments().isEmpty()) {
            for (String fragmentId : template.getIncludedFragments()) {
                String resolvedFragmentId = resolvePlaceholders(fragmentId, variables);
                DocumentTemplate fragment = loadTemplate(resolvedFragmentId);
                template.getSections().addAll(fragment.getSections());
            }
        }

        return template;
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

    private String resolvePlaceholders(String templateId, Map<String, Object> variables) {
        if (templateId == null) return null;
        Matcher m = PLACEHOLDER.matcher(templateId);
        StringBuffer sb = new StringBuffer();

        boolean found = false;
        while (m.find()) {
            found = true;
            String path = m.group(1);
            Object val = getValueFromPath(variables, path);
            if (val == null) {
                throw new com.example.demo.docgen.exception.TemplateLoadingException(
                    "UNRESOLVED_PLACEHOLDER",
                    "Unresolved placeholder '" + path + "' in template id: " + templateId
                );
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val.toString()));
        }
        m.appendTail(sb);

        if (!found) return templateId;
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Object getValueFromPath(Map<String, Object> variables, String path) {
        if (variables == null || variables.isEmpty()) return null;
        String[] parts = path.split("\\.");
        Object current = variables;
        for (String p : parts) {
            if (current == null) return null;
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(p);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Interpolate selected string fields within a loaded DocumentTemplate using the provided variables.
     * Currently interpolates PageSection.templatePath and header/footer content.
     */
    public void interpolateTemplateFields(DocumentTemplate template, Map<String, Object> variables) {
        if (template == null) return;

        if (template.getSections() != null) {
            for (com.example.demo.docgen.model.PageSection section : template.getSections()) {
                if (section.getTemplatePath() != null && section.getTemplatePath().contains("${")) {
                    section.setTemplatePath(resolvePlaceholders(section.getTemplatePath(), variables));
                }
                if (section.getCondition() != null && section.getCondition().contains("${")) {
                    section.setCondition(resolvePlaceholders(section.getCondition(), variables));
                }
            }
        }

        com.example.demo.docgen.model.HeaderFooterConfig hf = template.getHeaderFooterConfig();
        if (hf != null) {
            if (hf.getHeaders() != null) {
                for (com.example.demo.docgen.model.HeaderTemplate ht : hf.getHeaders()) {
                    if (ht.getContent() != null && ht.getContent().contains("${")) {
                        ht.setContent(resolvePlaceholders(ht.getContent(), variables));
                    }
                }
            }
            if (hf.getFooters() != null) {
                for (com.example.demo.docgen.model.FooterTemplate ft : hf.getFooters()) {
                    if (ft.getContent() != null && ft.getContent().contains("${")) {
                        ft.setContent(resolvePlaceholders(ft.getContent(), variables));
                    }
                }
            }
        }
    }

    /**
     * Clear the template cache
     */
    @CacheEvict(value = {"documentTemplates", "rawResources"}, allEntries = true)
    public void clearCache() {
        log.info("Clearing all template and resource caches");
    }

    /**
     * Fetch a raw resource (PDF or FTL) with caching
     */
    @LogExecutionTime("Fetching Raw Resource")
    @Cacheable(value = "rawResources", key = "#path")
    public byte[] getResourceBytes(String path) throws IOException {
        log.info("Fetching raw resource (cache miss): {}", path);
        try (InputStream is = getInputStream(path)) {
            return is.readAllBytes();
        }
    }

    private DocumentTemplate loadRawTemplate(String templateId) {
        // Try to resolve to an existing path (with prefixes/extensions). If none found, resolvedPath will be null.
        String resolvedPath = resolvePath(templateId);

        if (resolvedPath == null) {
            throw new com.example.demo.docgen.exception.TemplateLoadingException(
                "TEMPLATE_NOT_FOUND",
                "Template not found for id '" + templateId + "'. Check src/main/resources/templates or enable remote templates."
            );
        }

        try {
            if (resolvedPath.endsWith(".json")) {
                return loadFromJson(resolvedPath);
            } else if (resolvedPath.endsWith(".yaml") || resolvedPath.endsWith(".yml")) {
                return loadFromYaml(resolvedPath);
            } else {
                // If resolvedPath exists but has no known extension, provide clearer guidance
                throw new com.example.demo.docgen.exception.TemplateLoadingException(
                    "UNSUPPORTED_TEMPLATE_FORMAT",
                    "Resolved template path '" + resolvedPath + "' does not have a supported extension. " +
                    "Use .json, .yaml, or .yml for template files."
                );
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load template: " + resolvedPath, e);
        }
    }

    private List<String> buildCandidatePaths(String templateId) {
        List<String> candidates = new ArrayList<>();
        candidates.add(templateId);
        candidates.add("templates/" + templateId);
        String[] extensions = {".yaml", ".yml", ".json"};
        for (String ext : extensions) {
            candidates.add(templateId + ext);
            candidates.add("templates/" + templateId + ext);
        }
        return candidates;
    }

    private DocumentTemplate mergeTemplates(DocumentTemplate base, DocumentTemplate child) {
        List<com.example.demo.docgen.model.PageSection> mergedSections = new java.util.ArrayList<>();
        
        // Create a map of child sections for easy lookup
        java.util.Map<String, com.example.demo.docgen.model.PageSection> childSectionMap = new java.util.HashMap<>();
        for (com.example.demo.docgen.model.PageSection section : child.getSections()) {
            childSectionMap.put(section.getSectionId(), section);
        }

        // Add sections from base that are not excluded
        for (com.example.demo.docgen.model.PageSection baseSection : base.getSections()) {
            String sectionId = baseSection.getSectionId();
            
            if (child.getExcludedSections() != null && child.getExcludedSections().contains(sectionId)) {
                continue;
            }
            
            // If child has a section with the same ID, merge them
            if (childSectionMap.containsKey(sectionId)) {
                com.example.demo.docgen.model.PageSection childSection = childSectionMap.get(sectionId);
                mergeSection(baseSection, childSection);
                childSectionMap.remove(sectionId); // Mark as processed
            } else if (child.getSectionOverrides() != null && child.getSectionOverrides().containsKey(sectionId)) {
                // Legacy simple path override
                baseSection.setTemplatePath(child.getSectionOverrides().get(sectionId));
            }
            
            mergedSections.add(baseSection);
        }
        
        // Add remaining sections from child (those that didn't override base sections)
        mergedSections.addAll(childSectionMap.values());
        
        // Sort by order
        mergedSections.sort(java.util.Comparator.comparingInt(com.example.demo.docgen.model.PageSection::getOrder));
        
        child.setSections(mergedSections);
        
        // Merge header/footer if child doesn't have one
        if (child.getHeaderFooterConfig() == null) {
            child.setHeaderFooterConfig(base.getHeaderFooterConfig());
        }
        
        return child;
    }

    private void mergeSection(com.example.demo.docgen.model.PageSection base, com.example.demo.docgen.model.PageSection child) {
        if (child.getType() != null) base.setType(child.getType());
        if (child.getTemplatePath() != null) base.setTemplatePath(child.getTemplatePath());
        if (child.getMappingType() != null) base.setMappingType(child.getMappingType());
        if (child.getCondition() != null) base.setCondition(child.getCondition());
        if (child.getOrder() != 0) base.setOrder(child.getOrder());
        if (child.getViewModelType() != null) base.setViewModelType(child.getViewModelType());
        
        // Override mappings if provided
        if (child.getFieldMappings() != null && !child.getFieldMappings().isEmpty()) {
            base.setFieldMappings(child.getFieldMappings());
        }
        
        if (child.getFieldMappingGroups() != null && !child.getFieldMappingGroups().isEmpty()) {
            base.setFieldMappingGroups(child.getFieldMappingGroups());
        }

        if (child.getOverflowConfigs() != null && !child.getOverflowConfigs().isEmpty()) {
            base.setOverflowConfigs(child.getOverflowConfigs());
        }
    }

    private String resolvePath(String templateId) {
        // Return the first existing candidate path, or null if none exist.
        if (templateId == null) return null;

        // 1. Try as is
        if (exists(templateId)) return templateId;

        // 2. Try with templates/ prefix
        String withPrefix = "templates/" + templateId;
        if (exists(withPrefix)) return withPrefix;

        // 3. Try with extensions
        String[] extensions = {".yaml", ".yml", ".json"};
        for (String ext : extensions) {
            if (exists(templateId + ext)) return templateId + ext;
            if (exists("templates/" + templateId + ext)) return "templates/" + templateId + ext;
        }

        // Nothing found
        return null;
    }

    private boolean exists(String path) {
        try {
            if (new ClassPathResource(path).exists() || new File(path).exists()) {
                return true;
            }
            // If config server is configured and enabled, we assume it might exist there
            return remoteEnabled && configServerUri != null && !configServerUri.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    private DocumentTemplate loadFromJson(String path) throws IOException {
        InputStream inputStream = getInputStream(path);
        return jsonMapper.readValue(inputStream, DocumentTemplate.class);
    }
    
    private DocumentTemplate loadFromYaml(String path) throws IOException {
        InputStream inputStream = getInputStream(path);
        return yamlMapper.readValue(inputStream, DocumentTemplate.class);
    }
    
    private InputStream getInputStream(String path) throws IOException {
        // 1. Try loading from classpath first
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                return resource.getInputStream();
            }
        } catch (Exception e) {
            log.debug("Template not found in classpath: {}", path);
        }
        
        // 2. Try loading from file system
        File file = new File(path);
        if (file.exists()) {
            return file.toURI().toURL().openStream();
        }

        // 3. Try loading from Spring Cloud Config Server (Plain Text API)
        if (remoteEnabled && configServerUri != null && !configServerUri.isEmpty()) {
            return getFromConfigServer(path);
        }
        
        throw new IOException("Template file not found: " + path);
    }

    private InputStream getFromConfigServer(String path) throws IOException {
        // Spring Cloud Config Server Plain Text API: /{application}/{profile}/{label}/{path}
        String url = String.format("%s/%s/%s/%s/%s", 
            configServerUri, applicationName, activeProfile, configLabel, path);
        
        log.info("Fetching template from Config Server: {}", url);
        
        try {
            String content = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);
            if (content != null) {
                return new ByteArrayInputStream(content.getBytes());
            }
        } catch (Exception e) {
            log.error("Failed to fetch template from Config Server: {}", url, e);
        }
        
        throw new IOException("Template not found on Config Server: " + path);
    }
}
