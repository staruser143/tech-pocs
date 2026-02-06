# Architecture Overview

[← Back to Documentation Home](README.md)

## Overview

A flexible PDF document generation system that supports multiple template types (FreeMarker, AcroForms, Excel, PDFBox components) with the ability to compose multi-page/multi-section documents with headers and footers.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Document Generation API                   │
└───────────────────────────┬─────────────────────────────────┘
                            │
                ┌───────────▼──────────┐
                │  DocumentComposer    │
                │  (Orchestrator)      │
                └───────────┬──────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
┌───────▼────────┐  ┌──────▼──────┐  ┌────────▼────────┐
│ FreeMarker     │  │  AcroForm   │  │  Excel/PDFBox   │
│ Section        │  │  Section    │  │  Section        │
│ Renderer       │  │  Renderer   │  │  Renderer       │
└───────┬────────┘  └──────┬──────┘  └────────┬────────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            │
                ┌───────────▼──────────┐
                │   PDF Assembler      │
                │   (Headers/Footers)  │
                └───────────┬──────────┘
                            │
                     ┌──────▼──────┐
                     │  Final PDF  │
                     └─────────────┘
```

## Core Components

### 1. Document Template Model

```java
interface DocumentTemplate {
    String getTemplateId();
    List<PageSection> getSections();
    HeaderFooterConfig getHeaderFooterConfig();
}

interface PageSection {
    String getSectionId();
    SectionType getType();
    String getTemplatePath();  // Path to template file
    Map<String, String> getFieldMappings();  // Optional field mappings
    int getOrder();
}

enum SectionType {
    FREEMARKER,
    ACROFORM,
    PDFBOX_COMPONENT,
    EXCEL
}

class HeaderFooterConfig {
    HeaderTemplate header;
    FooterTemplate footer;
    boolean applyToAllPages;
    Set<Integer> excludePages;
}
```

### 2. RenderContext (Shared Rendering State)

The `RenderContext` is a critical component that carries shared state and resources throughout the rendering pipeline. It provides:

- **Data Access**: Access to the template data model
- **State Tracking**: Current page number, section being rendered
- **Resource Caching**: Reusable fonts, images to optimize performance
- **Conditional Logic**: Metadata for evaluating section conditions
- **Cross-Section Communication**: Share information between renderers

```java
public class RenderContext {
    private final DocumentTemplate template;
    private final Map<String, Object> data;  // Runtime data from request
    private final Map<String, Object> metadata;
    private int currentPage;
    private String currentSectionId;
    
    // Resource caching to avoid reloading fonts/images
    private Map<String, PDFont> loadedFonts;
    private Map<String, PDImageXObject> loadedImages;
    
    // Constructor now takes template AND data separately
    public RenderContext(DocumentTemplate template, Map<String, Object> data) {
        this.template = template;
        this.data = data;  // Data passed from generation request
        this.metadata = new HashMap<>();
        this.currentPage = 0;
        this.loadedFonts = new HashMap<>();
        this.loadedImages = new HashMap<>();
    }
    
    // Access template and data
    public DocumentTemplate getTemplate() { return template; }
    public Map<String, Object> getData() { return data; }
    
    // State management for tracking rendering progress
    public void setCurrentSectionId(String sectionId) { 
        this.currentSectionId = sectionId; 
    }
    public String getCurrentSectionId() { return currentSectionId; }
    
    public void incrementPage() { currentPage++; }
    public int getCurrentPage() { return currentPage; }
    
    // Metadata for conditional rendering and custom logic
    public void setMetadata(String key, Object value) { 
        metadata.put(key, value); 
    }
    public Object getMetadata(String key) { 
        return metadata.get(key); 
    }
    
    // Resource caching - load fonts/images once and reuse across sections
    public PDFont getOrLoadFont(String fontPath, PDDocument doc) throws IOException {
        return loadedFonts.computeIfAbsent(fontPath, 
            path -> loadFont(path, doc));
    }
    
    public PDImageXObject getOrLoadImage(String imagePath, PDDocument doc) throws IOException {
        return loadedImages.computeIfAbsent(imagePath,
            path -> loadImage(path, doc));
    }
    
    private PDFont loadFont(String path, PDDocument doc) {
        try {
            return PDType0Font.load(doc, new File(path));
        } catch (IOException e) {
            throw new ResourceLoadException("Failed to load font: " + path, e);
        }
    }
    
    private PDImageXObject loadImage(String path, PDDocument doc) throws IOException {
        return PDImageXObject.createFromFile(path, doc);
    }
}
```

**Usage Example:**

```java
// In a SectionRenderer
public PDDocument render(PageSection section, RenderContext context) {
    // Access shared data
    Map<String, Object> data = context.getData();
    
    // Use cached resources
    PDFont font = context.getOrLoadFont("/fonts/Arial.ttf", document);
    
    // Track state
    context.setMetadata("lastProcessedSection", section.getSectionId());
    
    // Check conditional logic
    if (context.getMetadata("includeOptionalContent") == Boolean.TRUE) {
        // Render optional content
    }
    
    return document;
}
```

### 3. DocumentComposer (Orchestrator)

```java
// Request object containing template reference and runtime data
class DocumentGenerationRequest {
    private String templateId;  // or DocumentTemplate template
    private Map<String, Object> data;  // Runtime data to merge
    private Map<String, Object> options;  // Optional generation options
    
    // Getters/setters
}

@Service
class DocumentComposer {
    private List<SectionRenderer> renderers;
    private PdfAssembler pdfAssembler;
    private HeaderFooterProcessor headerFooterProcessor;
    private TemplateLoader templateLoader;
    
    // Data passed at request time, not stored in template
    public byte[] generateDocument(DocumentGenerationRequest request) {
        // Load template structure (reusable, no data)
        DocumentTemplate template = templateLoader.loadTemplate(request.getTemplateId());
        
        // Create context with template structure + runtime data
        RenderContext context = new RenderContext(template, request.getData());
        List<PDDocument> sectionDocuments = new ArrayList<>();
        
        // Render each section
        for (PageSection section : template.getSections()) {
            SectionRenderer renderer = findRenderer(section.getType());
            PDDocument sectionDoc = renderer.render(section, context);
            sectionDocuments.add(sectionDoc);
        }
        
        // Merge all sections
        PDDocument mergedDocument = pdfAssembler.mergeSections(sectionDocuments);
        
        // Apply headers and footers
        PDDocument finalDocument = headerFooterProcessor.apply(
            mergedDocument, 
            template.getHeaderFooterConfig(),
            request.getData()  // Data for dynamic headers/footers
        );
        
        return convertToBytes(finalDocument);
    }
    
