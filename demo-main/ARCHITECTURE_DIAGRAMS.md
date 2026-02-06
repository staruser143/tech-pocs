# Field Mapping Strategy Architecture Diagram

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    HTTP POST /api/documents/generate             │
│                                                                  │
│  Request Body:                                                   │
│  {                                                               │
│    "templateId": "direct-mapping-example",                       │
│    "data": { ... }                                               │
│  }                                                               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              DocumentController.generateDocument()               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                  TemplateLoader.loadTemplate()                   │
│                                                                  │
│  Loads: direct-mapping-example.yaml                              │
│  ┌────────────────────────────────────────────────────────┐     │
│  │ templateId: direct-mapping-example                     │     │
│  │ sections:                                              │     │
│  │   - sectionId: applicant-info                          │     │
│  │     type: ACROFORM                                     │     │
│  │     mappingType: DIRECT    ◄── Strategy selector!     │     │
│  │     fieldMappings:                                     │     │
│  │       firstName: applicant.firstName                   │     │
│  │       email: applicant.contact.email                   │     │
│  └────────────────────────────────────────────────────────┘     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│               DocumentComposer.generateDocument()                │
│                                                                  │
│  For each section:                                               │
│    1. Find appropriate renderer (SectionRenderer)                │
│    2. Create RenderContext with data                             │
│    3. Call renderer.render(section, context)                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   AcroFormRenderer.render()                      │
│                                                                  │
│  1. Load PDF template                                            │
│  2. Get AcroForm from PDF                                        │
│  3. SELECT MAPPING STRATEGY based on section.mappingType        │
│  4. Map data to field values using strategy                      │
│  5. Fill form fields                                             │
│  6. Return PDDocument                                            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            │ Step 3: Strategy Selection
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│          FieldMappingStrategy strategy = findMappingStrategy()   │
│                                                                  │
│          if (mappingType == DIRECT)    → DirectMappingStrategy   │
│          if (mappingType == JSONPATH)  → JsonPathMappingStrategy │
│          if (mappingType == JSONATA)   → JsonataMappingStrategy  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            │ Step 4: Map Data
                            ▼
    ┌───────────────────────┴───────────────────────┐
    │                                               │
    ▼                         ▼                     ▼
┌─────────────┐       ┌──────────────┐      ┌──────────────┐
│   DIRECT    │       │   JSONPATH   │      │   JSONATA    │
│  Strategy   │       │   Strategy   │      │   Strategy   │
└─────────────┘       └──────────────┘      └──────────────┘
```

---

## Strategy Pattern Detail

```
┌──────────────────────────────────────────────────────────────┐
│                    FieldMappingStrategy                       │
│                      (Interface)                              │
│                                                               │
│  + mapData(sourceData, fieldMappings): Map<String, String>   │
│  + supports(MappingType): boolean                            │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         │ implements
         ┌───────────────┼───────────────┐
         │               │               │
         ▼               ▼               ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   DIRECT     │  │  JSONPATH    │  │   JSONATA    │
│   Strategy   │  │   Strategy   │  │   Strategy   │
│              │  │              │  │              │
│ @Component   │  │  @Component  │  │  @Component  │
└──────────────┘  └──────────────┘  └──────────────┘
```

---

## Data Flow Example: DIRECT Strategy

```
Input Data:
{
  "applicant": {
    "firstName": "John",
    "contact": {
      "email": "john@example.com"
    }
  }
}

Field Mappings:
{
  "firstName": "applicant.firstName",
  "email": "applicant.contact.email"
}

                    ┌─────────────────────────┐
                    │ DirectMappingStrategy   │
                    │                         │
                    │ mapData(data, mappings) │
                    └───────────┬─────────────┘
                                │
                    For each field mapping:
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐      ┌────────────────┐      ┌──────────────┐
│ firstName     │      │ email          │      │ ...          │
│ ↓             │      │ ↓              │      │              │
│ Navigate:     │      │ Navigate:      │      │              │
│ applicant     │      │ applicant      │      │              │
│   .firstName  │      │   .contact     │      │              │
│ ↓             │      │     .email     │      │              │
│ "John"        │      │ ↓              │      │              │
│               │      │ "john@e..."    │      │              │
└───────────────┘      └────────────────┘      └──────────────┘

