# Overflow Handling Strategies for PDF Form Generation

## Problem Statement

When generating PDF forms from dynamic data, you may encounter scenarios where the data exceeds the capacity of the template form fields. For example:

**Scenario:** An enrollment form template has fields for:
- 1 PRIMARY applicant
- 1 SPOUSE applicant  
- 3 CHILD applicants (Child1, Child2, Child3)

**Challenge:** What happens when the actual data contains 6 children, but the template only has fields for 3?

This document outlines 4 strategies to handle overflow data scenarios.

---

## Strategy 1: Pre-Processing with Data Splitting

### Concept
Split the data into "main" and "overflow" portions **before** document generation. Generate separate documents and merge them.

### Implementation Approach

```java
public class OverflowPreProcessor {
    
    public OverflowResult preprocessData(Map<String, Object> data, OverflowConfig config) {
        List<Map<String, Object>> items = extractArray(data, config.getArrayPath());
        
        // Split into main and overflow
        List<Map<String, Object>> mainItems = items.subList(0, 
            Math.min(items.size(), config.getMaxItemsInMain()));
        List<Map<String, Object>> overflowItems = items.size() > config.getMaxItemsInMain() 
            ? items.subList(config.getMaxItemsInMain(), items.size()) 
            : Collections.emptyList();
        
        // Create separate data sets
        Map<String, Object> mainData = new HashMap<>(data);
        setArray(mainData, config.getArrayPath(), mainItems);
        
        Map<String, Object> overflowData = new HashMap<>(data);
        setArray(overflowData, config.getArrayPath(), overflowItems);
        
        return new OverflowResult(mainData, overflowData);
    }
}
```

### Configuration Example

```java
OverflowConfig config = OverflowConfig.builder()
    .arrayPath("$.applicants[?(@.applicantType=='CHILD')]")
    .maxItemsInMain(3)
    .itemsPerOverflowPage(3)
    .build();
```

### Usage

```java
// 1. Pre-process data
OverflowResult result = preprocessor.preprocessData(enrollmentData, config);

// 2. Generate main document
byte[] mainPdf = documentComposer.generateDocument(
    DocumentGenerationRequest.builder()
        .templateId("templates/enrollment-main.yaml")
        .data(result.getMainData())
        .build()
);

// 3. Generate overflow document (if needed)
byte[] overflowPdf = result.hasOverflow() 
    ? documentComposer.generateDocument(
        DocumentGenerationRequest.builder()
            .templateId("templates/child-addendum.yaml")
            .data(result.getOverflowData())
            .build()
    )
    : null;

// 4. Merge PDFs
byte[] finalPdf = mergePdfs(mainPdf, overflowPdf);
```

### Pros
- ✅ Clean separation of concerns
- ✅ Reusable for any template
- ✅ Easy to understand and maintain
- ✅ Data structure remains unchanged

### Cons
- ❌ Requires separate template for overflow pages
- ❌ Manual merging step required
- ❌ More boilerplate code

### Best For
- Simple overflow scenarios
- When you have full control over templates
- When overflow is predictable

---

## Strategy 2: Automatic Overflow Composer

### Concept
Automatically detect overflow, generate addendum pages, and merge PDFs in a single method call.

### Implementation Approach