    private SectionRenderer findRenderer(SectionType type) {
        return renderers.stream()
            .filter(r -> r.supports(type))
            .findFirst()
            .orElseThrow(() -> new UnsupportedSectionTypeException(type));
    }
}
```

### 4. PDF Assembler

```java
@Component
class PdfAssembler {
    public PDDocument mergeSections(List<PDDocument> sections) {
        PDDocument result = new PDDocument();
        PDFMergerUtility merger = new PDFMergerUtility();
        
        for (PDDocument section : sections) {
            merger.appendDocument(result, section);
        }
        
        return result;
    }
}
```

## Technology Stack

### Core Dependencies (Maven)

```xml
<dependencies>
    <!-- PDF Generation -->
    <dependency>
        <groupId>org.apache.pdfbox</groupId>
        <artifactId>pdfbox</artifactId>
        <version>3.0.1</version>
    </dependency>
    
    <!-- FreeMarker Template Engine -->
    <dependency>
        <groupId>org.freemarker</groupId>
        <artifactId>freemarker</artifactId>
        <version>2.3.32</version>
    </dependency>
    
    <!-- YAML Support -->
    <dependency>
        <groupId>org.yaml</groupId>
        <artifactId>snakeyaml</artifactId>
        <version>2.2</version>
    </dependency>
    
    <!-- HTML to PDF (for FreeMarker output) -->
    <dependency>
        <groupId>com.openhtmltopdf</groupId>
        <artifactId>openhtmltopdf-pdfbox</artifactId>
        <version>1.0.10</version>
    </dependency>
    <dependency>
        <groupId>com.openhtmltopdf</groupId>
        <artifactId>openhtmltopdf-svg-support</artifactId>
        <version>1.0.10</version>
    </dependency>
    
    <!-- Excel Support (Apache POI) -->
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi</artifactId>
        <version>5.2.5</version>
    </dependency>
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>5.2.5</version>
    </dependency>
    
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

## Document Generation Flow

```
1. Client Request (templateId + runtime data)
   ↓
2. Load Document Template Definition (structure only, reusable)
   ↓
3. Create RenderContext (template structure + request data)
   ↓
4. For Each Section:
   a. Determine Section Type
   b. Select Appropriate Renderer
   c. Render Section with data from context
   d. Add to Section List
   ↓
5. Merge All Sections
   ↓
6. Apply Headers and Footers (with runtime data)
   ↓
7. Return Final PDF
```

## API Usage Example

```java
@RestController
@RequestMapping("/api/documents")
class DocumentController {
    @Autowired
    private DocumentComposer documentComposer;
    
    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody DocumentGenerationRequest request) {
        // Request contains:
        // - templateId: "enrollment-form-v1" (reusable template)
        // - data: { applicant: {...}, dependents: [...], ... } (runtime data)
        
        byte[] pdf = documentComposer.generateDocument(request);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "document.pdf");
        
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}

// Example request payload:
{
  "templateId": "enrollment-form-v1",
  "data": {
    "applicant": {
      "firstName": "John",
      "lastName": "Doe",
      "ssn": "123-45-6789"
    },
    "dependents": [
      {"firstName": "Jane", "lastName": "Doe", "relationship": "Spouse"}
    ],
    "coverages": [
      {"type": "Medical", "plan": "Gold"}
    ]
  },
  "options": {
    "locale": "en_US",
    "includeWatermark": false
  }
}
```

**Key Separation:**
- **Template** (`enrollment-form-v1`): Stored once, defines structure, sections, layout - **reusable**
- **Data**: Passed with each request - applicant info, dependents, coverage - **specific to this generation**
- **Result**: PDF generated by merging template structure with runtime data

## Template Composition & Inheritance

### Problem: Template Explosion in Healthcare Domain

In healthcare enrollment, you may have hundreds of templates based on:
- **Product Type**: HMO, PPO, EPO, POS
- **Market Category**: Individual, Small Group, Large Group
- **State**: 50 states with different regulations
- **Special Scenarios**: COBRA, Medicare Supplement, ACA plans

**Without composition:** 4 products × 3 markets × 50 states = **600 templates** 🔥

### Solution: Combined Inheritance + Composition Patterns

The design leverages **BOTH** patterns together:

#### 1. **Inheritance Pattern** (IS-A Relationship)
Templates extend base templates, inheriting and overriding sections.

```java
// enrollment-hmo-ca-small-group-v1 IS-A enrollment-hmo-ca-v1
// enrollment-hmo-ca-v1 IS-A enrollment-hmo-v1  
// enrollment-hmo-v1 IS-A enrollment-base-v1
```

#### 2. **Composition Pattern** (HAS-A Relationship)
Templates are composed of sections, and can include reusable section groups.

```java
// enrollment-hmo-ca-v1 HAS-A list of sections
// sections can be composed from section fragments
// templates can include shared section groups
```

### Combined Approach: Enhanced Template Model

```java
// Enhanced DocumentTemplate supporting BOTH patterns
interface DocumentTemplate {
    String getTemplateId();
    
    // INHERITANCE: Extend from base template
    String getBaseTemplateId();  // Parent template (IS-A)
    List<String> getExcludedSections();  // Remove inherited sections
    Map<String, String> getSectionOverrides();  // Override inherited sections
    
    // COMPOSITION: Build from sections and fragments
    List<PageSection> getSections();  // Own sections (HAS-A)
    List<String> getIncludedSectionGroups();  // Include reusable groups (HAS-A)
    List<String> getIncludedFragments();  // Include template fragments (HAS-A)
    
    HeaderFooterConfig getHeaderFooterConfig();
    Map<String, Object> getMetadata();
}

// Section Group - reusable composition unit
interface SectionGroup {
    String getGroupId();
    String getDescription();
    List<PageSection> getSections();
    Map<String, Object> getConditions();  // When to include this group
}

// Template Fragment - partial template for reuse
interface TemplateFragment {
    String getFragmentId();
    List<PageSection> getSections();
    HeaderFooterConfig getHeaderFooterConfig();
}
```

### Pattern 1: Inheritance Hierarchy

```yaml
# Base template (parent)
templateId: enrollment-base-v1
sections:
  - sectionId: applicant-info
    type: ACROFORM
    templatePath: forms/common/applicant.pdf
    order: 1
    
  - sectionId: dependent-info
    type: ACROFORM
    templatePath: forms/common/dependents.pdf
    order: 2
    
  - sectionId: authorization
    type: FREEMARKER
    templatePath: templates/common/authorization.ftl
    order: 10

---
# Child template (inheritance)
templateId: enrollment-hmo-ca-v1
baseTemplateId: enrollment-base-v1  # INHERITS from base
metadata:
  productType: HMO
  state: CA
sectionOverrides:
  authorization: templates/state/ca/authorization.ftl  # OVERRIDE
sections:
  - sectionId: ca-disclosure  # ADD new section
    type: FREEMARKER
    templatePath: templates/state/ca/disclosure.ftl
    order: 9
```

### Pattern 2: Section Group Composition

