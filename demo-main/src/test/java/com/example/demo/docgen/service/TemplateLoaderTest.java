package com.example.demo.docgen.service;

import com.example.demo.docgen.model.DocumentTemplate;
import com.example.demo.docgen.model.PageSection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TemplateLoaderTest {

    @Autowired
    private TemplateLoader templateLoader;

    @Autowired
    private CacheManager cacheManager;

    @Test
    public void testTemplateInheritanceAndMerging() {
        // Load the child template which inherits from base
        DocumentTemplate template = templateLoader.loadTemplate("test-inheritance-child.yaml");

        assertNotNull(template);
        assertEquals("test-inheritance-child", template.getTemplateId());
        
        // Should have 2 sections (section1 merged, section2 excluded, section3 added)
        assertEquals(2, template.getSections().size());

        // Verify section1 merging
        PageSection section1 = template.getSections().stream()
                .filter(s -> s.getSectionId().equals("section1"))
                .findFirst()
                .orElseThrow();
        
        assertEquals("base-path.pdf", section1.getTemplatePath()); // From base
        assertEquals("$.child.field1", section1.getFieldMappings().get("field1")); // Overridden by child
        assertEquals("$.child.field2", section1.getFieldMappings().get("field2")); // Added by child

        // Verify section2 exclusion
        assertFalse(template.getSections().stream().anyMatch(s -> s.getSectionId().equals("section2")));

        // Verify section3 addition
        assertTrue(template.getSections().stream().anyMatch(s -> s.getSectionId().equals("section3")));
    }

    @Test
    public void testCaching() {
        String templateId = "test-inheritance-base.yaml";
        
        // Clear cache first
        templateLoader.clearCache();
        
        // First load - should be a cache miss (logged)
        DocumentTemplate t1 = templateLoader.loadTemplate(templateId);
        
        // Second load - should be a cache hit
        DocumentTemplate t2 = templateLoader.loadTemplate(templateId);
        
        // They should be the same instance if cached correctly (depending on cache implementation, 
        // but for ConcurrentMapCache it is the same instance)
        assertSame(t1, t2);
        
        // Verify cache contains the entry
        assertNotNull(cacheManager.getCache("documentTemplates").get(templateId));
    }
}