```java
public class OverflowDocumentComposer {
    
    public byte[] generateWithOverflow(String templateId, 
                                       Map<String, Object> data,
                                       OverflowConfig config) {
        
        // Check for overflow
        if (!hasOverflow(data, config)) {
            return documentComposer.generateDocument(
                DocumentGenerationRequest.builder()
                    .templateId(templateId)
                    .data(data)
                    .build()
            );
        }
        
        // Generate main document
        Map<String, Object> mainData = createMainData(data, config);
        byte[] mainPdf = documentComposer.generateDocument(
            DocumentGenerationRequest.builder()
                .templateId(templateId)
                .data(mainData)
                .build()
        );
        
        // Generate overflow pages
        List<byte[]> overflowPages = generateOverflowPages(data, config);
        
        // Merge all PDFs
        return mergePdfs(mainPdf, overflowPages);
    }
    
    private List<byte[]> generateOverflowPages(Map<String, Object> data, 
                                                OverflowConfig config) {
        List<Map<String, Object>> items = extractArray(data, config.getArrayPath());
        List<Map<String, Object>> overflowItems = items.subList(
            config.getMaxItemsInMain(), items.size()
        );
        
        List<byte[]> pages = new ArrayList<>();
        
        // Chunk overflow items by page capacity
        for (int i = 0; i < overflowItems.size(); i += config.getItemsPerOverflowPage()) {
            int end = Math.min(i + config.getItemsPerOverflowPage(), overflowItems.size());
            List<Map<String, Object>> pageItems = overflowItems.subList(i, end);
            
            Map<String, Object> pageData = new HashMap<>(data);
            setArray(pageData, config.getArrayPath(), pageItems);
            
            byte[] pagePdf = documentComposer.generateDocument(
                DocumentGenerationRequest.builder()
                    .templateId(config.getAddendumTemplatePath())
                    .data(pageData)
                    .build()
            );
            
            pages.add(pagePdf);
        }
        
        return pages;
    }
}
```

### Configuration Example

```java
OverflowConfig config = OverflowConfig.builder()
    .arrayPath("$.applicants[?(@.applicantType=='CHILD')]")
    .maxItemsInMain(3)
    .itemsPerOverflowPage(3)
    .addendumTemplatePath("templates/child-addendum.yaml")
    .build();
```

### Usage

```java
// Single method call handles everything
byte[] finalPdf = overflowComposer.generateWithOverflow(
    "templates/enrollment-main.yaml",
    enrollmentData,
    config
);
```

### Pros
- ✅ Single method call - very simple to use
- ✅ Automatic overflow detection
- ✅ Automatic page calculation
- ✅ Handles N overflow pages automatically

### Cons
- ❌ More complex implementation
- ❌ Requires separate addendum template
- ❌ Less control over merge process

### Best For
- Repeated overflow scenarios
- When overflow count is highly variable (3, 10, 20+ children)
- When you want to abstract complexity
- Production systems with many overflow cases

---

## Strategy 3: Manual YAML Slicing with Array Indexing

### Concept
Use YAML template mappings to explicitly slice the data array into main and overflow portions using JSONPath array indexes.

### Implementation: Main Template

```yaml
templateId: enrollment-main
sections:
  - sectionId: enrollment-form
    type: ACROFORM
    templatePath: templates/forms/applicant-form.pdf
    fieldMappingGroups:
      
      # Primary and Spouse (always in main form)
      - mappingType: JSONPATH
        fields:
          PrimaryFirstName: "$.applicants[?(@.applicantType=='PRIMARY')].firstName"
          PrimaryLastName: "$.applicants[?(@.applicantType=='PRIMARY')].lastName"
          SpouseFirstName: "$.applicants[?(@.applicantType=='SPOUSE')].firstName"
          SpouseLastName: "$.applicants[?(@.applicantType=='SPOUSE')].lastName"
      
      # First 3 children only - using array index slicing [0], [1], [2]
      - mappingType: CUSTOM
        functionName: identity
        fields:
          # Child 1 (index 0)
          Child1FirstName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][0].firstName"
          Child1LastName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][0].lastName"
          Child1DOB: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][0].dateOfBirth"
          
          # Child 2 (index 1)
          Child2FirstName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][1].firstName"
          Child2LastName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][1].lastName"
          Child2DOB: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][1].dateOfBirth"
          
          # Child 3 (index 2)
          Child3FirstName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][2].firstName"
          Child3LastName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][2].lastName"
          Child3DOB: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][2].dateOfBirth"
      
      # Overflow indicator
      - mappingType: DIRECT
        fields:
          OverflowNote: "See attached addendum for additional dependents"
```

### Implementation: Addendum Template