```yaml
# Reusable section group definition
groupId: hipaa-compliance-group
description: Standard HIPAA compliance sections
sections:
  - sectionId: hipaa-notice
    type: FREEMARKER
    templatePath: templates/compliance/hipaa-notice.ftl
    order: 100
    
  - sectionId: privacy-practices
    type: FREEMARKER
    templatePath: templates/compliance/privacy-practices.ftl
    order: 101

---
# Another reusable group
groupId: state-mandated-disclosures-ca
description: California state-mandated disclosures
sections:
  - sectionId: ca-ab-1672
    type: FREEMARKER
    templatePath: templates/state/ca/ab-1672.ftl
    order: 200
    
  - sectionId: ca-surprise-billing
    type: FREEMARKER
    templatePath: templates/state/ca/surprise-billing.ftl
    order: 201

---
# Template using COMPOSITION
templateId: enrollment-hmo-ca-v1
baseTemplateId: enrollment-hmo-v1  # INHERITANCE
includedSectionGroups:  # COMPOSITION
  - hipaa-compliance-group
  - state-mandated-disclosures-ca
sections:
  - sectionId: pcp-selection
    type: ACROFORM
    templatePath: forms/hmo/pcp-selection.pdf
    order: 4
```

### Pattern 3: Template Fragments (Mix-in Composition)

```yaml
# Fragment: Common footer pages
fragmentId: standard-footer-pages
sections:
  - sectionId: terms-and-conditions
    type: FREEMARKER
    templatePath: templates/common/terms.ftl
    order: 900
    
  - sectionId: contact-info
    type: FREEMARKER
    templatePath: templates/common/contact.ftl
    order: 901

---
# Fragment: Small group specific pages
fragmentId: employer-attestation-pages
sections:
  - sectionId: employer-attestation
    type: ACROFORM
    templatePath: forms/small-group/employer-attestation.pdf
    order: 50
    
  - sectionId: employee-roster
    type: EXCEL
    templatePath: templates/small-group/employee-roster.xlsx
    order: 51

---
# Template using multiple fragments (composition)
templateId: enrollment-hmo-ca-small-group-v1
baseTemplateId: enrollment-hmo-ca-v1  # INHERITANCE
includedSectionGroups:  # COMPOSITION from groups
  - hipaa-compliance-group
includedFragments:  # COMPOSITION from fragments
  - standard-footer-pages
  - employer-attestation-pages
sections:  # Own sections
  - sectionId: group-coverage-summary
    type: FREEMARKER
    templatePath: templates/small-group/coverage-summary.ftl
    order: 5
```

### Enhanced Composite Template Builder

```java
@Component
class CompositeTemplateBuilder {
    private TemplateRepository templateRepository;
    private SectionGroupRepository sectionGroupRepository;
    private TemplateFragmentRepository fragmentRepository;
    
    public DocumentTemplate buildTemplate(String templateId) {
        DocumentTemplate template = templateRepository.findById(templateId);
        
        CompositeDocumentTemplate result = new CompositeDocumentTemplate();
        result.setTemplateId(templateId);
        
        List<PageSection> allSections = new ArrayList<>();
        
        // 1. INHERITANCE: Recursively get sections from base template
        if (template.getBaseTemplateId() != null) {
            DocumentTemplate baseTemplate = buildTemplate(template.getBaseTemplateId());
            allSections.addAll(baseTemplate.getSections());
        }
        
        // 2. COMPOSITION: Include sections from section groups
        for (String groupId : template.getIncludedSectionGroups()) {
            SectionGroup group = sectionGroupRepository.findById(groupId);
            if (shouldIncludeGroup(group, template)) {
                allSections.addAll(group.getSections());
            }
        }
        
        // 3. COMPOSITION: Include sections from fragments
        for (String fragmentId : template.getIncludedFragments()) {
            TemplateFragment fragment = fragmentRepository.findById(fragmentId);
            allSections.addAll(fragment.getSections());
        }
        
        // 4. Remove excluded sections (from inheritance)
        allSections.removeIf(s -> 
            template.getExcludedSections().contains(s.getSectionId())
        );
        
        // 5. Override sections (from inheritance)
        for (Map.Entry<String, String> override : template.getSectionOverrides().entrySet()) {
            allSections.removeIf(s -> s.getSectionId().equals(override.getKey()));
            allSections.add(createOverrideSection(override.getKey(), override.getValue()));
        }
        
        // 6. Add template's own sections
        allSections.addAll(template.getSections());
        
        // 7. Sort by order and remove duplicates
        allSections = deduplicateAndSort(allSections);
        
        result.setSections(allSections);
        result.setHeaderFooterConfig(resolveHeaderFooterConfig(template));
        
        return result;
    }
    
    private boolean shouldIncludeGroup(SectionGroup group, DocumentTemplate template) {
        // Check if group conditions match template metadata
        if (group.getConditions() == null || group.getConditions().isEmpty()) {
            return true;
        }
        
        for (Map.Entry<String, Object> condition : group.getConditions().entrySet()) {
            Object templateValue = template.getMetadata().get(condition.getKey());
            if (!condition.getValue().equals(templateValue)) {
                return false;
            }
        }
        return true;
    }
    
    private List<PageSection> deduplicateAndSort(List<PageSection> sections) {
        // Keep last occurrence of each section ID (allows overriding)
        Map<String, PageSection> sectionMap = new LinkedHashMap<>();
        for (PageSection section : sections) {
            sectionMap.put(section.getSectionId(), section);
        }
        
        return sectionMap.values().stream()
            .sorted(Comparator.comparingInt(PageSection::getOrder))
            .collect(Collectors.toList());
    }
}
```

### Real-World Example: Both Patterns Combined

```yaml
# Scenario: California Small Group HMO Enrollment

# Result template composition:
templateId: enrollment-hmo-ca-small-group-v1

# Uses INHERITANCE from:
├─ enrollment-hmo-ca-v1 (state variant)
│  ├─ enrollment-hmo-v1 (product variant)
│  │  └─ enrollment-base-v1 (base)

# Uses COMPOSITION of:
├─ Section Groups:
│  ├─ hipaa-compliance-group (federal requirements)
│  ├─ state-mandated-disclosures-ca (CA requirements)
│  └─ aca-marketplace-group (if ACA plan)
│
├─ Fragments:
│  ├─ standard-footer-pages (common to all)
│  └─ employer-attestation-pages (small group specific)
│
└─ Own Sections:
   ├─ group-coverage-summary
   └─ employer-contribution-schedule

# Final composed template has ~20 sections from:
# - 5 sections inherited from base templates
# - 8 sections from composed groups  
# - 4 sections from fragments
# - 3 own sections
```

### Benefits of Combined Approach