Output:
{
  "firstName": "John",
  "email": "john@example.com"
}

This is passed to fillFormFields() which sets PDF field values
```

---

## Data Flow Example: JSONPATH Strategy

```
Input Data:
{
  "employee": {
    "name": "Jane",
    "projects": [
      {"name": "Alpha", "status": "Active"},
      {"name": "Beta", "status": "Completed"},
      {"name": "Gamma", "status": "Active"}
    ]
  }
}

Field Mappings:
{
  "employeeName": "$.employee.name",
  "activeProjects": "$.employee.projects[?(@.status == 'Active')].name"
}

                    ┌─────────────────────────┐
                    │ JsonPathMappingStrategy │
                    │                         │
                    │ mapData(data, mappings) │
                    └───────────┬─────────────┘
                                │
                    For each field mapping:
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       
┌───────────────┐      ┌────────────────────────┐      
│ employeeName  │      │ activeProjects         │      
│ ↓             │      │ ↓                      │      
│ JSONPath:     │      │ JSONPath:              │      
│ $.employee    │      │ $.employee.projects    │      
│   .name       │      │   [?(@.status=='Active')]│    
│ ↓             │      │ ↓                      │      
│ "Jane"        │      │ ["Alpha", "Gamma"]     │      
│               │      │ ↓ (convert array)      │      
│               │      │ "Alpha, Gamma"         │      
└───────────────┘      └────────────────────────┘      

Output:
{
  "employeeName": "Jane",
  "activeProjects": "Alpha, Gamma"
}
```

---

## Data Flow Example: JSONATA Strategy

```
Input Data:
{
  "customer": {
    "firstName": "Bob",
    "lastName": "Smith"
  },
  "items": [
    {"name": "Widget A", "price": 25, "quantity": 2},
    {"name": "Widget B", "price": 50, "quantity": 1}
  ]
}

Field Mappings:
{
  "fullName": "customer.firstName & ' ' & customer.lastName",
  "total": "$sum(items.(price * quantity))"
}

                    ┌─────────────────────────┐
                    │ JsonataMappingStrategy  │
                    │                         │
                    │ mapData(data, mappings) │
                    └───────────┬─────────────┘
                                │
                    For each field mapping:
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       
┌───────────────┐      ┌────────────────────────┐      
│ fullName      │      │ total                  │      
│ ↓             │      │ ↓                      │      
│ JSONata:      │      │ JSONata:               │      
│ customer.     │      │ $sum(                  │      
│   firstName   │      │   items.(              │      
│   & ' ' &     │      │     price * quantity   │      
│   lastName    │      │   )                    │      
│ ↓             │      │ )                      │      
│ "Bob" & " "   │      │ ↓                      │      
│   & "Smith"   │      │ items[0]: 25 * 2 = 50  │      
│ ↓             │      │ items[1]: 50 * 1 = 50  │      
│ "Bob Smith"   │      │ sum: 100               │      
│               │      │ ↓                      │      
│               │      │ "100"                  │      
└───────────────┘      └────────────────────────┘      

Output:
{
  "fullName": "Bob Smith",
  "total": "100"
}
```

---

## Spring Dependency Injection

```
┌─────────────────────────────────────────────────┐
│         Spring Application Context              │
└─────────────────────────────────────────────────┘
                    │
                    │ Component Scanning
                    │
    ┌───────────────┼───────────────┐
    │               │               │
    ▼               ▼               ▼
┌─────────┐   ┌─────────┐   ┌─────────┐
│ DIRECT  │   │JSONPATH │   │ JSONATA │
│@Component│  │@Component│  │@Component│
└─────────┘   └─────────┘   └─────────┘
    │               │               │
    └───────────────┼───────────────┘
                    │
                    │ Injected as List
                    ▼