```yaml
templateId: child-addendum
sections:
  - sectionId: addendum-page
    type: ACROFORM
    templatePath: templates/forms/applicant-form.pdf
    fieldMappingGroups:
      
      # Header with policy info
      - mappingType: DIRECT
        fields:
          PolicyNumber: "policyNumber"
          PageTitle: "Addendum - Additional Dependents"
      
      # Children 4-6 using indexes [3], [4], [5]
      - mappingType: CUSTOM
        functionName: identity
        fields:
          # Child 4 (index 3 - 4th child)
          Child1FirstName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][3].firstName"
          Child1LastName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][3].lastName"
          Child1DOB: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][3].dateOfBirth"
          
          # Child 5 (index 4 - 5th child)
          Child2FirstName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][4].firstName"
          Child2LastName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][4].lastName"
          Child2DOB: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][4].dateOfBirth"
          
          # Child 6 (index 5 - 6th child)
          Child3FirstName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][5].firstName"
          Child3LastName: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][5].lastName"
          Child3DOB: "jsonpath:$.applicants[?(@.applicantType=='CHILD')][5].dateOfBirth"
```

### Usage

```java
// 1. Generate main form (children 0-2)
byte[] mainPdf = documentComposer.generateDocument(
    DocumentGenerationRequest.builder()
        .templateId("templates/enrollment-main.yaml")
        .data(enrollmentData)
        .build()
);

// 2. Generate addendum (children 3-5)
byte[] addendumPdf = documentComposer.generateDocument(
    DocumentGenerationRequest.builder()
        .templateId("templates/child-addendum.yaml")
        .data(enrollmentData)
        .build()
);

// 3. Merge PDFs
byte[] finalPdf = mergePdfs(mainPdf, addendumPdf);
```

### Pros
- ✅ Pure declarative YAML - no Java code needed
- ✅ Clear which data goes where
- ✅ Uses existing CUSTOM mapping strategy
- ✅ Leverages JSONPath array indexing
- ✅ Very explicit and auditable

### Cons
- ❌ Hardcoded array indexes (limited to fixed count)
- ❌ Cannot handle variable overflow (e.g., 10+ children without creating more templates)
- ❌ Requires manual PDF merging
- ❌ Template maintenance overhead

### Best For
- Fixed overflow capacity (e.g., always max 6 children)
- When you want pure declarative configuration
- When auditability is critical
- Simple, predictable scenarios

---

## Strategy 4: Conditional Overflow Indicators

### Concept
Add conditional fields to the main form that indicate overflow exists and provide summary information.

### Implementation

```yaml
templateId: enrollment-with-indicators
sections:
  - sectionId: enrollment-form
    type: ACROFORM
    templatePath: templates/forms/applicant-form.pdf
    fieldMappingGroups:
      
      # Regular fields (first 3 children)
      - mappingType: JSONPATH
        fields:
          Child1Name: "$.applicants[?(@.applicantType=='CHILD')][0].firstName"
          Child2Name: "$.applicants[?(@.applicantType=='CHILD')][1].firstName"
          Child3Name: "$.applicants[?(@.applicantType=='CHILD')][2].firstName"
      
      # Overflow indicators using CUSTOM transformations
      - mappingType: CUSTOM
        functionName: calculateChildCount
        fields:
          TotalChildCount: "jsonpath:$.applicants[?(@.applicantType=='CHILD')]"
          
      - mappingType: CUSTOM
        functionName: hasOverflow
        fields:
          HasAdditionalChildren: "jsonpath:$.applicants[?(@.applicantType=='CHILD')]"
          
      - mappingType: CUSTOM  
        functionName: getOverflowNames
        fields:
          AdditionalChildrenNames: "jsonpath:$.applicants[?(@.applicantType=='CHILD')]"
```

### Custom Functions

```java
public class OverflowTransformations {
    
    public static String calculateChildCount(List<Map<String, Object>> children) {
        return String.valueOf(children.size());
    }
    
    public static String hasOverflow(List<Map<String, Object>> children) {
        return children.size() > 3 ? "Yes - See Addendum" : "No";
    }
    
    public static String getOverflowNames(List<Map<String, Object>> children) {
        if (children.size() <= 3) return "";
        
        return children.stream()
            .skip(3)
            .map(child -> child.get("firstName") + " " + child.get("lastName"))
            .collect(Collectors.joining(", "));
    }
}
```

### Result on PDF Form