```java
// Enhanced DocumentTemplate with inheritance
interface DocumentTemplate {
    String getTemplateId();
    String getBaseTemplateId();  // Optional parent template
    List<PageSection> getSections();
    List<String> getExcludedSections();  // Sections to remove from base
    Map<String, String> getSectionOverrides();  // Override base sections
    HeaderFooterConfig getHeaderFooterConfig();
    Map<String, Object> getMetadata();  // Product, State, Market metadata
}

// Composite template builder
@Component
class CompositeTemplateBuilder {
    private TemplateRepository templateRepository;
    
    public DocumentTemplate buildTemplate(String templateId) {
        DocumentTemplate template = templateRepository.findById(templateId);
        
        if (template.getBaseTemplateId() == null) {
            return template;  // Base template, no composition needed
        }
        
        // Recursively build from base templates
        DocumentTemplate baseTemplate = buildTemplate(template.getBaseTemplateId());
        
        return composeTemplates(baseTemplate, template);
    }
    
    private DocumentTemplate composeTemplates(DocumentTemplate base, DocumentTemplate variant) {
        CompositeDocumentTemplate composite = new CompositeDocumentTemplate();
        composite.setTemplateId(variant.getTemplateId());
        
        // 1. Start with base sections
        List<PageSection> sections = new ArrayList<>(base.getSections());
        
        // 2. Remove excluded sections
        sections.removeIf(s -> variant.getExcludedSections().contains(s.getSectionId()));
        
        // 3. Override sections with variant-specific implementations
        for (Map.Entry<String, String> override : variant.getSectionOverrides().entrySet()) {
            sections.removeIf(s -> s.getSectionId().equals(override.getKey()));
            sections.add(createOverrideSection(override.getKey(), override.getValue()));
        }
        
        // 4. Add new variant-specific sections
        sections.addAll(variant.getSections());
        
        // 5. Sort by order
        sections.sort(Comparator.comparingInt(PageSection::getOrder));
        
        composite.setSections(sections);
        composite.setHeaderFooterConfig(
            variant.getHeaderFooterConfig() != null 
                ? variant.getHeaderFooterConfig() 
                : base.getHeaderFooterConfig()
        );
        
        return composite;
    }
}
```

### Benefits of Combined Approach

#### Inheritance Benefits (IS-A):
- ✅ **Natural hierarchy**: Product → State → Market variants
- ✅ **Override capability**: State-specific regulations override defaults
- ✅ **Reduced duplication**: Common behavior inherited once
- ✅ **Type safety**: Template variants are compatible

#### Composition Benefits (HAS-A):
- ✅ **Mix and match**: Combine sections from multiple sources
- ✅ **Reusability**: Section groups used across different template hierarchies
- ✅ **Flexibility**: Same group in HMO, PPO, EPO templates
- ✅ **Modularity**: Update compliance group → all templates updated

#### Combined Power:
```
Inheritance handles: Product/State/Market variants
Composition handles: Compliance sections, shared pages, regulations

Example:
- HMO and PPO templates (different hierarchies)
- Both include HIPAA compliance group (composition)
- Both include state-specific groups (composition)
- Each has product-specific sections (inheritance + own sections)
```

### Comparison: Pure Inheritance vs. Combined Approach

| Aspect | Pure Inheritance | Inheritance + Composition |
|--------|------------------|---------------------------|
| Template Count | ~200 | ~60 |
| Reusable Units | None | Section groups, fragments |
| Cross-hierarchy Sharing | Difficult | Easy (compose same groups) |
| Regulatory Updates | Update each hierarchy | Update group once |
| Flexibility | Limited | High |
| Maintenance | Complex | Moderate |

### Healthcare Template Repository Structure

```java
// Enhanced DocumentTemplate with inheritance
interface DocumentTemplate {
    String getTemplateId();
    String getBaseTemplateId();  // Optional parent template
    List<PageSection> getSections();
    List<String> getExcludedSections();  // Sections to remove from base
    Map<String, String> getSectionOverrides();  // Override base sections
    HeaderFooterConfig getHeaderFooterConfig();
    Map<String, Object> getMetadata();  // Product, State, Market metadata
}

// Composite template builder
@Component
class CompositeTemplateBuilder {
    private TemplateRepository templateRepository;
    
    public DocumentTemplate buildTemplate(String templateId) {
        DocumentTemplate template = templateRepository.findById(templateId);
        
        if (template.getBaseTemplateId() == null) {
            return template;  // Base template, no composition needed
        }
        
        // Recursively build from base templates
        DocumentTemplate baseTemplate = buildTemplate(template.getBaseTemplateId());
        
        return composeTemplates(baseTemplate, template);
    }
    
    private DocumentTemplate composeTemplates(DocumentTemplate base, DocumentTemplate variant) {
        CompositeDocumentTemplate composite = new CompositeDocumentTemplate();
        composite.setTemplateId(variant.getTemplateId());
        
        // 1. Start with base sections
        List<PageSection> sections = new ArrayList<>(base.getSections());
        
        // 2. Remove excluded sections
        sections.removeIf(s -> variant.getExcludedSections().contains(s.getSectionId()));
        
        // 3. Override sections with variant-specific implementations
        for (Map.Entry<String, String> override : variant.getSectionOverrides().entrySet()) {
            sections.removeIf(s -> s.getSectionId().equals(override.getKey()));
            sections.add(createOverrideSection(override.getKey(), override.getValue()));
        }
        
        // 4. Add new variant-specific sections
        sections.addAll(variant.getSections());
        
        // 5. Sort by order
        sections.sort(Comparator.comparingInt(PageSection::getOrder));
        
        composite.setSections(sections);
        composite.setHeaderFooterConfig(
            variant.getHeaderFooterConfig() != null 
                ? variant.getHeaderFooterConfig() 
                : base.getHeaderFooterConfig()
        );
        
        return composite;
    }
}
```

### Healthcare Enrollment Template Hierarchy

```yaml
# Base template: Common enrollment sections
```
Templates (Inheritance Tree):
```
templates/
├── base/
│   └── enrollment-base-v1.yaml
├── products/
│   ├── enrollment-hmo-v1.yaml (extends base)
│   ├── enrollment-ppo-v1.yaml (extends base)
│   └── enrollment-epo-v1.yaml (extends base)
├── states/
│   ├── enrollment-hmo-ca-v1.yaml (extends hmo)
│   ├── enrollment-hmo-ny-v1.yaml (extends hmo)
│   └── enrollment-ppo-ca-v1.yaml (extends ppo)
└── markets/
    ├── enrollment-hmo-ca-individual-v1.yaml (extends hmo-ca)
    └── enrollment-hmo-ca-small-group-v1.yaml (extends hmo-ca)
```

