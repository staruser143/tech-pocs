package com.example.demo.docgen.renderer;

import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.mapper.FieldMappingStrategy;
import com.example.demo.docgen.mapper.JsonPathMappingStrategy;
import com.example.demo.docgen.mapper.MappingType;
import com.example.demo.docgen.model.FieldMappingGroup;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.RepeatingGroupConfig;
import com.example.demo.docgen.service.TemplateLoader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AcroFormRendererTest {

    private AcroFormRenderer renderer;
    private TemplateLoader templateLoader;

    @BeforeEach
    public void setup() throws IOException {
        templateLoader = Mockito.mock(TemplateLoader.class);
        List<FieldMappingStrategy> strategies = Arrays.asList(new JsonPathMappingStrategy());
        renderer = new AcroFormRenderer(strategies, templateLoader);
        
        // Mock a simple PDF with some fields
        PDDocument doc = new PDDocument();
        PDAcroForm form = new PDAcroForm(doc);
        doc.getDocumentCatalog().setAcroForm(form);
        
        // We can't easily add fields to a mock PDF without a lot of boilerplate, 
        // but we can test the mapping logic by making mapFieldValues protected or testing via reflection,
        // or better, just verify the logic that calls the strategies.
    }

    @Test
    public void testRepeatingGroupMapping() throws Exception {
        // Setup data with an array
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> child1 = new HashMap<>();
        child1.put("name", "Alice");
        Map<String, Object> child2 = new HashMap<>();
        child2.put("name", "Bob");
        data.put("children", Arrays.asList(child1, child2));

        // Setup section with repeating group
        Map<String, String> fields = new HashMap<>();
        fields.put("childName", "name");

        RepeatingGroupConfig repeatingConfig = RepeatingGroupConfig.builder()
                .indexPosition(RepeatingGroupConfig.IndexPosition.AFTER_FIELD)
                .indexSeparator(".")
                .fields(fields)
                .build();

        FieldMappingGroup group = FieldMappingGroup.builder()
                .mappingType(MappingType.JSONPATH)
                .basePath("$.children")
                .fields(new HashMap<>()) // Initialize fields map to avoid NPE
                .repeatingGroup(repeatingConfig)
                .build();

        PageSection section = PageSection.builder()
                .sectionId("test")
                .fieldMappingGroups(Arrays.asList(group))
                .build();

        RenderContext context = new RenderContext(null, data);

        // Use reflection to test the private method mapFieldValues
        java.lang.reflect.Method method = AcroFormRenderer.class.getDeclaredMethod("mapFieldValues", PageSection.class, RenderContext.class);
        method.setAccessible(true);
        Map<String, String> result = (Map<String, String>) method.invoke(renderer, section, context);

        // Verify results
        assertEquals("Alice", result.get("childName.1"));
        assertEquals("Bob", result.get("childName.2"));
    }
}