```
Children Listed on This Form: 3
Total Children: 6
Additional Children: Yes - See Addendum
Additional Children Names: Oliver Smith, Ava Smith, Liam Smith
```

### Pros
- ✅ User-friendly - clearly indicates overflow
- ✅ Provides summary on main form
- ✅ No additional templates needed for indicators
- ✅ Good for compliance/audit requirements

### Cons
- ❌ Still need addendum pages for full data
- ❌ Indicators only - not a complete solution
- ❌ Requires custom transformation functions

### Best For
- Compliance requirements (must show total count)
- User experience (clear indication of additional pages)
- Used **in combination** with other strategies
- Audit trails

---

## Strategy Comparison Matrix

| Feature | Strategy 1<br/>Pre-Processing | Strategy 2<br/>Auto Composer | Strategy 3<br/>YAML Slicing | Strategy 4<br/>Indicators |
|---------|------------------------------|------------------------------|----------------------------|--------------------------|
| **Ease of Use** | Medium | ⭐ Easy | Medium | Easy |
| **Setup Complexity** | Low | High | ⭐ Low | Medium |
| **Variable Overflow** | ✅ Yes | ⭐ Yes | ❌ No | ✅ Yes |
| **Code Required** | Medium | High | ⭐ None (YAML only) | Medium |
| **Template Changes** | New addendum | New addendum | New addendum | ⭐ None |
| **Performance** | ⭐ Fast | Medium | ⭐ Fast | ⭐ Fast |
| **Auditability** | Good | Medium | ⭐ Excellent | Good |
| **Flexibility** | ⭐ High | ⭐ High | Low | Medium |
| **Best For** | Simple cases | Complex cases | Fixed capacity | UX/Compliance |

---

## Recommended Approach by Scenario

### Scenario 1: Simple Enrollment (≤6 children)
**Recommendation:** **Strategy 3 - Manual YAML Slicing**
- Most straightforward
- Pure configuration, no code
- Perfect for fixed capacity

### Scenario 2: Variable Enrollment (unknown child count)
**Recommendation:** **Strategy 2 - Auto Composer**
- Handles any number of children
- Automatic page calculation
- Clean API

### Scenario 3: Compliance-Driven
**Recommendation:** **Strategy 1 + Strategy 4**
- Pre-process for clean separation
- Add indicators for audit trail
- Clear documentation trail

### Scenario 4: High Performance Requirements
**Recommendation:** **Strategy 1 - Pre-Processing**
- Minimal overhead
- Simple merge logic
- Predictable performance

---

## PDF Merging with PDFBox

All strategies (except Strategy 4) require merging PDFs. Here's the standard approach:

```java
public byte[] mergePdfs(byte[] mainPdf, byte[] addendumPdf) throws Exception {
    try (PDDocument mainDoc = Loader.loadPDF(mainPdf);
         PDDocument addendumDoc = Loader.loadPDF(addendumPdf);
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.appendDocument(mainDoc, addendumDoc);
        mainDoc.save(output);
        
        return output.toByteArray();
    }
}
```

### Merging Multiple Pages

```java
public byte[] mergePdfs(byte[] mainPdf, List<byte[]> addendumPages) throws Exception {
    try (PDDocument mainDoc = Loader.loadPDF(mainPdf);
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        
        PDFMergerUtility merger = new PDFMergerUtility();
        
        // Append each addendum page
        for (byte[] page : addendumPages) {
            try (PDDocument pageDoc = Loader.loadPDF(page)) {
                merger.appendDocument(mainDoc, pageDoc);
            }
        }
        
        mainDoc.save(output);
        return output.toByteArray();
    }
}
```

---

## Configuration Model

```java
@Data
@Builder
public class OverflowConfig {
    /**
     * JSONPath to the array that may overflow
     * Example: "$.applicants[?(@.applicantType=='CHILD')]"
     */
    private String arrayPath;
    
    /**
     * Maximum items to include in main document
     */
    private int maxItemsInMain;
    
    /**
     * Number of items per overflow/addendum page
     */
    private int itemsPerOverflowPage;
    
    /**
     * Template path for addendum pages
     */
    private String addendumTemplatePath;
    
    /**
     * Field name for overflow indicator (Strategy 4)
     */
    private String overflowIndicatorField;
}
```