┌─────────────────────────────────────────────────┐
│          AcroFormRenderer                       │
│                                                 │
│  @Component                                     │
│  public class AcroFormRenderer {                │
│                                                 │
│    private final List<FieldMappingStrategy>     │
│                 strategies;                     │
│                                                 │
│    public AcroFormRenderer(                     │
│        List<FieldMappingStrategy> strategies) { │
│      this.strategies = strategies;              │
│    }                                            │
│  }                                              │
└─────────────────────────────────────────────────┘
```

---

## Complete Request Flow

```
1. User sends HTTP POST
   ↓
2. DocumentController receives request
   ↓
3. TemplateLoader loads YAML
   ↓
   Parses: mappingType: DIRECT
   ↓
4. DocumentComposer creates RenderContext
   ↓
5. Finds AcroFormRenderer for ACROFORM type
   ↓
6. AcroFormRenderer.render() called
   ↓
7. Loads PDF template
   ↓
8. Calls findMappingStrategy(DIRECT)
   ↓
   Returns: DirectMappingStrategy
   ↓
9. Calls strategy.mapData(data, fieldMappings)
   ↓
   Returns: {"firstName": "John", "email": "..."}
   ↓
10. Calls fillFormFields(acroForm, mappedValues)
    ↓
    For each field:
      field.setValue(value)
    ↓
11. Returns PDDocument with filled form
    ↓
12. DocumentComposer returns PDF bytes
    ↓
13. Controller sends PDF response to user
```

---

## Configuration Flow

```
YAML Template File
┌────────────────────────────────────┐
│ templateId: my-template            │
│ sections:                          │
│   - sectionId: my-section          │
│     type: ACROFORM                 │
│     mappingType: DIRECT ◄───────┐  │
│     fieldMappings:              │  │
│       firstName: applicant.name │  │
└─────────────────────────────────┼──┘
                                  │
                    This controls │
                          strategy│selection
                                  │
                                  ▼
┌─────────────────────────────────────┐
│  PageSection Model                  │
│                                     │
│  private MappingType mappingType;   │
│                                     │
│  getMappingType() → DIRECT          │
└────────────────┬────────────────────┘
                 │
                 │ Used by renderer
                 ▼
┌─────────────────────────────────────┐
│  AcroFormRenderer                   │
│                                     │
│  strategy = findMappingStrategy(    │
│    section.getMappingType()         │
│  )                                  │
│                                     │
│  if (DIRECT) → DirectStrategy       │
│  if (JSONPATH) → JsonPathStrategy   │
│  if (JSONATA) → JsonataStrategy     │
└─────────────────────────────────────┘
```

---

## Key Design Decisions

### 1. Strategy Pattern
✅ Allows adding new strategies without modifying existing code
✅ Each strategy is independently testable
✅ Clear separation of concerns

### 2. YAML Configuration
✅ No code changes to switch strategies
✅ Per-section configuration
✅ Template designers control the approach

### 3. Spring Integration
✅ Automatic discovery of all strategies
✅ Dependency injection handles wiring
✅ Easy to add new strategies (just add @Component)

### 4. Backward Compatibility
✅ Default to JSONPATH if not specified
✅ Existing templates continue to work
✅ Gradual migration path

---

## Extension Points

Want to add a new strategy (e.g., Velocity expressions)?

```java
@Component
public class VelocityMappingStrategy implements FieldMappingStrategy {
    
    @Override
    public Map<String, String> mapData(
            Map<String, Object> sourceData,
            Map<String, String> fieldMappings) {
        // Your implementation
    }
    
    @Override
    public boolean supports(MappingType type) {
        return type == MappingType.VELOCITY;  // Add to enum
    }
}
```

That's it! Spring automatically discovers it.

---

## Summary

This architecture provides:
- **Flexibility**: 3 strategies for different needs
- **Configurability**: YAML-based strategy selection
- **Extensibility**: Easy to add new strategies
- **Performance**: Choose appropriate strategy per section
- **Maintainability**: Clear separation of concerns
- **Testability**: Each component independently testable