Section Groups (Composition Units):
```
section-groups/
├── compliance/
│   ├── hipaa-compliance-group.yaml
│   ├── aca-marketplace-group.yaml
│   └── medicare-supplement-group.yaml
├── state-regulations/
│   ├── california-disclosures-group.yaml
│   ├── new-york-disclosures-group.yaml
│   └── texas-disclosures-group.yaml
└── market-specific/
    ├── small-group-requirements-group.yaml
    └── large-group-requirements-group.yaml
```

Fragments (Reusable Template Parts):
```
fragments/
├── common-footers.yaml
├── employer-pages.yaml
├── broker-commissions.yaml
└── enrollment-checklist.yaml
```

### Example YAML Definitions

```yaml
templateId: enrollment-base-v1
sections:
  - sectionId: applicant-info
    type: ACROFORM
    templatePath: forms/common/applicant.pdf
    order: 1
    
  - sectionId: dependent-info
    type: ACROFORM
    templatePath: forms/common/dependents.pdf
    order: 2
    
  - sectionId: coverage-selection
    type: ACROFORM
    templatePath: forms/common/coverage.pdf
    order: 3
    
  - sectionId: authorization
    type: FREEMARKER
    templatePath: templates/common/authorization.ftl
    order: 10

---
# Product variant: HMO-specific additions
templateId: enrollment-hmo-v1
baseTemplateId: enrollment-base-v1
metadata:
  productType: HMO
sections:
  - sectionId: pcp-selection  # New HMO-specific section
    type: ACROFORM
    templatePath: forms/hmo/pcp-selection.pdf
    order: 4

---
# State variant: California HMO
templateId: enrollment-hmo-ca-v1
baseTemplateId: enrollment-hmo-v1
metadata:
  productType: HMO
  state: CA
sectionOverrides:
  authorization: templates/state/ca/authorization.ftl  # CA-specific authorization
sections:
  - sectionId: ca-disclosure  # California-specific disclosure
    type: FREEMARKER
    templatePath: templates/state/ca/disclosure.ftl
    order: 9

---
# Market variant: Small Group California HMO
templateId: enrollment-hmo-ca-small-group-v1
baseTemplateId: enrollment-hmo-ca-v1
metadata:
  productType: HMO
  state: CA
  marketCategory: SMALL_GROUP
sections:
  - sectionId: employer-info  # Small group needs employer info
    type: ACROFORM
    templatePath: forms/small-group/employer.pdf
    order: 0
excludedSections:
  - dependent-info  # Small group uses different dependent section
sections:
  - sectionId: employee-dependent-info
    type: ACROFORM
    templatePath: forms/small-group/employee-dependents.pdf
    order: 2
```
templateId: enrollment-base-v1
sections:
  - sectionId: applicant-info
    type: ACROFORM
    templatePath: forms/common/applicant.pdf
    order: 1
    
  - sectionId: dependent-info
    type: ACROFORM
    templatePath: forms/common/dependents.pdf
    order: 2
    
  - sectionId: coverage-selection
    type: ACROFORM
    templatePath: forms/common/coverage.pdf
    order: 3
    
  - sectionId: authorization
    type: FREEMARKER
    templatePath: templates/common/authorization.ftl
    order: 10

---
# Product variant: HMO-specific additions
templateId: enrollment-hmo-v1
baseTemplateId: enrollment-base-v1
metadata:
  productType: HMO
sections:
  - sectionId: pcp-selection  # New HMO-specific section
    type: ACROFORM
    templatePath: forms/hmo/pcp-selection.pdf
    order: 4

---
# State variant: California HMO
templateId: enrollment-hmo-ca-v1
baseTemplateId: enrollment-hmo-v1
metadata:
  productType: HMO
  state: CA
sectionOverrides:
  authorization: templates/state/ca/authorization.ftl  # CA-specific authorization
sections:
  - sectionId: ca-disclosure  # California-specific disclosure
    type: FREEMARKER
    templatePath: templates/state/ca/disclosure.ftl
    order: 9

---
# Market variant: Small Group California HMO
templateId: enrollment-hmo-ca-small-group-v1
baseTemplateId: enrollment-hmo-ca-v1
metadata:
  productType: HMO
  state: CA
  marketCategory: SMALL_GROUP
sections:
  - sectionId: employer-info  # Small group needs employer info
    type: ACROFORM
    templatePath: forms/small-group/employer.pdf
    order: 0
excludedSections:
  - dependent-info  # Small group uses different dependent section
sections:
  - sectionId: employee-dependent-info
    type: ACROFORM
    templatePath: forms/small-group/employee-dependents.pdf
    order: 2
```

### Field Mapping Composition

```java
// Field mapping with inheritance
interface FieldMappingSpec {
    String getSpecId();
    String getBaseSpecId();  // Inherit from base mapping
    Map<String, String> getFieldMappings();
    Map<String, String> getFieldOverrides();  // Override inherited mappings
}

@Component
class CompositeFieldMappingBuilder {
    public Map<String, String> buildFieldMapping(String specId) {
        FieldMappingSpec spec = loadSpec(specId);
        Map<String, String> mappings = new HashMap<>();
        
        // Recursively get base mappings
        if (spec.getBaseSpecId() != null) {
            mappings.putAll(buildFieldMapping(spec.getBaseSpecId()));
        }
        
        // Add/override with current spec
        mappings.putAll(spec.getFieldMappings());
        mappings.putAll(spec.getFieldOverrides());
        
        return mappings;
    }
}

// Example field mapping hierarchy
{
  "specId": "enrollment-base-mapping-v1",
  "fieldMappings": {
    "applicant.firstName": "$.applicant.personalInfo.firstName",
    "applicant.lastName": "$.applicant.personalInfo.lastName",
    "applicant.ssn": "$.applicant.personalInfo.ssn",
    "applicant.dob": "$.applicant.personalInfo.dateOfBirth"
  }
}

{
  "specId": "enrollment-ca-mapping-v1",
  "baseSpecId": "enrollment-base-mapping-v1",
  "fieldMappings": {
    "ca.disclosure.signed": "$.applicant.disclosures.california.signed",
    "ca.disclosure.date": "$.applicant.disclosures.california.signedDate"
  },
  "fieldOverrides": {
    "applicant.ssn": "$.applicant.personalInfo.maskedSSN"  // CA requires masking
  }
}
```

### Template Selection Strategy

```java
@Component
class TemplateSelector {
    private CompositeTemplateBuilder templateBuilder;
    
    public DocumentTemplate selectTemplate(EnrollmentRequest request) {
        // Build template ID from request attributes
        String templateId = buildTemplateId(
            request.getProductType(),
            request.getState(),
            request.getMarketCategory(),
            request.getSpecialScenario()
        );
        
        // Try specific template first, fall back to more general
        return findBestMatchTemplate(templateId, request);
    }
    