---

## Testing Recommendations

### Test Data Structure

```json
{
  "policyNumber": "POL-2024-001",
  "applicants": [
    {"applicantType": "PRIMARY", "firstName": "John", "lastName": "Smith"},
    {"applicantType": "SPOUSE", "firstName": "Jane", "lastName": "Smith"},
    {"applicantType": "CHILD", "firstName": "Emily", "lastName": "Smith"},
    {"applicantType": "CHILD", "firstName": "Michael", "lastName": "Smith"},
    {"applicantType": "CHILD", "firstName": "Sophia", "lastName": "Smith"},
    {"applicantType": "CHILD", "firstName": "Oliver", "lastName": "Smith"},
    {"applicantType": "CHILD", "firstName": "Ava", "lastName": "Smith"},
    {"applicantType": "CHILD", "firstName": "Liam", "lastName": "Smith"}
  ]
}
```

### Test Cases

1. **No Overflow** (3 children)
   - Should generate single page
   - No addendum needed

2. **Minimal Overflow** (4 children)
   - Should generate 2 pages
   - 3 on main, 1 on addendum

3. **Full Overflow** (6 children)
   - Should generate 2 pages
   - 3 on main, 3 on addendum

4. **Multiple Overflow Pages** (10+ children)
   - Should generate 3+ pages
   - Test pagination logic

5. **Edge Cases**
   - 0 children
   - Exactly 3 children
   - Null/missing applicants array

---

## Performance Considerations

### Strategy Performance Comparison

| Metric | Strategy 1 | Strategy 2 | Strategy 3 | Strategy 4 |
|--------|-----------|-----------|-----------|-----------|
| Data Processing | ~1ms | ~2ms | 0ms | ~1ms |
| PDF Generation | N × 50ms | N × 50ms | N × 50ms | 1 × 50ms |
| PDF Merging | ~10ms | ~10ms | ~10ms | 0ms |
| **Total (3 pages)** | **~161ms** | **~162ms** | **~160ms** | **~51ms** |

**Note:** Strategy 4 is fastest but doesn't handle overflow data, only indicators.

---

## Implementation Checklist

### For Strategy 1 (Pre-Processing)
- [ ] Create `OverflowPreProcessor` class
- [ ] Create `OverflowConfig` model
- [ ] Create `OverflowResult` model
- [ ] Design addendum template
- [ ] Implement PDF merging utility
- [ ] Add unit tests for data splitting
- [ ] Add integration tests for full flow

### For Strategy 2 (Auto Composer)
- [ ] Create `OverflowDocumentComposer` class
- [ ] Implement overflow detection logic
- [ ] Implement page chunking algorithm
- [ ] Design addendum template
- [ ] Implement PDF merging with multiple pages
- [ ] Add configuration validation
- [ ] Add comprehensive tests

### For Strategy 3 (YAML Slicing)
- [ ] Design main template with array indexes
- [ ] Design addendum template with offset indexes
- [ ] Test JSONPath array indexing
- [ ] Implement PDF merging
- [ ] Document index mapping clearly
- [ ] Add tests for edge cases (missing indexes)

### For Strategy 4 (Indicators)
- [ ] Add indicator fields to template
- [ ] Implement custom transformation functions
- [ ] Register functions in `CustomMappingStrategy`
- [ ] Test with various overflow counts
- [ ] Combine with another strategy for complete solution

---

## Conclusion

Each overflow strategy serves different needs:

- **Strategy 1** is best for **simple, clean implementations**
- **Strategy 2** is best for **production systems with variable overflow**
- **Strategy 3** is best for **fixed capacity, pure configuration**
- **Strategy 4** is best for **user experience and compliance**

For most production systems, **Strategy 2 (Auto Composer)** provides the best balance of flexibility and ease of use. For simpler scenarios with predictable overflow, **Strategy 3 (YAML Slicing)** offers the clearest, most auditable approach.

Consider combining strategies - for example, Strategy 2 for automation plus Strategy 4 for user-facing indicators.