    private String buildTemplateId(String product, String state, 
                                   String market, String scenario) {
        // Try most specific first
        List<String> candidates = Arrays.asList(
            String.format("enrollment-%s-%s-%s-%s-v1", product, state, market, scenario),
            String.format("enrollment-%s-%s-%s-v1", product, state, market),
            String.format("enrollment-%s-%s-v1", product, state),
            String.format("enrollment-%s-v1", product),
            "enrollment-base-v1"
        );
        
        for (String candidate : candidates) {
            if (templateExists(candidate)) {
                return candidate;
            }
        }
        
        return "enrollment-base-v1";  // Ultimate fallback
    }
    
    private DocumentTemplate findBestMatchTemplate(String templateId, 
                                                    EnrollmentRequest request) {
        DocumentTemplate template = templateBuilder.buildTemplate(templateId);
        
        // Validate template matches request criteria
        validateTemplateMetadata(template, request);
        
        return template;
    }
}
```

### Benefits

1. **Reduced Template Count**: 600 templates → ~60 base + variant templates
2. **Easier Maintenance**: Update base template affects all variants
3. **Consistent Behavior**: Common sections behave identically across products
4. **Override Flexibility**: State-specific or product-specific customizations
5. **Clear Hierarchy**: Visual representation of template relationships

### Template Hierarchy Example

```
enrollment-base-v1
├── enrollment-hmo-v1
│   ├── enrollment-hmo-ca-v1
│   │   ├── enrollment-hmo-ca-individual-v1
│   │   └── enrollment-hmo-ca-small-group-v1
│   └── enrollment-hmo-ny-v1
├── enrollment-ppo-v1
│   ├── enrollment-ppo-ca-v1
│   └── enrollment-ppo-tx-v1
└── enrollment-epo-v1
```

### Usage in API

```java
@PostMapping("/generate")
public ResponseEntity<byte[]> generate(@RequestBody EnrollmentDocumentRequest request) {
    // Select appropriate template based on enrollment attributes
    DocumentTemplate template = templateSelector.selectTemplate(request);
    
    // Build composite template with all inherited sections
    DocumentTemplate compositeTemplate = templateBuilder.buildTemplate(template.getTemplateId());
    
    // Generate document
    DocumentGenerationRequest genRequest = DocumentGenerationRequest.builder()
        .template(compositeTemplate)
        .data(request.getEnrollmentData())
        .build();
    
    byte[] pdf = documentComposer.generateDocument(genRequest);
    return ResponseEntity.ok(pdf);
}

// Request example
{
  "productType": "HMO",
  "state": "CA",
  "marketCategory": "SMALL_GROUP",
  "enrollmentData": {
    "applicant": {...},
    "employer": {...}
  }
}
// Automatically selects: enrollment-hmo-ca-small-group-v1
// Which inherits from: enrollment-hmo-ca-v1 → enrollment-hmo-v1 → enrollment-base-v1
```

## Key Design Patterns

1. **Strategy Pattern**: Different renderers for different section types
2. **Template Method**: Common document generation flow with customizable steps
3. **Builder Pattern**: For constructing complex document templates
4. **Factory Pattern**: For creating appropriate renderers
5. **Composite Pattern**: For nested sections/pages and template inheritance
6. **Registry Pattern**: For managing custom PDFBox components
7. **Chain of Responsibility**: For template selection fallback hierarchy

## Template Definition Formats

The system supports both JSON and YAML for template definitions:

### JSON Format
```json
{
  "templateId": "invoice-template-v1",
  "sections": [
    {
      "sectionId": "company-header",
      "type": "FREEMARKER",
      "order": 1,
      "templatePath": "templates/invoice/header.ftl"
    }
  ]
}
```

### YAML Format
```yaml
templateId: invoice-template-v1
sections:
  - sectionId: company-header
    type: FREEMARKER
    order: 1
    templatePath: templates/invoice/header.ftl
```

### Template Loader

```java
@Component
class TemplateLoader {
    private ObjectMapper jsonMapper;
    private Yaml yamlParser;
    
    public DocumentTemplate loadTemplate(String templatePath) {
        if (templatePath.endsWith(".json")) {
            return loadFromJson(templatePath);
        } else if (templatePath.endsWith(".yaml") || templatePath.endsWith(".yml")) {
            return loadFromYaml(templatePath);
        }
        throw new UnsupportedTemplateFormatException(templatePath);
    }
    
    private DocumentTemplate loadFromJson(String path) {
        return jsonMapper.readValue(new File(path), DocumentTemplate.class);
    }
    
    private DocumentTemplate loadFromYaml(String path) {
        Constructor constructor = new Constructor(DocumentTemplate.class, new LoaderOptions());
        Yaml yaml = new Yaml(constructor);
        return yaml.load(new FileInputStream(path));
    }
}
```

## Overflow Handling for Repeating Data

### Problem: Limited Form Slots vs. Variable Data

Healthcare enrollment forms often have **fixed slots** for repeating data:
- AcroForm PDF with **3 slots** for child applicants, but applicant has **5 children**
- Form with **1 slot** for prior coverage, but applicant has **3 prior coverages**
- **Overflow data** (items 4-5, or items 2-3) must appear on addendum pages

### Solution: Dynamic Overflow Detection & Addendum Generation

```java
// Enhanced PageSection with overflow configuration
interface PageSection {
    String getSectionId();
    SectionType getType();
    String getTemplatePath();
    Map<String, String> getFieldMappings();
    int getOrder();
    
    // Overflow handling
    OverflowConfig getOverflowConfig();
}

// Overflow configuration
class OverflowConfig {
    private boolean enabled;
    private String dataPath;  // JSONPath to repeating data
    private int maxSlots;     // Max items that fit in main form
    private OverflowStrategy strategy;
    private String addendumTemplatePath;  // Template for overflow pages
    private int itemsPerAddendumPage;
}

enum OverflowStrategy {
    CONTINUATION_SHEET,    // Generate continuation sheets for overflow
    DYNAMIC_FORM,          // Duplicate form pages for all items
    ADDENDUM_TABLE,        // Render overflow as table in addendum
    FREEMARKER_TEMPLATE    // Use FreeMarker template for overflow
}
```

### Strategy 1: Continuation Sheet (Most Common)

```yaml
# Main enrollment form section
sectionId: dependent-info
type: ACROFORM
templatePath: forms/common/dependents.pdf
order: 2
fieldMappings:
  dependent1.firstName: $.dependents[0].firstName
  dependent1.lastName: $.dependents[0].lastName
  dependent1.dob: $.dependents[0].dateOfBirth
  dependent2.firstName: $.dependents[1].firstName
  dependent2.lastName: $.dependents[1].lastName
  dependent2.dob: $.dependents[1].dateOfBirth
  dependent3.firstName: $.dependents[2].firstName
  dependent3.lastName: $.dependents[2].lastName
  dependent3.dob: $.dependents[2].dateOfBirth
overflowConfig:
  enabled: true
  dataPath: $.dependents
  maxSlots: 3
  strategy: CONTINUATION_SHEET
  addendumTemplatePath: forms/common/dependents-continuation.pdf
  itemsPerAddendumPage: 5  # Continuation sheet has 5 slots
```

### Overflow Renderer Implementation

```java
@Component
class AcroFormRendererWithOverflow implements SectionRenderer {
    private OverflowHandler overflowHandler;
    private AcroFormRenderer baseRenderer;
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        OverflowConfig config = section.getOverflowConfig();
        
        if (config == null || !config.isEnabled()) {
            // No overflow handling, render normally
            return baseRenderer.render(section, context);
        }
        
        // Detect overflow
        OverflowResult overflowResult = overflowHandler.detectOverflow(
            section, 
            context.getData(), 
            config
        );
        
        if (!overflowResult.hasOverflow()) {
            // All data fits in main form
            return baseRenderer.render(section, context);
        }
        
        // Render main form with first N items
        PDDocument mainDoc = renderMainFormWithPartialData(
            section, 
            overflowResult.getMainFormData(), 
            context
        );
        
        // Render addendum pages for overflow items
        List<PDDocument> addendumDocs = renderAddendumPages(
            config,
            overflowResult.getOverflowData(),
            context
        );
        
        // Merge main + addendum pages
        return mergeDocuments(mainDoc, addendumDocs);
    }
    
    private PDDocument renderMainFormWithPartialData(
            PageSection section, 
            Map<String, Object> partialData,
            RenderContext context) {
        
        // Create new context with only the data that fits
        RenderContext partialContext = new RenderContext(
            context.getTemplate(), 
            partialData
        );
        
        // Add overflow indicator flag to main form
        partialContext.setMetadata("hasOverflow", true);
        partialContext.setMetadata("continuedOnPage", "See Addendum");
        
        return baseRenderer.render(section, partialContext);
    }
    
    private List<PDDocument> renderAddendumPages(
            OverflowConfig config,
            List<Map<String, Object>> overflowItems,
            RenderContext context) {
        
        List<PDDocument> addendumDocs = new ArrayList<>();
        int itemsPerPage = config.getItemsPerAddendumPage();
        
        // Partition overflow items into pages
        List<List<Map<String, Object>>> pages = 
            Lists.partition(overflowItems, itemsPerPage);
        
        for (int pageNum = 0; pageNum < pages.size(); pageNum++) {
            List<Map<String, Object>> pageItems = pages.get(pageNum);
            
            PDDocument addendumDoc = renderAddendumPage(
                config.getAddendumTemplatePath(),
                pageItems,
                pageNum + 1,
                pages.size(),
                context
            );
            
            addendumDocs.add(addendumDoc);
        }
        
        return addendumDocs;
    }
}

// Overflow detection and data partitioning
@Component
class OverflowHandler {
    private JsonPathEvaluator jsonPathEvaluator;
    
    public OverflowResult detectOverflow(
            PageSection section,
            Map<String, Object> data,
            OverflowConfig config) {
        
        // Extract repeating data using JSONPath
        Object repeatingData = jsonPathEvaluator.evaluate(
            data, 
            config.getDataPath()
        );
        
        if (!(repeatingData instanceof List)) {
            return OverflowResult.noOverflow();
        }
        
        List<?> items = (List<?>) repeatingData;
        int maxSlots = config.getMaxSlots();
        
        if (items.size() <= maxSlots) {
            // All items fit in main form
            return OverflowResult.noOverflow();
        }
        
        // Partition data: first N items vs. overflow items
        List<?> mainItems = items.subList(0, maxSlots);
        List<?> overflowItems = items.subList(maxSlots, items.size());
        
        // Create separate data maps
        Map<String, Object> mainFormData = createDataWithItems(
            data, 
            config.getDataPath(), 
            mainItems
        );
        
        return OverflowResult.builder()
            .hasOverflow(true)
            .totalItems(items.size())
            .mainFormData(mainFormData)
            .overflowData(convertToMaps(overflowItems))
            .overflowCount(overflowItems.size())
            .build();
    }
    
    private Map<String, Object> createDataWithItems(
            Map<String, Object> originalData,
            String dataPath,
            List<?> items) {
        
        // Deep copy original data
        Map<String, Object> newData = deepCopy(originalData);
        
        // Replace the repeating data with limited items
        jsonPathEvaluator.set(newData, dataPath, items);
        
        return newData;
    }
}

// Result object
@Data
@Builder
class OverflowResult {
    private boolean hasOverflow;
    private int totalItems;
    private int overflowCount;
    private Map<String, Object> mainFormData;  // Data for main form
    private List<Map<String, Object>> overflowData;  // Overflow items
    
    public static OverflowResult noOverflow() {
        return OverflowResult.builder()
            .hasOverflow(false)
            .build();
    }
}
```

### Strategy 2: FreeMarker Addendum (Flexible Layout)

```yaml
sectionId: prior-coverage
type: ACROFORM
templatePath: forms/common/prior-coverage.pdf
order: 5
fieldMappings:
  coverage1.carrier: $.applicant.priorCoverages[0].carrierName
  coverage1.policyNumber: $.applicant.priorCoverages[0].policyNumber
  coverage1.endDate: $.applicant.priorCoverages[0].endDate
overflowConfig:
  enabled: true
  dataPath: $.applicant.priorCoverages
  maxSlots: 1
  strategy: FREEMARKER_TEMPLATE
  addendumTemplatePath: templates/addendum/prior-coverage-overflow.ftl
```

FreeMarker addendum template:
```html
<!-- templates/addendum/prior-coverage-overflow.ftl -->
<!DOCTYPE html>
<html>
<head>
    <style>
        table { width: 100%; border-collapse: collapse; }
        th, td { border: 1px solid #000; padding: 8px; }
        .header { font-weight: bold; background-color: #f0f0f0; }
    </style>
</head>
<body>
    <h2>Addendum: Additional Prior Coverages</h2>
    <p>Applicant: ${applicant.firstName} ${applicant.lastName}</p>
    
    <table>
        <thead>
            <tr class="header">
                <th>Carrier Name</th>
                <th>Policy Number</th>
                <th>Coverage Type</th>
                <th>Start Date</th>
                <th>End Date</th>
            </tr>
        </thead>
        <tbody>
            <#list overflowItems as coverage>
            <tr>
                <td>${coverage.carrierName}</td>
                <td>${coverage.policyNumber}</td>
                <td>${coverage.coverageType}</td>
                <td>${coverage.startDate}</td>
                <td>${coverage.endDate}</td>
            </tr>
            </#list>
        </tbody>
    </table>
    
    <p><em>Continued from main enrollment form</em></p>
</body>
</html>
```

### Strategy 3: Dynamic Form Duplication

For scenarios where you want **all dependents on identical forms**:

```java
@Component
class DynamicFormRenderer implements SectionRenderer {
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        OverflowConfig config = section.getOverflowConfig();
        
        if (config.getStrategy() != OverflowStrategy.DYNAMIC_FORM) {
            // Use other strategy
            return defaultRender(section, context);
        }
        
        // Get all items
        List<?> items = (List<?>) jsonPathEvaluator.evaluate(
            context.getData(), 
            config.getDataPath()
        );
        
        List<PDDocument> formPages = new ArrayList<>();
        
        // Render one form page per item
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> itemData = createDataForItem(
                context.getData(), 
                config.getDataPath(), 
                items.get(i),
                i
            );
            
            RenderContext itemContext = new RenderContext(
                context.getTemplate(), 
                itemData
            );
            
            PDDocument formPage = baseRenderer.render(section, itemContext);
            formPages.add(formPage);
        }
        
        // Merge all form pages
        return mergeDocuments(formPages);
    }
}
```

### Real-World Example: Enrollment with Overflow

```java
// Request data
{
  "applicant": {
    "firstName": "John",
    "lastName": "Doe",
    "priorCoverages": [
      {"carrierName": "BlueCross", "policyNumber": "BC123"},
      {"carrierName": "Aetna", "policyNumber": "AET456"},  // OVERFLOW
      {"carrierName": "UHC", "policyNumber": "UHC789"}     // OVERFLOW
    ]
  },
  "dependents": [
    {"firstName": "Jane", "dob": "2000-01-01"},
    {"firstName": "Jack", "dob": "2002-02-02"},
    {"firstName": "Jill", "dob": "2004-03-03"},
    {"firstName": "Jim", "dob": "2006-04-04"},   // OVERFLOW
    {"firstName": "Jenny", "dob": "2008-05-05"}  // OVERFLOW
  ]
}

// Result PDF structure:
// Page 1: Main enrollment form
//   - Applicant info
//   - First 3 dependents (Jane, Jack, Jill)
//   - First prior coverage (BlueCross)
//   - Checkbox: "Additional dependents listed in Addendum" ✓
//   - Checkbox: "Additional coverages listed in Addendum" ✓
//
// Page 2: Dependent Continuation Sheet (Auto-generated)
//   - Dependent 4: Jim (2006-04-04)
//   - Dependent 5: Jenny (2008-05-05)
//
// Page 3: Prior Coverage Addendum (Auto-generated)
//   - Table with Aetna and UHC coverages
```

### Overflow Indication on Main Form

```java
// Add overflow indicators to main form fields
@Component
class OverflowIndicatorProcessor {
    
    public void addOverflowIndicators(
            PDDocument document, 
            OverflowResult overflowResult) {
        
        if (!overflowResult.isHasOverflow()) {
            return;
        }
        
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        
        // Check "Additional items in addendum" checkbox
        PDField overflowCheckbox = acroForm.getField("additionalItemsInAddendum");
        if (overflowCheckbox instanceof PDCheckBox) {
            ((PDCheckBox) overflowCheckbox).check();
        }
        
        // Fill overflow count field
        PDField overflowCountField = acroForm.getField("additionalItemCount");
        if (overflowCountField instanceof PDTextField) {
            ((PDTextField) overflowCountField).setValue(
                String.valueOf(overflowResult.getOverflowCount())
            );
        }
        
        // Add "Continued" note
        PDField continuedField = acroForm.getField("continuedNote");
        if (continuedField instanceof PDTextField) {
            ((PDTextField) continuedField).setValue(
                "Continued on Addendum - See attached pages"
            );
        }
    }
}
```

### Benefits

1. ✅ **Automatic Overflow Detection**: No manual data splitting required
2. ✅ **Multiple Strategies**: Choose continuation sheets, tables, or dynamic forms
3. ✅ **Seamless Integration**: Works with existing AcroForm renderer
4. ✅ **Flexible Templates**: Use AcroForms or FreeMarker for addendum
5. ✅ **Clear Indication**: Main form shows overflow indicators
6. ✅ **Regulatory Compliance**: All data captured in final PDF

### Configuration Example

```yaml
# Enrollment template with multiple overflow sections
templateId: enrollment-hmo-ca-v1
sections:
  - sectionId: applicant-info
    type: ACROFORM
    templatePath: forms/applicant.pdf
    order: 1
    
  - sectionId: dependent-info
    type: ACROFORM
    templatePath: forms/dependents.pdf
    order: 2
    overflowConfig:
      enabled: true
      dataPath: $.dependents
      maxSlots: 3
      strategy: CONTINUATION_SHEET
      addendumTemplatePath: forms/dependents-continuation.pdf
      itemsPerAddendumPage: 5
      
  - sectionId: prior-coverage
    type: ACROFORM
    templatePath: forms/prior-coverage.pdf
    order: 3
    overflowConfig:
      enabled: true
      dataPath: $.applicant.priorCoverages
      maxSlots: 1
      strategy: FREEMARKER_TEMPLATE
      addendumTemplatePath: templates/prior-coverage-addendum.ftl
      
  - sectionId: authorization
    type: FREEMARKER
    templatePath: templates/authorization.ftl
    order: 10
```

## Security Considerations

1. **Template Injection Prevention**: Sanitize FreeMarker templates
2. **File Access Control**: Restrict template file locations
3. **Resource Limits**: Set max pages, file size limits
4. **Validation**: Validate all input data before rendering

```java
@Component
class TemplateValidator {
    public void validate(DocumentTemplate template) {
        validateSectionCount(template);
        validateDataSize(template);
        validateTemplatePaths(template);
    }
}
```

## Error Handling

```java
class DocumentGenerationException extends RuntimeException {
    private String templateId;
    private String sectionId;
    private ErrorCode errorCode;
}

enum ErrorCode {
    TEMPLATE_NOT_FOUND,
    INVALID_SECTION_TYPE,
    RENDERING_FAILED,
    MERGE_FAILED,
    HEADER_FOOTER_FAILED
}
```

## Performance Optimization

1. **Template Caching**: Cache compiled FreeMarker templates
2. **Form Template Caching**: Cache loaded PDF forms
3. **Font Caching**: Cache loaded fonts for PDFBox
4. **Parallel Section Rendering**: Render independent sections in parallel
5. **Resource Pooling**: Pool PDDocument objects

```java
@Configuration
class DocumentGenerationConfig {
    @Bean
    public ExecutorService sectionRenderingExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
```

## Related Documentation

- [Section Renderers](02-section-renderers.md) - Detailed renderer implementations
- [Field Mapping Strategies](03-field-mapping-strategies.md) - Data mapping approaches
- [Headers and Footers](04-headers-footers.md) - Header/footer configuration
- [Storage Strategies](05-storage-strategies.md) - Template storage options
- [API Reference](07-api-reference.md) - REST API documentation

---
[← Back to Documentation Home](README.md) | [Next: Section Renderers →](02-section-renderers.md)
