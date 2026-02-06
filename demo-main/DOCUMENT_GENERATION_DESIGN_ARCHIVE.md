# Document Generation System Design

## Overview
A flexible PDF document generation system that supports multiple template types (FreeMarker, AcroForms, PDFBox components) with the ability to compose multi-page/multi-section documents with headers and footers.

## Architecture

### High-Level Architecture

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
│ FreeMarker     │  │  AcroForm   │  │  PDFBox Direct  │
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
    Map<String, Object> getData();
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

### 2. Section Renderer Strategy Pattern

```java
interface SectionRenderer {
    boolean supports(SectionType type);
    PDDocument render(PageSection section, RenderContext context);
}

class FreeMarkerSectionRenderer implements SectionRenderer {
    private Configuration freemarkerConfig;
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        // 1. Process FreeMarker template with data
        // 2. Convert HTML to PDF using OpenHtmlToPDF
        // 3. Return PDDocument
    }
}

class AcroFormSectionRenderer implements SectionRenderer {
    private FieldMappingStrategy mappingStrategy;
    private boolean flattenForms = true;
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        // 1. Load PDF form template
        PDDocument formDoc = loadFormTemplate(section.getFormTemplatePath());
        
        // 2. Map source data to form fields
        Map<String, String> fieldValues = mappingStrategy.mapData(
            section.getData(), 
            section.getFieldMappings()
        );
        
        // 3. Fill form fields with mapped data
        fillFormFields(formDoc, fieldValues);
        
        // 4. Flatten form (optional)
        if (flattenForms) {
            formDoc.getDocumentCatalog().getAcroForm().flatten();
        }
        
        // 5. Return PDDocument
        return formDoc;
    }
    
    private void fillFormFields(PDDocument document, Map<String, String> fieldValues) {
        try {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            for (Map.Entry<String, String> entry : fieldValues.entrySet()) {
                PDField field = acroForm.getField(entry.getKey());
                if (field != null) {
                    field.setValue(entry.getValue());
                }
            }
        } catch (IOException e) {
            throw new FormFillException("Failed to fill form fields", e);
        }
    }
}

class PdfBoxDirectSectionRenderer implements SectionRenderer {
    private ComponentRegistry componentRegistry;
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        // 1. Lookup component from registry
        // 2. Execute component rendering logic
        // 3. Return PDDocument
    }
}

class ExcelSectionRenderer implements SectionRenderer {
    private FieldMappingStrategy mappingStrategy;
    private ExcelToPdfConverter excelConverter;
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        // 1. Load Excel template (.xlsx or .xls)
        Workbook workbook = loadExcelTemplate(section.getTemplatePath());
        
        // 2. Map and populate data using cell references or named ranges
        populateExcelData(workbook, section.getData(), section.getFieldMappings());
        
        // 3. Evaluate formulas
        evaluateFormulas(workbook);
        
        // 4. Convert Excel to PDF
        PDDocument pdfDoc = excelConverter.convert(workbook);
        
        // 5. Return PDDocument
        return pdfDoc;
    }
    
    private void populateExcelData(Workbook workbook, Map<String, Object> data,
                                   FieldMappingConfig mappings) {
        Map<String, Object> cellValues = mappingStrategy.mapData(data, mappings);
        
        for (Map.Entry<String, Object> entry : cellValues.entrySet()) {
            String cellRef = entry.getKey();
            Object value = entry.getValue();
            
            if (cellRef.contains("!")) {
                // Sheet!CellRef format (e.g., "Sheet1!A1")
                String[] parts = cellRef.split("!");
                Sheet sheet = workbook.getSheet(parts[0]);
                setCellValue(sheet, parts[1], value);
            } else {
                // Named range
                Name namedRange = workbook.getName(cellRef);
                if (namedRange != null) {
                    setCellValueByNamedRange(workbook, namedRange, value);
                }
            }
        }
    }
    
    private void setCellValue(Sheet sheet, String cellRef, Object value) {
        CellReference ref = new CellReference(cellRef);
        Row row = sheet.getRow(ref.getRow());
        if (row == null) row = sheet.createRow(ref.getRow());
        
        Cell cell = row.getCell(ref.getCol());
        if (cell == null) cell = row.createCell(ref.getCol());
        
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }
    
    private void evaluateFormulas(Workbook workbook) {
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        evaluator.evaluateAll();
    }
}
```

## AcroForm Field Mapping Strategies

When filling AcroForm PDF templates, you need to map source data to PDF form field names. Multiple strategies can be employed based on complexity and data structure.

### Field Mapping Strategy Interface

```java
interface FieldMappingStrategy {
    Map<String, String> mapData(Map<String, Object> sourceData, 
                                FieldMappingConfig mappingConfig);
}

class FieldMappingConfig {
    private MappingType mappingType;
    private Map<String, String> fieldMappings;  // For DIRECT and JSONPATH
    private String transformationScript;         // For JSONATA
}

enum MappingType {
    DIRECT,      // Direct Java Map/List access
    JSONPATH,    // JSONPath expressions
    JSONATA      // JSONata transformations
}
```

### 1. Direct Access Strategy (Java Maps/Lists)

**Simple, fast, no dependencies. Best for flat or simple nested structures.**

```java
@Component
class DirectMappingStrategy implements FieldMappingStrategy {
    
    @Override
    public Map<String, String> mapData(Map<String, Object> sourceData, 
                                      FieldMappingConfig config) {
        Map<String, String> result = new HashMap<>();
        
        for (Map.Entry<String, String> mapping : config.getFieldMappings().entrySet()) {
            String formFieldName = mapping.getKey();
            String sourceFieldPath = mapping.getValue();
            
            Object value = getNestedValue(sourceData, sourceFieldPath);
            result.put(formFieldName, convertToString(value));
        }
        
        return result;
    }
    
    private Object getNestedValue(Map<String, Object> data, String path) {
        String[] parts = path.split("\\.");
        Object current = data;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else if (current instanceof List && part.matches("\\d+")) {
                current = ((List<?>) current).get(Integer.parseInt(part));
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    private String convertToString(Object value) {
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Date) return formatDate((Date) value);
        return value.toString();
    }
}
```

**Configuration Example:**
```yaml
sectionId: customer-info
type: ACROFORM
formTemplatePath: forms/customer-info.pdf
fieldMappingType: DIRECT
fieldMappings:
  customer_name: customerName
  customer_address: address.street
  customer_city: address.city
  customer_zip: address.zipCode
  invoice_total: invoice.total
  first_item: invoice.items.0.description
data:
  customerName: John Doe
  address:
    street: 123 Main St
    city: Boston
    zipCode: "02101"
  invoice:
    total: 1500.00
    items:
      - description: Consulting Services
        amount: 1500.00
```

### 2. JSONPath Strategy

**Powerful querying for complex JSON structures. Good for filtering, wildcards, and expressions.**

```java
@Component
class JsonPathMappingStrategy implements FieldMappingStrategy {
    
    @Override
    public Map<String, String> mapData(Map<String, Object> sourceData, 
                                      FieldMappingConfig config) {
        Map<String, String> result = new HashMap<>();
        DocumentContext jsonContext = JsonPath.parse(sourceData);
        
        for (Map.Entry<String, String> mapping : config.getFieldMappings().entrySet()) {
            String formFieldName = mapping.getKey();
            String jsonPathExpression = mapping.getValue();
            
            try {
                Object value = jsonContext.read(jsonPathExpression);
                result.put(formFieldName, convertToString(value));
            } catch (PathNotFoundException e) {
                result.put(formFieldName, "");
            }
        }
        
        return result;
    }
    
    private String convertToString(Object value) {
        if (value == null) return "";
        if (value instanceof List) {
            // Handle multi-value results
            return ((List<?>) value).stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        }
        return value.toString();
    }
}
```

**Maven Dependency:**
```xml
<dependency>
    <groupId>com.jayway.jsonpath</groupId>
    <artifactId>json-path</artifactId>
    <version>2.9.0</version>
</dependency>
```

**Configuration Example:**
```yaml
sectionId: employee-form
type: ACROFORM
formTemplatePath: forms/employee.pdf
fieldMappingType: JSONPATH
fieldMappings:
  employee_name: $.employee.fullName
  employee_dept: $.employee.department.name
  manager_name: $.employee.manager.fullName
  total_salary: $.employee.compensation.base
  recent_project: $.employee.projects[0].name
  all_skills: $.employee.skills[*].name
  senior_employees: $.employees[?(@.yearsOfService > 5)].name
data:
  employee:
    fullName: Jane Smith
    department:
      name: Engineering
      code: ENG
    manager:
      fullName: Bob Johnson
    compensation:
      base: 120000
      bonus: 15000
    projects:
      - name: Project Alpha
        status: Active
      - name: Project Beta
        status: Completed
    skills:
      - name: Java
        level: Expert
      - name: Python
        level: Intermediate
```

**JSONPath Advantages:**
- Filter expressions: `$.items[?(@.price > 100)]`
- Wildcards: `$.items[*].name`
- Array slicing: `$.items[0:3]`
- Deep scanning: `$..author` (all authors at any level)

### 3. JSONata Strategy

**Powerful transformations and computations. Best for complex data transformations.**

```java
@Component
class JsonataMappingStrategy implements FieldMappingStrategy {
    
    @Override
    public Map<String, String> mapData(Map<String, Object> sourceData, 
                                      FieldMappingConfig config) {
        Map<String, String> result = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        String jsonData = mapper.writeValueAsString(sourceData);
        
        for (Map.Entry<String, String> mapping : config.getFieldMappings().entrySet()) {
            String formFieldName = mapping.getKey();
            String jsonataExpression = mapping.getValue();
            
            try {
                Jsonata jsonata = Jsonata.jsonata(jsonataExpression);
                Object value = jsonata.evaluate(jsonData);
                result.put(formFieldName, convertToString(value));
            } catch (Exception e) {
                result.put(formFieldName, "");
            }
        }
        
        return result;
    }
}
```

**Maven Dependency:**
```xml
<dependency>
    <groupId>com.api.jsonata4java</groupId>
    <artifactId>JSONata4Java</artifactId>
    <version>2.4.8</version>
</dependency>
```

**Configuration Example:**
```yaml
sectionId: invoice-summary
type: ACROFORM
formTemplatePath: forms/invoice.pdf
fieldMappingType: JSONATA
fieldMappings:
  customer_full_name: firstName & ' ' & lastName
  total_amount: "$sum(items.price * items.quantity)"
  tax_amount: "$sum(items.price * items.quantity) * 0.08"
  grand_total: "$sum(items.(price * quantity)) * 1.08"
  item_count: "$count(items)"
  avg_item_price: "$average(items.price)"
  formatted_date: "$fromMillis(orderDate, '[M01]/[D01]/[Y0001]')"
  most_expensive: "$max(items.price)"
  items_above_50: "$join(items[price > 50].name, ', ')"
data:
  firstName: John
  lastName: Doe
  orderDate: 1704124800000
  items:
    - name: Widget A
      price: 25.00
      quantity: 3
    - name: Widget B
      price: 75.00
      quantity: 2
    - name: Widget C
      price: 40.00
      quantity: 1
```

**JSONata Advantages:**
- Mathematical operations: `$sum()`, `$average()`, `$max()`
- String manipulation: `$uppercase()`, `$substring()`, concatenation
- Date formatting: `$fromMillis()`, `$now()`
- Conditional logic: `condition ? trueValue : falseValue`
- Custom functions and transformations

### Comparison Matrix

| Feature | Direct Access | JSONPath | JSONata |
|---------|---------------|----------|---------|
| **Complexity** | Simple | Medium | Complex |
| **Performance** | ⚡⚡⚡ Fastest | ⚡⚡ Fast | ⚡ Slower |
| **Learning Curve** | Easy | Medium | Steep |
| **Dependencies** | None | 1 library | 1 library |
| **Nested Access** | ✅ Basic | ✅ Advanced | ✅ Advanced |
| **Filtering** | ❌ No | ✅ Yes | ✅ Yes |
| **Transformations** | ❌ Manual | ❌ Limited | ✅ Extensive |
| **Calculations** | ❌ Manual | ❌ No | ✅ Yes |
| **String Operations** | ❌ Manual | ❌ Limited | ✅ Yes |
| **Array Operations** | 🟡 Basic | ✅ Good | ✅ Excellent |
| **Date Formatting** | ❌ Manual | ❌ No | ✅ Yes |
| **Conditional Logic** | ❌ Manual | 🟡 Filters | ✅ Full |

### Hybrid Strategy (Recommended)

**Support all three strategies with automatic selection or per-field configuration.**

```java
@Component
class HybridMappingStrategy implements FieldMappingStrategy {
    private DirectMappingStrategy directStrategy;
    private JsonPathMappingStrategy jsonPathStrategy;
    private JsonataMappingStrategy jsonataStrategy;
    
    @Override
    public Map<String, String> mapData(Map<String, Object> sourceData, 
                                      FieldMappingConfig config) {
        FieldMappingStrategy strategy = selectStrategy(config.getMappingType());
        return strategy.mapData(sourceData, config);
    }
    
    private FieldMappingStrategy selectStrategy(MappingType type) {
        switch (type) {
            case JSONPATH: return jsonPathStrategy;
            case JSONATA: return jsonataStrategy;
            case DIRECT:
            default: return directStrategy;
        }
    }
}
```

### Advanced: Per-Field Mapping Type

```yaml
sectionId: complex-form
type: ACROFORM
formTemplatePath: forms/complex.pdf
fieldMappings:
  # Direct access for simple fields
  - formField: customer_name
    mappingType: DIRECT
    sourcePath: customerName
  
  # JSONPath for complex queries
  - formField: high_value_items
    mappingType: JSONPATH
    sourcePath: $.items[?(@.price > 100)].name
  
  # JSONata for calculations
  - formField: total_with_tax
    mappingType: JSONATA
    sourcePath: "$sum(items.(price * quantity)) * 1.08"
  
  # Direct for nested
  - formField: city
    mappingType: DIRECT
    sourcePath: address.city
```

### Best Practice Recommendations

**Use Direct Access when:**
- Simple, flat data structures
- Performance is critical (high-volume generation)
- No complex filtering or transformations needed
- Minimal dependencies preferred

**Use JSONPath when:**
- Complex nested JSON structures
- Need filtering capabilities
- Working with arrays and collections
- Familiar with JSONPath syntax (similar to XPath)

**Use JSONata when:**
- Complex calculations required (totals, averages, tax)
- Data transformations needed (format dates, concatenate strings)
- Conditional logic in mappings
- Need to aggregate or summarize data

**Hybrid Approach when:**
- Different forms have different complexity levels
- Want flexibility for future requirements
- Team has varying skill levels

### Performance Considerations

**Benchmark (1000 form fills):**
- **Direct Access**: ~500ms (0.5ms per form)
- **JSONPath**: ~1200ms (1.2ms per form)
- **JSONata**: ~2500ms (2.5ms per form)

**Optimization Tips:**
1. **Cache compiled expressions** (JSONPath, JSONata)
2. **Use Direct Access for simple fields** even in complex forms
3. **Pre-process data** if same transformation used multiple times
4. **Lazy evaluation** - only compute needed fields

```java
@Component
class OptimizedJsonataMappingStrategy implements FieldMappingStrategy {
    private LoadingCache<String, Jsonata> expressionCache = CacheBuilder.newBuilder()
        .maximumSize(100)
        .build(new CacheLoader<String, Jsonata>() {
            public Jsonata load(String expression) {
                return Jsonata.jsonata(expression);
            }
        });
    
    // Use cached compiled expressions
}
```

### Mapping Repeated Data to AcroForm Templates

**Challenge:** AcroForm PDF templates typically have fixed, single-value fields, but business data often includes repeating groups (invoice items, order lines, employee history, etc.).

#### Strategy 1: Multiple Field Sets (Fixed Number of Items)

**Best for:** Forms with a known maximum number of items (e.g., max 10 invoice items)

**PDF Form Structure:**
```
Item1_Description, Item1_Quantity, Item1_Price, Item1_Total
Item2_Description, Item2_Quantity, Item2_Price, Item2_Total
Item3_Description, Item3_Quantity, Item3_Price, Item3_Total
...
Item10_Description, Item10_Quantity, Item10_Price, Item10_Total
```

**Implementation:**
```java
@Component
class RepeatingGroupMapper {
    
    public Map<String, String> mapRepeatingGroups(List<InvoiceItem> items, int maxItems) {
        Map<String, String> fieldValues = new HashMap<>();
        
        // Map each item to its corresponding field set
        for (int i = 0; i < Math.min(items.size(), maxItems); i++) {
            InvoiceItem item = items.get(i);
            int fieldIndex = i + 1; // 1-based indexing
            
            fieldValues.put("Item" + fieldIndex + "_Description", item.getDescription());
            fieldValues.put("Item" + fieldIndex + "_Quantity", String.valueOf(item.getQuantity()));
            fieldValues.put("Item" + fieldIndex + "_Price", formatCurrency(item.getPrice()));
            fieldValues.put("Item" + fieldIndex + "_Total", formatCurrency(item.getTotal()));
        }
        
        // Clear remaining fields if fewer items than max
        for (int i = items.size(); i < maxItems; i++) {
            int fieldIndex = i + 1;
            fieldValues.put("Item" + fieldIndex + "_Description", "");
            fieldValues.put("Item" + fieldIndex + "_Quantity", "");
            fieldValues.put("Item" + fieldIndex + "_Price", "");
            fieldValues.put("Item" + fieldIndex + "_Total", "");
        }
        
        return fieldValues;
    }
}
```

**Configuration:**
```yaml
sectionId: invoice-items
type: ACROFORM
formTemplatePath: forms/invoice-10-items.pdf
fieldMappingType: DIRECT
maxRepeatingItems: 10
fieldMappings:
  InvoiceNumber: invoiceNumber
  CustomerName: customer.name
  # Items will be mapped automatically
data:
  invoiceNumber: "INV-2026-001"
  customer:
    name: John Doe
  items:
    - description: Consulting Services
      quantity: 10
      price: 150.00
      total: 1500.00
    - description: Software License
      quantity: 5
      price: 200.00
      total: 1000.00
```

**Pros:**
- ✅ Simple to implement
- ✅ Works with standard PDF forms
- ✅ Good for fixed-size datasets

**Cons:**
- ❌ Limited to predefined max items
- ❌ Wastes space if few items
- ❌ Can't handle overflow

#### Strategy 2: Overflow Handling (Multiple Pages)

**Best for:** Variable number of items that might exceed form capacity

**Implementation:**
```java
@Component
class OverflowAcroFormRenderer implements SectionRenderer {
    
    private static final int ITEMS_PER_PAGE = 10;
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        List<InvoiceItem> allItems = (List<InvoiceItem>) section.getData().get("items");
        
        // Split items into pages
        List<List<InvoiceItem>> pages = partition(allItems, ITEMS_PER_PAGE);
        
        PDDocument result = new PDDocument();
        
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            List<InvoiceItem> pageItems = pages.get(pageIndex);
            
            // Load form template for each page
            PDDocument formPage = loadFormTemplate(section.getFormTemplatePath());
            
            // Map data for this page
            Map<String, String> fieldValues = new HashMap<>();
            
            // Header fields (only on first page)
            if (pageIndex == 0) {
                fieldValues.put("InvoiceNumber", section.getData().get("invoiceNumber"));
                fieldValues.put("CustomerName", section.getData().get("customer.name"));
                fieldValues.put("InvoiceDate", section.getData().get("invoiceDate"));
            }
            
            // Map items for this page
            fieldValues.putAll(mapRepeatingGroups(pageItems, ITEMS_PER_PAGE));
            
            // Footer fields
            fieldValues.put("PageNumber", String.valueOf(pageIndex + 1));
            fieldValues.put("TotalPages", String.valueOf(pages.size()));
            
            // Only show totals on last page
            if (pageIndex == pages.size() - 1) {
                fieldValues.put("Subtotal", calculateSubtotal(allItems));
                fieldValues.put("Tax", calculateTax(allItems));
                fieldValues.put("Total", calculateTotal(allItems));
            }
            
            // Fill form
            fillFormFields(formPage, fieldValues);
            
            // Append to result
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.appendDocument(result, formPage);
            formPage.close();
        }
        
        return result;
    }
    
    private <T> List<List<T>> partition(List<T> list, int pageSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += pageSize) {
            partitions.add(list.subList(i, Math.min(i + pageSize, list.size())));
        }
        return partitions;
    }
}
```

**Pros:**
- ✅ Handles unlimited items
- ✅ Consistent layout per page
- ✅ Professional appearance

**Cons:**
- ❌ Multiple pages for few items
- ❌ More complex implementation

#### Strategy 3: Concatenated Text Fields

**Best for:** Text-heavy data, notes, history entries

**Implementation:**
```java
@Component
class ConcatenatedFieldMapper {
    
    public String concatenateItems(List<String> items, String separator) {
        return String.join(separator, items);
    }
    
    public String formatItemList(List<InvoiceItem> items) {
        return items.stream()
            .map(item -> String.format("%s (Qty: %d) - %s", 
                item.getDescription(), 
                item.getQuantity(), 
                formatCurrency(item.getTotal())))
            .collect(Collectors.joining("\n"));
    }
    
    public Map<String, String> mapToMultilineField(List<InvoiceItem> items) {
        Map<String, String> fieldValues = new HashMap<>();
        
        // Single multiline text field
        fieldValues.put("ItemList", formatItemList(items));
        
        return fieldValues;
    }
}
```

**PDF Form:** Single large text field named "ItemList"

**Result in PDF:**
```
Consulting Services (Qty: 10) - $1,500.00
Software License (Qty: 5) - $1,000.00
Support Package (Qty: 1) - $500.00
```

**Pros:**
- ✅ Simple form structure
- ✅ Unlimited items
- ✅ Good for summary views

**Cons:**
- ❌ Loss of structure
- ❌ Hard to parse programmatically
- ❌ Limited formatting options

#### Strategy 4: Hybrid Approach (Form + Generated Pages)

**Best for:** Complex documents with both fixed form fields and dynamic tables

**Implementation:**
```java
@Component
class HybridInvoiceRenderer {
    
    public PDDocument render(InvoiceData data) {
        PDDocument result = new PDDocument();
        
        // Page 1: AcroForm with header/summary
        PDDocument formPage = renderFormPage(data);
        PDFMergerUtility.appendDocument(result, formPage);
        
        // Page 2+: Generated item pages (using PDFBox or FreeMarker)
        if (data.getItems().size() > 0) {
            PDDocument itemsPages = renderItemsTable(data.getItems());
            PDFMergerUtility.appendDocument(result, itemsPages);
        }
        
        return result;
    }
    
    private PDDocument renderFormPage(InvoiceData data) {
        // Use AcroForm for header
        PDDocument formDoc = loadFormTemplate("invoice-header.pdf");
        
        Map<String, String> fields = new HashMap<>();
        fields.put("InvoiceNumber", data.getInvoiceNumber());
        fields.put("CustomerName", data.getCustomer().getName());
        fields.put("InvoiceDate", formatDate(data.getDate()));
        fields.put("ItemCount", String.valueOf(data.getItems().size()));
        fields.put("GrandTotal", formatCurrency(data.getTotal()));
        
        fillFormFields(formDoc, fields);
        return formDoc;
    }
    
    private PDDocument renderItemsTable(List<InvoiceItem> items) {
        // Use PDFBox direct rendering or FreeMarker for table
        // This gives full control over layout and pagination
        return itemTableRenderer.render(items);
    }
}
```

**Configuration:**
```yaml
templateId: invoice-hybrid
sections:
  # AcroForm header/summary
  - sectionId: invoice-header
    type: ACROFORM
    order: 1
    formTemplatePath: forms/invoice-header.pdf
    fieldMappings:
      InvoiceNumber: invoiceNumber
      CustomerName: customer.name
      InvoiceDate: invoiceDate
      ItemCount: "itemCount"
      GrandTotal: total
    
  # FreeMarker table for items
  - sectionId: invoice-items
    type: FREEMARKER
    order: 2
    templatePath: templates/invoice-items-table.ftl
    viewModelType: InvoiceItemsViewModel
    data:
      items: ${items}
```

**Pros:**
- ✅ Best of both worlds
- ✅ Structured header (AcroForm)
- ✅ Flexible content (generated)
- ✅ Professional appearance

**Cons:**
- ❌ More complex setup
- ❌ Multiple templates to maintain

#### Strategy 5: Dynamic Form Generation

**Best for:** Programmatic form creation based on data

**Implementation:**
```java
@Component
class DynamicAcroFormGenerator {
    
    public PDDocument generateFormWithItems(InvoiceData data) throws IOException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        
        // Create AcroForm
        PDAcroForm acroForm = new PDAcroForm(document);
        document.getDocumentCatalog().setAcroForm(acroForm);
        
        // Add header fields
        addTextField(acroForm, page, "InvoiceNumber", 50, 750, 200, 20);
        addTextField(acroForm, page, "CustomerName", 50, 720, 200, 20);
        
        // Dynamically add item fields based on actual item count
        int y = 650;
        for (int i = 0; i < data.getItems().size(); i++) {
            InvoiceItem item = data.getItems().get(i);
            
            addTextField(acroForm, page, "Item" + i + "_Desc", 50, y, 200, 20);
            addTextField(acroForm, page, "Item" + i + "_Qty", 260, y, 50, 20);
            addTextField(acroForm, page, "Item" + i + "_Price", 320, y, 80, 20);
            addTextField(acroForm, page, "Item" + i + "_Total", 410, y, 80, 20);
            
            // Set values
            acroForm.getField("Item" + i + "_Desc").setValue(item.getDescription());
            acroForm.getField("Item" + i + "_Qty").setValue(String.valueOf(item.getQuantity()));
            acroForm.getField("Item" + i + "_Price").setValue(formatCurrency(item.getPrice()));
            acroForm.getField("Item" + i + "_Total").setValue(formatCurrency(item.getTotal()));
            
            y -= 30;
            
            // Start new page if needed
            if (y < 100) {
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                y = 750;
            }
        }
        
        // Flatten form
        acroForm.flatten();
        
        return document;
    }
    
    private void addTextField(PDAcroForm form, PDPage page, String name, 
                              float x, float y, float width, float height) throws IOException {
        PDTextField textField = new PDTextField(form);
        textField.setPartialName(name);
        
        PDAnnotationWidget widget = textField.getWidgets().get(0);
        PDRectangle rect = new PDRectangle(x, y, width, height);
        widget.setRectangle(rect);
        widget.setPage(page);
        
        page.getAnnotations().add(widget);
        form.getFields().add(textField);
    }
}
```

**Pros:**
- ✅ Perfectly sized for data
- ✅ No wasted space
- ✅ Automatic pagination

**Cons:**
- ❌ Complex code
- ❌ No design preview
- ❌ Harder to maintain

#### Strategy 6: Summary + Detail Pattern

**Best for:** Forms requiring both summary and detail views

**Implementation:**
```java
public Map<String, String> mapWithSummary(List<InvoiceItem> items) {
    Map<String, String> fields = new HashMap<>();
    
    // Summary fields
    fields.put("TotalItems", String.valueOf(items.size()));
    fields.put("FirstItem", items.isEmpty() ? "" : items.get(0).getDescription());
    fields.put("LastItem", items.isEmpty() ? "" : items.get(items.size() - 1).getDescription());
    
    // Statistical summary
    fields.put("HighestValue", formatCurrency(
        items.stream().map(InvoiceItem::getTotal).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO)
    ));
    fields.put("LowestValue", formatCurrency(
        items.stream().map(InvoiceItem::getTotal).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO)
    ));
    fields.put("AverageValue", formatCurrency(
        items.stream().map(InvoiceItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(items.size()), 2, RoundingMode.HALF_UP)
    ));
    
    // Top N items (if form has space for them)
    List<InvoiceItem> topItems = items.stream()
        .sorted(Comparator.comparing(InvoiceItem::getTotal).reversed())
        .limit(5)
        .collect(Collectors.toList());
    
    for (int i = 0; i < topItems.size(); i++) {
        InvoiceItem item = topItems.get(i);
        fields.put("TopItem" + (i+1) + "_Desc", item.getDescription());
        fields.put("TopItem" + (i+1) + "_Amount", formatCurrency(item.getTotal()));
    }
    
    return fields;
}
```

**Pros:**
- ✅ Useful for executive summaries
- ✅ Fits in single-page forms
- ✅ Highlights key information

**Cons:**
- ❌ Loss of detail
- ❌ Not suitable for all use cases

#### Strategy 7: Automated Repeating Groups (Implemented)

**Best for:** Automatically mapping collections to numbered PDF fields with flexible naming conventions.

**Implementation:**
```java
@Data
public class RepeatingGroupConfig {
    private String prefix;
    private String suffix;
    private int startIndex = 1;
    private String indexSeparator;
    private IndexPosition indexPosition = IndexPosition.BEFORE_FIELD;
    private Integer maxItems;
    private Map<String, String> fields;

    public enum IndexPosition {
        BEFORE_FIELD, // e.g., child1FirstName
        AFTER_FIELD   // e.g., childFirstName.1
    }
}
```

**Configuration Example:**
```yaml
sectionId: enrollment-form
type: ACROFORM
fieldMappingGroups:
  - mappingType: JSONPATH
    basePath: "application.applicants[type='CHILD']"
    repeatingGroup:
      prefix: "child"
      startIndex: 1
      indexSeparator: "."
      indexPosition: AFTER_FIELD
      fields:
        FirstName: "demographic.firstName"
        LastName: "demographic.lastName"
        DOB: "demographic.dateOfBirth"
```

**Pros:**
- ✅ Extremely low verbosity
- ✅ Handles dynamic collection sizes
- ✅ Flexible field naming (prefix, suffix, separator, position)
- ✅ Reusable mapping templates

**Cons:**
- ❌ Requires consistent PDF field naming conventions

#### Strategy 8: Consistent Header/Footer & Pagination (Implemented)

**Best for:** Adding global headers, footers, and page numbering across all document types (AcroForm, FreeMarker, PDFBox Components).

**Implementation:**
The system uses a post-processing approach where headers and footers are overlaid on the merged PDF document.

**Configuration:**
```yaml
headerFooterConfig:
  header:
    renderType: FREEMARKER
    content: "Application ID: ${application.applicationId} | Page ${pageNumber} of ${totalPages}"
    alignment: "CENTER"
    marginTop: 30
  footer:
    renderType: PDFBOX
    content: "© 2026 Example Corp | Confidential"
    alignment: "LEFT"
    marginBottom: 30
  excludePages: [0] # Skip first page
```

**Render Types:**
1. **PDFBOX**: Simple text rendering with basic placeholder support (`{page}`, `{total}`).
2. **FREEMARKER**: Full template support with access to the entire request data and page context variables (`pageNumber`, `totalPages`).

**Pros:**
- ✅ Consistent look across different section types
- ✅ Dynamic content based on document data
- ✅ Flexible alignment and margin control
- ✅ Support for page exclusion (e.g., no header on cover page)

**Cons:**
- ❌ Overlays can sometimes overlap with section content if margins are not managed

### Recommended Approach by Use Case

| Use Case | Recommended Strategy | Reason |
|----------|---------------------|---------|
| **Invoice (1-10 items)** | Multiple Field Sets | Simple, predictable |
| **Invoice (10+ items)** | Hybrid (Form + Table) | Best appearance |
| **Order History** | Overflow Handling | Unlimited items |
| **Notes/Comments** | Concatenated Text | Text-heavy data |
| **Reports** | Summary + Detail | Focus on insights |
| **Dynamic Forms** | Dynamic Generation | Perfect fit for data |

### Enhanced AcroForm Renderer with Repeating Groups

The `AcroFormRenderer` supports complex mapping scenarios by combining multiple `FieldMappingGroup` objects. Each group can use a different strategy (DIRECT, JSONPATH, JSONATA) and can optionally define a `repeatingGroup` configuration.

**Implementation Logic:**
```java
private Map<String, String> mapRepeatingGroup(FieldMappingGroup group, RenderContext context, FieldMappingStrategy strategy) {
    RepeatingGroupConfig config = group.getRepeatingGroup();
    Object collection = strategy.evaluatePath(context.getData(), group.getBasePath());
    
    List<?> items = (List<?>) collection;
    for (int i = 0; i < items.size(); i++) {
        Object item = items.get(i);
        int displayIndex = config.getStartIndex() + i;
        
        Map<String, String> itemFieldMappings = new HashMap<>();
        for (Map.Entry<String, String> fieldEntry : config.getFields().entrySet()) {
            // Construct PDF field name based on prefix, suffix, separator, and position
            String pdfFieldName = constructFieldName(config, fieldEntry.getKey(), displayIndex);
            itemFieldMappings.put(pdfFieldName, fieldEntry.getValue());
        }
        
        // Map fields for this single item context
        Map<String, String> itemValues = strategy.mapFromContext(item, itemFieldMappings);
        result.putAll(itemValues);
    }
    return result;
}
```

**Configuration with Repeating Group:**
```yaml
sectionId: enrollment-form
type: ACROFORM
templatePath: templates/forms/applicant-form.pdf
fieldMappingGroups:
  # Group 1: Simple fields
  - mappingType: DIRECT
    fields:
      applicationId: application.applicationId
  
  # Group 2: Repeating children
  - mappingType: JSONPATH
    basePath: "application.applicants[type='CHILD']"
    repeatingGroup:
      prefix: "child"
      startIndex: 1
      indexSeparator: "."
      indexPosition: AFTER_FIELD
      fields:
        FirstName: "demographic.firstName"
        LastName: "demographic.lastName"
```
```

### 3. Document Composer (Orchestrator)

```java
@Service
class DocumentComposer {
    private List<SectionRenderer> renderers;
    private PdfAssembler pdfAssembler;
    private HeaderFooterProcessor headerFooterProcessor;
    
    public byte[] generateDocument(DocumentTemplate template) {
        RenderContext context = new RenderContext(template);
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
            template.getHeaderFooterConfig()
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

### 5. Header/Footer Processor (Hybrid Approach)

```java
// Updated HeaderFooterConfig supporting multiple rendering types
class HeaderFooterConfig {
    private HeaderTemplate header;
    private FooterTemplate footer;
    private boolean applyToAllPages;
    private Set<Integer> excludePages;
}

class HeaderTemplate {
    private RenderType renderType;  // PDFBOX or FREEMARKER
    private String content;         // Simple text or template path
    private String alignment;
    private float marginTop;
    private Map<String, Object> data;  // Data for FreeMarker templates
}

enum RenderType {
    PDFBOX,      // Direct PDFBox rendering
    FREEMARKER   // FreeMarker template to HTML to PDF
}

@Component
class HeaderFooterProcessor {
    private FreeMarkerHeaderFooterRenderer freemarkerRenderer;
    private PdfBoxHeaderFooterRenderer pdfBoxRenderer;
    
    public PDDocument apply(PDDocument document, HeaderFooterConfig config) {
        if (config == null) {
            return document;
        }
        
        int totalPages = document.getNumberOfPages();
        
        for (int pageNum = 0; pageNum < totalPages; pageNum++) {
            if (config.getExcludePages().contains(pageNum)) {
                continue;
            }
            
            PDPage page = document.getPage(pageNum);
            PageContext pageContext = new PageContext(pageNum + 1, totalPages);
            
            if (config.getHeader() != null) {
                applyHeader(document, page, config.getHeader(), pageContext);
            }
            
            if (config.getFooter() != null) {
                applyFooter(document, page, config.getFooter(), pageContext);
            }
        }
        
        return document;
    }
    
    private void applyHeader(PDDocument document, PDPage page, 
                           HeaderTemplate header, PageContext context) {
        HeaderFooterRenderer renderer = getRenderer(header.getRenderType());
        renderer.renderHeader(document, page, header, context);
    }
    
    private void applyFooter(PDDocument document, PDPage page, 
                           FooterTemplate footer, PageContext context) {
        HeaderFooterRenderer renderer = getRenderer(footer.getRenderType());
        renderer.renderFooter(document, page, footer, context);
    }
    
    private HeaderFooterRenderer getRenderer(RenderType type) {
        return type == RenderType.FREEMARKER 
            ? freemarkerRenderer 
            : pdfBoxRenderer;
    }
}

// Renderer Strategy Interface
interface HeaderFooterRenderer {
    void renderHeader(PDDocument document, PDPage page, 
                     HeaderTemplate template, PageContext context);
    void renderFooter(PDDocument document, PDPage page, 
                     FooterTemplate template, PageContext context);
}

// PDFBox Implementation
@Component
class PdfBoxHeaderFooterRenderer implements HeaderFooterRenderer {
    @Override
    public void renderHeader(PDDocument document, PDPage page, 
                           HeaderTemplate header, PageContext context) {
        try (PDPageContentStream contentStream = 
                new PDPageContentStream(document, page, 
                    PDPageContentStream.AppendMode.APPEND, true)) {
            
            float yPosition = page.getMediaBox().getHeight() - header.getMarginTop();
            
            // Simple text rendering with variable substitution
            String text = substituteVariables(header.getContent(), context);
            
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 10);
            contentStream.newLineAtOffset(getXPosition(header, page), yPosition);
            contentStream.showText(text);
            contentStream.endText();
        } catch (IOException e) {
            throw new HeaderFooterRenderException("Failed to render header", e);
        }
    }
    
    @Override
    public void renderFooter(PDDocument document, PDPage page, 
                           FooterTemplate footer, PageContext context) {
        // Similar implementation for footer
    }
    
    private String substituteVariables(String template, PageContext context) {
        return template
            .replace("{{pageNumber}}", String.valueOf(context.getPageNumber()))
            .replace("{{totalPages}}", String.valueOf(context.getTotalPages()))
            .replace("{{date}}", LocalDate.now().toString());
    }
}

// FreeMarker Implementation
@Component
class FreeMarkerHeaderFooterRenderer implements HeaderFooterRenderer {
    private Configuration freemarkerConfig;
    private PdfRenderer pdfRenderer;  // OpenHtmlToPDF wrapper
    
    @Override
    public void renderHeader(PDDocument document, PDPage page, 
                           HeaderTemplate header, PageContext context) {
        try {
            // 1. Process FreeMarker template
            Template template = freemarkerConfig.getTemplate(header.getContent());
            Map<String, Object> data = prepareData(header.getData(), context);
            String html = processTemplate(template, data);
            
            // 2. Convert HTML to PDF snippet
            byte[] headerPdf = pdfRenderer.renderHtmlToPdf(html);
            
            // 3. Overlay onto page
            PDDocument headerDoc = PDDocument.load(headerPdf);
            overlayOnPage(document, page, headerDoc, header.getMarginTop(), true);
            headerDoc.close();
            
        } catch (Exception e) {
            throw new HeaderFooterRenderException("Failed to render FreeMarker header", e);
        }
    }
    
    @Override
    public void renderFooter(PDDocument document, PDPage page, 
                           FooterTemplate footer, PageContext context) {
        // Similar implementation for footer
    }
    
    private Map<String, Object> prepareData(Map<String, Object> templateData, 
                                           PageContext context) {
        Map<String, Object> data = new HashMap<>(templateData);
        data.put("pageNumber", context.getPageNumber());
        data.put("totalPages", context.getTotalPages());
        data.put("date", LocalDate.now());
        return data;
    }
    
    private String processTemplate(Template template, Map<String, Object> data) 
            throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        template.process(data, writer);
        return writer.toString();
    }
    
    private void overlayOnPage(PDDocument document, PDPage targetPage, 
                              PDDocument overlayDoc, float margin, boolean isHeader) {
        // Use PDFBox LayerUtility to overlay the rendered header/footer
        LayerUtility layerUtility = new LayerUtility(document);
        PDFormXObject overlay = layerUtility.importPageAsForm(overlayDoc, 0);
        
        // Calculate position and overlay
        // Implementation details...
    }
}

class PageContext {
    private int pageNumber;
    private int totalPages;
    // ... getters
}
```

## Header/Footer Rendering Approaches

### Comparison: PDFBox vs FreeMarker

| Aspect | PDFBox Direct | FreeMarker Templates |
|--------|---------------|---------------------|
| **Complexity** | Simple text/shapes | Rich HTML layouts |
| **Performance** | ⚡ Fast | 🐌 Slower (HTML→PDF conversion) |
| **Flexibility** | Limited to PDFBox API | Full HTML/CSS support |
| **Learning Curve** | Steeper (coordinates, fonts) | Easier (HTML/CSS) |
| **Dynamic Content** | Variable substitution only | Full templating logic |
| **Images/Logos** | Manual positioning | CSS layout |
| **Conditional Logic** | Java code | FreeMarker directives |
| **Maintenance** | Code changes | Template changes |
| **Consistency** | Requires careful coding | CSS ensures consistency |
| **Use Case** | Simple text headers/footers | Complex branded headers |

### When to Use Each

**Use PDFBox Direct when:**
- Simple page numbers, dates, copyright text
- Performance is critical (high-volume generation)
- No complex layouts needed
- Minimal dynamic content
- You want precise control over positioning

**Use FreeMarker Templates when:**
- Complex layouts with logos, multiple columns
- Branded headers/footers matching company style
- Conditional content (e.g., "Draft" watermark)
- HTML/CSS expertise on team
- Need to reuse existing HTML components
- Frequent header/footer design changes

### Hybrid Configuration Examples

**JSON Configuration:**
```json
{
  "headerFooter": {
    "header": {
      "renderType": "FREEMARKER",
      "content": "templates/headers/invoice-header.ftl",
      "marginTop": 20,
      "data": {
        "companyLogo": "/images/logo.png",
        "companyName": "ACME Corp"
      }
    },
    "footer": {
      "renderType": "PDFBOX",
      "content": "Page {{pageNumber}} of {{totalPages}} - © 2026 ACME Corp",
      "alignment": "CENTER",
      "marginBottom": 20
    }
  }
}
```

**YAML Configuration:**
```yaml
headerFooter:
  header:
    renderType: FREEMARKER
    content: templates/headers/invoice-header.ftl
    marginTop: 20
    data:
      companyLogo: /images/logo.png
      companyName: ACME Corp
      showDraftWatermark: true
  
  footer:
    renderType: PDFBOX  # Simple footer = faster
    content: "Page {{pageNumber}} of {{totalPages}} - © 2026 ACME Corp"
    alignment: CENTER
    marginBottom: 20
```

### FreeMarker Header Template Example

**File: `templates/headers/invoice-header.ftl`**
```html
<!DOCTYPE html>
<html>
<head>
    <style>
        body { margin: 0; padding: 10px; font-family: Arial, sans-serif; }
        .header-container {
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 2px solid #333;
            padding-bottom: 5px;
        }
        .logo { height: 40px; }
        .company-info { text-align: right; }
        .page-info { font-size: 10px; color: #666; }
        <#if showDraftWatermark>
        .watermark {
            position: absolute;
            top: 20px;
            right: 20px;
            color: red;
            font-size: 24px;
            font-weight: bold;
            opacity: 0.5;
            transform: rotate(-15deg);
        }
        </#if>
    </style>
</head>
<body>
    <div class="header-container">
        <div>
            <img src="${companyLogo}" class="logo" alt="Company Logo" />
        </div>
        <div class="company-info">
            <strong>${companyName}</strong><br/>
            <span class="page-info">Page ${pageNumber} of ${totalPages}</span>
        </div>
    </div>
    <#if showDraftWatermark>
    <div class="watermark">DRAFT</div>
    </#if>
</body>
</html>
```

### Pros & Cons Summary

**PDFBox Pros:**
- ✅ Fast performance
- ✅ Low memory footprint
- ✅ Precise positioning control
- ✅ No additional conversion step
- ✅ Good for simple content

**PDFBox Cons:**
- ❌ Complex layouts require lots of code
- ❌ Positioning requires coordinate calculations
- ❌ Font management complexity
- ❌ Limited to PDFBox capabilities
- ❌ Changes require code deployment

**FreeMarker Pros:**
- ✅ Rich layouts with HTML/CSS
- ✅ Familiar web technologies
- ✅ Template changes without code deployment
- ✅ Conditional logic in templates
- ✅ Reusable components
- ✅ Logo/image placement via CSS

**FreeMarker Cons:**
- ❌ Performance overhead (HTML→PDF)
- ❌ Additional memory usage
- ❌ Complexity of HTML/CSS rendering
- ❌ Potential layout inconsistencies
- ❌ Dependency on HTML rendering engine

### Recommendation

**Hybrid Approach (Best of Both Worlds):**
- **Complex headers** (with logos, branding) → FreeMarker
- **Simple footers** (page numbers, copyright) → PDFBox
- **Performance-critical scenarios** → PDFBox
- **Frequently changing designs** → FreeMarker

The design supports both seamlessly, allowing per-header/footer configuration.

## Headers/Footers on AcroForm Templates

### Yes, FreeMarker Headers/Footers Work with AcroForm Sections!

Headers and footers are applied **after** section rendering and merging, making them completely independent of the section rendering method. This means you can apply FreeMarker-based headers/footers on top of AcroForm-generated pages.

### Processing Order

```
1. Render AcroForm Section
   └─> Load PDF form template
   └─> Fill form fields with data
   └─> Generate PDDocument
   
2. Render Other Sections (if any)
   └─> FreeMarker, PDFBox, etc.
   
3. Merge All Sections
   └─> Combine into single PDDocument
   
4. Apply Headers/Footers ⭐
   └─> FreeMarker headers overlay on ALL pages
   └─> PDFBox footers overlay on ALL pages
   └─> Works regardless of section type
```

### Example: AcroForm + FreeMarker Header

**Template Definition:**
```yaml
templateId: employee-form-with-branding
sections:
  - sectionId: employment-application
    type: ACROFORM
    order: 1
    formTemplatePath: forms/employment-application.pdf
    data:
      employeeName: John Doe
      employeeId: "12345"
      department: Engineering
      startDate: "2026-01-15"

headerFooter:
  header:
    renderType: FREEMARKER  # Rich branded header
    content: templates/headers/company-header.ftl
    marginTop: 15
    data:
      companyLogo: /images/hr-logo.png
      department: Human Resources
      confidential: true
  
  footer:
    renderType: PDFBOX  # Simple footer
    content: "Page {{pageNumber}} of {{totalPages}} - Confidential"
    alignment: CENTER
    marginBottom: 15
```

**Result:**
```
┌─────────────────────────────────────────────┐
│  [LOGO]  Human Resources  CONFIDENTIAL      │ ← FreeMarker Header
├─────────────────────────────────────────────┤
│                                             │
│  Employee Name: [John Doe      ]            │
│  Employee ID:   [12345         ]            │ ← AcroForm Content
│  Department:    [Engineering   ]            │   (Filled Fields)
│  Start Date:    [2026-01-15    ]            │
│                                             │
├─────────────────────────────────────────────┤
│    Page 1 of 1 - Confidential               │ ← PDFBox Footer
└─────────────────────────────────────────────┘
```

### Technical Implementation

The `HeaderFooterProcessor` works at the PDDocument level, using PDFBox's `AppendMode.APPEND` to overlay content on existing pages:

```java
// This works on ANY PDDocument, regardless of origin
private void applyHeader(PDDocument document, PDPage page, 
                       HeaderTemplate header, PageContext context) {
    // AppendMode.APPEND preserves existing content (AcroForm fields, etc.)
    try (PDPageContentStream contentStream = 
            new PDPageContentStream(document, page, 
                PDPageContentStream.AppendMode.APPEND, true)) {
        
        // Header is rendered ON TOP of existing page content
        // Works whether page came from AcroForm, FreeMarker, or PDFBox
        renderer.renderHeader(document, page, header, context);
    }
}
```

### Multi-Section Example with Mixed Types

```yaml
templateId: comprehensive-employee-package
sections:
  # AcroForm-based employment form
  - sectionId: employment-form
    type: ACROFORM
    order: 1
    formTemplatePath: forms/employment.pdf
    data:
      employeeName: John Doe
      # ... form data
  
  # FreeMarker-based benefits summary  
  - sectionId: benefits-summary
    type: FREEMARKER
    order: 2
    templatePath: templates/benefits.ftl
    data:
      healthPlan: Premium
      # ... benefits data
  
  # PDFBox custom tax forms
  - sectionId: tax-withholding
    type: PDFBOX_COMPONENT
    order: 3
    componentId: taxForm
    data:
      filingStatus: Single
      # ... tax data

# Single header/footer applied to ALL pages
headerFooter:
  header:
    renderType: FREEMARKER
    content: templates/headers/hr-header.ftl
    marginTop: 15
    data:
      packageType: New Hire Package
      year: 2026
  footer:
    renderType: PDFBOX
    content: "HR Department - Page {{pageNumber}} of {{totalPages}}"
    alignment: CENTER
    marginBottom: 15
```

**Result Document:**
- **Page 1** (AcroForm): FreeMarker header + filled form + PDFBox footer
- **Page 2** (FreeMarker): FreeMarker header + benefits content + PDFBox footer  
- **Page 3** (PDFBox): FreeMarker header + tax form + PDFBox footer

All pages get consistent branding regardless of content source! ✨

### Considerations

**Layout Margins:**
- Ensure AcroForm templates have sufficient top/bottom margins
- Header/footer will overlay if margins are insufficient
- Typical recommendation: 
  - Top margin: 50-70 points (≈0.7-1 inch) for headers
  - Bottom margin: 40-50 points (≈0.55-0.7 inch) for footers

**Form Flattening:**
```java
class AcroFormSectionRenderer implements SectionRenderer {
    private boolean flattenForms = true;  // Recommended for headers/footers
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        PDDocument formDoc = loadAndFillForm(section);
        
        if (flattenForms) {
            // Flatten form fields to prevent interference with overlays
            formDoc.getDocumentCatalog().getAcroForm().flatten();
        }
        
        return formDoc;
    }
}
```

**Why Flatten?**
- Prevents form field rendering from overlapping header/footer content
- Ensures consistent appearance across PDF viewers
- Makes the document final (fields not editable after generation)

### Performance Notes

Applying FreeMarker headers/footers on AcroForm pages has minimal performance impact:
- **AcroForm rendering**: ~100-200ms per page
- **FreeMarker header overlay**: ~50-100ms per page (one-time HTML→PDF conversion, cached)
- **Total**: ~150-300ms per page

For high-volume scenarios, consider:
- Caching rendered headers (same header for multiple documents)
- Using PDFBox for simple headers when branding isn't critical
```

### 6. Component Registry (for PDFBox Direct Components)

```java
interface PdfComponent {
    void render(PDDocument document, PDPage page, RenderContext context);
}

@Component
class ComponentRegistry {
    private Map<String, PdfComponent> components = new ConcurrentHashMap<>();
    
    public void register(String componentId, PdfComponent component) {
        components.put(componentId, component);
    }
    
    public PdfComponent get(String componentId) {
        return components.get(componentId);
    }
}

// Example custom component
@Component("invoiceTable")
class InvoiceTableComponent implements PdfComponent {
    @Override
    public void render(PDDocument document, PDPage page, RenderContext context) {
        // Custom PDFBox rendering logic for invoice table
        List<InvoiceItem> items = context.getData("items");
        
        try (PDPageContentStream contentStream = 
                new PDPageContentStream(document, page)) {
            // Draw table headers, rows, borders, etc.
            drawTable(contentStream, items);
        }
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
    
    <!-- Alternative: Apache FOP for XSL-FO to PDF -->
    <dependency>
        <groupId>org.apache.xmlgraphics</groupId>
        <artifactId>fop</artifactId>
        <version>2.9</version>
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
    
    <!-- Excel to PDF Conversion -->
    <dependency>
        <groupId>fr.opensagres.xdocreport</groupId>
        <artifactId>fr.opensagres.poi.xwpf.converter.pdf</artifactId>
        <version>2.0.4</version>
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
1. Client Request
   ↓
2. Load Document Template Definition
   ↓
3. For Each Section:
   a. Determine Section Type
   b. Select Appropriate Renderer
   c. Render Section to PDDocument
   d. Add to Section List
   ↓
4. Merge All Sections
   ↓
5. Apply Headers and Footers
   ↓
6. Return Final PDF
```

## Template Definition Formats

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

### JSON Format Example

```json
{
  "templateId": "invoice-template-v1",
  "sections": [
    {
      "sectionId": "company-header",
      "type": "FREEMARKER",
      "order": 1,
      "templatePath": "templates/invoice/header.ftl",
      "data": {
        "companyName": "ACME Corp",
        "companyLogo": "base64..."
      }
    },
    {
      "sectionId": "customer-info",
      "type": "ACROFORM",
      "order": 2,
      "formTemplatePath": "forms/customer-info.pdf",
      "data": {
        "customerName": "John Doe",
        "address": "123 Main St"
      }
    },
    {
      "sectionId": "invoice-table",
      "type": "PDFBOX_COMPONENT",
      "order": 3,
      "componentId": "invoiceTable",
      "data": {
        "items": [
          {"description": "Item 1", "quantity": 2, "price": 50.00}
        ]
      }
    }
  ],
  "headerFooter": {
    "header": {
      "enabled": true,
      "template": "Page {{pageNumber}} of {{totalPages}}",
      "alignment": "RIGHT",
      "marginTop": 20
    },
    "footer": {
      "enabled": true,
      "template": "© 2026 ACME Corp",
      "alignment": "CENTER",
      "marginBottom": 20
    },
    "excludePages": [0]
  }
}
```

### YAML Format Example

```yaml
# Invoice Template Definition
templateId: invoice-template-v1

# Common data that can be reused
commonHeaderFooter: &standardHeaderFooter
  header:
    enabled: true
    template: "Page {{pageNumber}} of {{totalPages}}"
    alignment: RIGHT
    marginTop: 20
  footer:
    enabled: true
    template: "© 2026 ACME Corp"
    alignment: CENTER
    marginBottom: 20
  excludePages: [0]

sections:
  - sectionId: company-header
    type: FREEMARKER
    order: 1
    templatePath: templates/invoice/header.ftl
    data:
      companyName: ACME Corp
      companyLogo: base64...
  
  - sectionId: customer-info
    type: ACROFORM
    order: 2
    formTemplatePath: forms/customer-info.pdf
    data:
      customerName: John Doe
      address: 123 Main St
  
  - sectionId: invoice-table
    type: PDFBOX_COMPONENT
    order: 3
    componentId: invoiceTable
    data:
      items:
        - description: Item 1
          quantity: 2
          price: 50.00

headerFooter: *standardHeaderFooter
```

### Format Comparison

| Feature | JSON | YAML |
|---------|------|------|
| **Comments** | ❌ No | ✅ Yes |
| **Readability** | Good | Excellent |
| **Reusability** | ❌ No anchors | ✅ Anchors & aliases |
| **Parsing Speed** | Faster | Slower |
| **Validation** | Stricter | More lenient |
| **API Integration** | Better | Good |
| **Human Editing** | Good | Excellent |

**Recommendation**: Use YAML for manual template creation and JSON for programmatic/API usage. The system supports both seamlessly.
```

## Key Design Patterns

1. **Strategy Pattern**: Different renderers for different section types
2. **Template Method**: Common document generation flow with customizable steps
3. **Builder Pattern**: For constructing complex document templates
4. **Factory Pattern**: For creating appropriate renderers
5. **Composite Pattern**: For nested sections/pages
6. **Registry Pattern**: For managing custom PDFBox components

## Advanced Features

### 1. Caching Strategy

```java
@Component
class TemplateCacheManager {
    private Cache<String, Template> freemarkerCache;
    private Cache<String, PDDocument> formTemplateCache;
    
    public Template getCachedFreemarkerTemplate(String path) {
        return freemarkerCache.get(path, this::loadFreemarkerTemplate);
    }
}
```

### 2. Asynchronous Generation

```java
@Service
class AsyncDocumentGenerator {
    @Async
    public CompletableFuture<byte[]> generateAsync(DocumentTemplate template) {
        return CompletableFuture.supplyAsync(() -> 
            documentComposer.generateDocument(template)
        );
    }
}
```

### 3. Conditional Section Rendering

```java
interface SectionCondition {
    boolean shouldRender(RenderContext context);
}

class PageSection {
    private SectionCondition condition;
    
    public boolean isRenderable(RenderContext context) {
        return condition == null || condition.shouldRender(context);
    }
}
```

### 4. Multi-Format Support

```java
interface DocumentExporter {
    byte[] export(PDDocument document, OutputFormat format);
}

enum OutputFormat {
    PDF,
    PDF_A,  // Archival format
    PDF_UA  // Universal Accessibility
}
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

## Testing Strategy

```java
@SpringBootTest
class DocumentComposerTest {
    @Test
    void shouldGenerateDocumentWithMultipleSections() {
        // Given
        DocumentTemplate template = createTestTemplate();
        
        // When
        byte[] pdf = documentComposer.generateDocument(template);
        
        // Then
        assertValidPdf(pdf);
        assertPageCount(pdf, 3);
        assertHasHeaders(pdf);
        assertHasFooters(pdf);
    }
}
```

## API Example

```java
@RestController
@RequestMapping("/api/documents")
class DocumentController {
    @Autowired
    private DocumentComposer documentComposer;
    
    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody DocumentTemplate template) {
        byte[] pdf = documentComposer.generateDocument(template);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", 
            template.getTemplateId() + ".pdf");
        
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
```

## Excel-Based Templates

### Overview

Excel templates provide a familiar spreadsheet-based approach for document generation, ideal for financial reports, invoices, tabular data, and calculations. The system loads Excel templates (.xlsx/.xls), populates data, evaluates formulas, and converts to PDF.

### Excel Section Configuration

#### Cell Reference Mapping

```yaml
sectionId: financial-report
type: EXCEL
order: 1
templatePath: templates/financial-report.xlsx
sheetName: Report  # Optional, defaults to first sheet
fieldMappingType: DIRECT
fieldMappings:
  # Direct cell references
  Sheet1!A1: companyName
  Sheet1!B5: reportDate
  Sheet1!C10: revenue
  Sheet1!C11: expenses
  # C12 has formula: =C10-C11 (auto-calculated)
  
  # Named ranges (cleaner approach)
  CompanyName: companyName
  ReportDate: reportDate
  TotalRevenue: revenue
  TotalExpenses: expenses
  
data:
  companyName: ACME Corporation
  reportDate: "2026-01-01"
  revenue: 1500000
  expenses: 980000
```

#### Array/Table Population

```yaml
sectionId: invoice-items
type: EXCEL
order: 1
templatePath: templates/invoice.xlsx
fieldMappingType: DIRECT
fieldMappings:
  # Header fields
  InvoiceNumber: invoiceNumber
  CustomerName: customer.name
  InvoiceDate: invoiceDate
  
  # Table data (starting at row 10)
  ItemsTable: items  # Named table range
  
data:
  invoiceNumber: "INV-2026-001"
  customer:
    name: John Doe
    address: 123 Main St
  invoiceDate: "2026-01-15"
  items:
    - description: Consulting Services
      quantity: 10
      rate: 150.00
      # Formula in Excel: =quantity * rate
    - description: Software License
      quantity: 5
      rate: 200.00
    - description: Support Package
      quantity: 1
      rate: 500.00
```

### Excel Template Renderer Implementation

```java
@Component
class ExcelSectionRenderer implements SectionRenderer {
    private FieldMappingStrategy mappingStrategy;
    private ExcelToPdfConverter excelConverter;
    
    @Override
    public boolean supports(SectionType type) {
        return type == SectionType.EXCEL;
    }
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        try {
            // Load Excel template
            Workbook workbook = loadExcelTemplate(section.getTemplatePath());
            
            // Populate data
            populateExcelData(workbook, section.getData(), 
                            section.getFieldMappings(), section.getSheetName());
            
            // Evaluate all formulas
            evaluateFormulas(workbook);
            
            // Convert to PDF
            PDDocument pdfDoc = excelConverter.convert(workbook);
            
            workbook.close();
            return pdfDoc;
            
        } catch (Exception e) {
            throw new ExcelRenderException("Failed to render Excel template", e);
        }
    }
    
    private Workbook loadExcelTemplate(String templatePath) throws IOException {
        FileInputStream fis = new FileInputStream(templatePath);
        return WorkbookFactory.create(fis);
    }
    
    private void populateExcelData(Workbook workbook, Map<String, Object> data,
                                   FieldMappingConfig mappings, String sheetName) {
        Sheet sheet = sheetName != null 
            ? workbook.getSheet(sheetName) 
            : workbook.getSheetAt(0);
        
        Map<String, Object> cellValues = mappingStrategy.mapData(data, mappings);
        
        for (Map.Entry<String, Object> entry : cellValues.entrySet()) {
            String target = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof List) {
                // Handle table/array data
                populateTable(workbook, target, (List<?>) value);
            } else {
                // Handle single cell
                populateSingleCell(workbook, sheet, target, value);
            }
        }
    }
    
    private void populateSingleCell(Workbook workbook, Sheet sheet, 
                                    String cellRef, Object value) {
        if (cellRef.contains("!")) {
            // Sheet!Cell format
            String[] parts = cellRef.split("!");
            sheet = workbook.getSheet(parts[0]);
            cellRef = parts[1];
        }
        
        // Try named range first
        Name namedRange = workbook.getName(cellRef);
        if (namedRange != null) {
            cellRef = namedRange.getRefersToFormula().split("!")[1];
        }
        
        CellReference ref = new CellReference(cellRef);
        Row row = sheet.getRow(ref.getRow());
        if (row == null) row = sheet.createRow(ref.getRow());
        
        Cell cell = row.getCell(ref.getCol());
        if (cell == null) cell = row.createCell(ref.getCol());
        
        setCellValue(cell, value);
    }
    
    private void populateTable(Workbook workbook, String tableRange, List<?> data) {
        Name namedRange = workbook.getName(tableRange);
        if (namedRange == null) return;
        
        // Parse range reference (e.g., "Sheet1!A10:D10")
        String formula = namedRange.getRefersToFormula();
        String[] parts = formula.split("!");
        Sheet sheet = workbook.getSheet(parts[0]);
        AreaReference area = new AreaReference(parts[1], SpreadsheetVersion.EXCEL2007);
        
        CellReference firstCell = area.getFirstCell();
        int startRow = firstCell.getRow();
        int startCol = firstCell.getCol();
        
        // Populate rows
        for (int i = 0; i < data.size(); i++) {
            Object rowData = data.get(i);
            Row row = sheet.getRow(startRow + i);
            if (row == null) row = sheet.createRow(startRow + i);
            
            if (rowData instanceof Map) {
                populateRowFromMap(row, startCol, (Map<String, Object>) rowData);
            } else if (rowData instanceof List) {
                populateRowFromList(row, startCol, (List<?>) rowData);
            }
        }
    }
    
    private void populateRowFromMap(Row row, int startCol, Map<String, Object> data) {
        int col = startCol;
        for (Object value : data.values()) {
            Cell cell = row.getCell(col);
            if (cell == null) cell = row.createCell(col);
            setCellValue(cell, value);
            col++;
        }
    }
    
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            // Apply date format if cell doesn't have one
            if (cell.getCellStyle().getDataFormat() == 0) {
                CellStyle dateStyle = cell.getSheet().getWorkbook().createCellStyle();
                dateStyle.setDataFormat((short) 14); // mm/dd/yyyy
                cell.setCellStyle(dateStyle);
            }
        } else {
            cell.setCellValue(value.toString());
        }
    }
    
    private void evaluateFormulas(Workbook workbook) {
        FormulaEvaluator evaluator = 
            workbook.getCreationHelper().createFormulaEvaluator();
        evaluator.evaluateAll();
    }
}
```

### Excel to PDF Conversion Options

#### Option 1: Apache POI + iText (Recommended)

```java
@Component
class ITextExcelToPdfConverter implements ExcelToPdfConverter {
    
    @Override
    public PDDocument convert(Workbook workbook) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Create PDF document
        Document document = new Document(PageSize.A4.rotate()); // Landscape for wide sheets
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();
        
        Sheet sheet = workbook.getSheetAt(0);
        PdfPTable table = createPdfTable(sheet);
        document.add(table);
        document.close();
        
        // Convert to PDDocument
        return PDDocument.load(baos.toByteArray());
    }
    
    private PdfPTable createPdfTable(Sheet sheet) {
        int maxCols = getMaxColumns(sheet);
        PdfPTable table = new PdfPTable(maxCols);
        
        for (Row row : sheet) {
            for (Cell cell : row) {
                PdfPCell pdfCell = new PdfPCell();
                pdfCell.setPhrase(new Phrase(getCellValueAsString(cell)));
                
                // Apply styling from Excel
                applyCellStyle(pdfCell, cell.getCellStyle());
                table.addCell(pdfCell);
            }
        }
        
        return table;
    }
}
```

**Dependency:**
```xml
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itextpdf</artifactId>
    <version>5.5.13.3</version>
</dependency>
```

#### Option 2: Documents4j (Windows/LibreOffice Required)

```java
@Component
class Documents4jExcelToPdfConverter implements ExcelToPdfConverter {
    
    @Override
    public PDDocument convert(Workbook workbook) throws IOException {
        // Save workbook to temp file
        File tempExcel = File.createTempFile("excel", ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(tempExcel)) {
            workbook.write(fos);
        }
        
        // Convert to PDF
        File tempPdf = File.createTempFile("pdf", ".pdf");
        IConverter converter = LocalConverter.builder().build();
        converter.convert(tempExcel).as(DocumentType.XLSX)
                 .to(tempPdf).as(DocumentType.PDF)
                 .execute();
        
        // Load as PDDocument
        PDDocument pdfDoc = PDDocument.load(tempPdf);
        
        // Cleanup
        tempExcel.delete();
        tempPdf.delete();
        
        return pdfDoc;
    }
}
```

**Dependency:**
```xml
<dependency>
    <groupId>com.documents4j</groupId>
    <artifactId>documents4j-local</artifactId>
    <version>1.1.8</version>
</dependency>
```

#### Option 3: Apache FOP (XSL-FO Transform)

Best for precise control, requires converting Excel to XSL-FO first.

### Excel Template Design Best Practices

**1. Use Named Ranges**
```excel
CompanyName → A1
InvoiceDate → B5
ItemsTable → A10:D20
TotalAmount → E21
```

Benefits:
- More readable configurations
- Template structure can change without breaking mappings
- Self-documenting

**2. Pre-format Cells**
- Apply number formats (currency, percentages, dates)
- Set column widths
- Define cell styles (fonts, colors, borders)
- Create formulas that reference data cells

**3. Use Excel Tables**
- Insert → Table
- Name the table
- Automatic formatting and formula propagation

**4. Formulas for Calculations**
```excel
Subtotal: =SUM(D10:D20)
Tax:      =E21*0.08
Total:    =E21+E22
```

### Complete Example Configuration

```yaml
templateId: quarterly-financial-report
sections:
  # Executive summary from FreeMarker
  - sectionId: executive-summary
    type: FREEMARKER
    order: 1
    templatePath: templates/executive-summary.ftl
    data:
      quarter: Q1 2026
      highlights: [...]
  
  # Financial tables from Excel
  - sectionId: income-statement
    type: EXCEL
    order: 2
    templatePath: templates/income-statement.xlsx
    sheetName: Income Statement
    fieldMappings:
      CompanyName: companyName
      ReportPeriod: period
      Revenue: revenue
      COGS: cogs
      # Gross Profit has formula: =Revenue-COGS
      OperatingExpenses: opex
      # Net Income has formula: =(Revenue-COGS)-OperatingExpenses
    data:
      companyName: ACME Corp
      period: Q1 2026
      revenue: 5000000
      cogs: 2000000
      opex: 1500000
  
  - sectionId: balance-sheet
    type: EXCEL
    order: 3
    templatePath: templates/balance-sheet.xlsx
    fieldMappings:
      CurrentAssets: assets.current
      FixedAssets: assets.fixed
      CurrentLiabilities: liabilities.current
      LongTermDebt: liabilities.longTerm
      Equity: equity
    data:
      assets:
        current: 3000000
        fixed: 7000000
      liabilities:
        current: 1500000
        longTerm: 3000000
      equity: 5500000
  
  # Custom charts via PDFBox
  - sectionId: revenue-chart
    type: PDFBOX_COMPONENT
    order: 4
    componentId: revenueChart
    data:
      chartData: [...]

headerFooter:
  header:
    renderType: FREEMARKER
    content: templates/headers/financial-header.ftl
    marginTop: 15
  footer:
    renderType: PDFBOX
    content: "Page {{pageNumber}} of {{totalPages}} - Confidential"
    alignment: CENTER
    marginBottom: 15
```

### Excel vs Other Template Types

| Feature | Excel | FreeMarker | AcroForm | PDFBox |
|---------|-------|------------|----------|---------|
| **Best For** | Tables, calculations | Rich text layouts | Government forms | Custom graphics |
| **Ease of Creation** | ⭐⭐⭐⭐⭐ Very Easy | ⭐⭐⭐ Medium | ⭐⭐ Hard | ⭐ Very Hard |
| **Calculations** | ⭐⭐⭐⭐⭐ Native | ❌ No | ❌ No | 🟡 Manual |
| **Formatting** | ⭐⭐⭐⭐⭐ Rich | ⭐⭐⭐⭐ CSS | ⭐⭐ Limited | ⭐⭐⭐⭐ Full control |
| **Business User Friendly** | ⭐⭐⭐⭐⭐ Yes | ⭐⭐ No | ⭐⭐⭐ Moderate | ❌ No |
| **Formulas** | ⭐⭐⭐⭐⭐ Full Excel | ❌ No | ❌ No | ❌ No |
| **Charts/Graphs** | ⭐⭐⭐⭐⭐ Native | 🟡 Limited | ❌ No | ⭐⭐⭐ Custom |
| **Performance** | ⭐⭐⭐ Good | ⭐⭐⭐⭐ Fast | ⭐⭐⭐⭐ Fast | ⭐⭐⭐⭐⭐ Fastest |
| **Data Tables** | ⭐⭐⭐⭐⭐ Excellent | ⭐⭐⭐ Good | ⭐⭐ Limited | ⭐⭐ Manual |

### When to Use Excel Templates

**✅ Use Excel when:**
- Financial reports, invoices, statements
- Heavy tabular data (pricing tables, inventory)
- Complex calculations and formulas needed
- Business users need to maintain templates
- Charts and graphs required
- Conditional formatting needed
- Data validation rules

**❌ Don't use Excel when:**
- Simple text documents
- Complex multi-column layouts (use FreeMarker)
- Government compliance forms (use AcroForm)
- Pixel-perfect positioning required (use PDFBox)
- Performance is critical (Excel→PDF conversion is slower)

### Advantages

1. **Familiar tool** - Business users already know Excel
2. **Built-in calculations** - Formulas automatically compute
3. **Rich formatting** - Colors, borders, fonts, number formats
4. **Charts/graphs** - Native chart support
5. **What-you-see-is-what-you-get** - Preview is accurate
6. **No code changes** - Template updates without deployment
7. **Data validation** - Excel's built-in validation

### Limitations

1. **Conversion quality** - Excel→PDF not always perfect
2. **Performance** - Slower than other methods
3. **Page breaks** - Can be unpredictable
4. **Complex layouts** - Limited compared to HTML/CSS
5. **Dependencies** - Requires Apache POI (large library)
6. **Platform dependency** - Some converters need LibreOffice/Windows

## Template and Configuration Storage Strategies

Managing templates (FreeMarker, AcroForm PDF, Excel) and configurations (JSON/YAML) is crucial for maintainability, versioning, and scalability. Multiple storage strategies can be employed based on requirements.

### Storage Strategy Overview

| Strategy | Best For | Pros | Cons |
|----------|----------|------|------|
| **File System** | Development, small deployments | Simple, fast | No versioning, scaling issues |
| **Database** | Enterprise apps, auditing | Versioning, transactional | Performance overhead |
| **Cloud Storage** | Distributed systems, scalability | Scalable, durable | Network latency, cost |
| **Git Repository** | Version control, collaboration | Full history, branching | Complex integration |
| **CMS/DAM** | Business user management | UI for editing, workflows | Additional infrastructure |
| **Hybrid** | Production systems | Best of all worlds | More complex |

### 1. File System Storage

**Simple, direct file access from local or network file systems.**

```java
@Component
class FileSystemTemplateRepository implements TemplateRepository {
    
    @Value("${templates.base.path:/opt/templates}")
    private String basePath;
    
    @Override
    public InputStream loadTemplate(String templatePath) throws IOException {
        Path fullPath = Paths.get(basePath, templatePath);
        validatePath(fullPath); // Prevent path traversal attacks
        return Files.newInputStream(fullPath);
    }
    
    @Override
    public DocumentTemplate loadConfiguration(String configPath) throws IOException {
        Path fullPath = Paths.get(basePath, "configs", configPath);
        validatePath(fullPath);
        
        if (configPath.endsWith(".json")) {
            return objectMapper.readValue(fullPath.toFile(), DocumentTemplate.class);
        } else if (configPath.endsWith(".yaml") || configPath.endsWith(".yml")) {
            return yamlMapper.readValue(fullPath.toFile(), DocumentTemplate.class);
        }
        throw new UnsupportedFormatException(configPath);
    }
    
    @Override
    public void saveTemplate(String templatePath, InputStream content) throws IOException {
        Path fullPath = Paths.get(basePath, templatePath);
        validatePath(fullPath);
        Files.createDirectories(fullPath.getParent());
        Files.copy(content, fullPath, StandardCopyOption.REPLACE_EXISTING);
    }
    
    private void validatePath(Path path) throws IOException {
        Path normalizedBase = Paths.get(basePath).toRealPath();
        Path normalizedPath = path.normalize().toAbsolutePath();
        
        if (!normalizedPath.startsWith(normalizedBase)) {
            throw new SecurityException("Path traversal attempt detected");
        }
    }
}
```

**Directory Structure:**
```
/opt/templates/
├── freemarker/
│   ├── invoice/
│   │   ├── header.ftl
│   │   ├── body.ftl
│   │   └── footer.ftl
│   └── reports/
│       └── financial.ftl
├── acroforms/
│   ├── employment-application.pdf
│   └── customer-info.pdf
├── excel/
│   ├── income-statement.xlsx
│   └── balance-sheet.xlsx
├── configs/
│   ├── invoice-template-v1.yaml
│   ├── employee-package.json
│   └── financial-report.yaml
└── headers/
    └── company-header.ftl
```

**Configuration:**
```yaml
# application.yml
templates:
  base-path: /opt/templates
  cache:
    enabled: true
    ttl: 3600
```

**Pros:**
- ✅ Simple implementation
- ✅ Fast access
- ✅ Easy debugging (can view/edit files directly)
- ✅ No additional infrastructure

**Cons:**
- ❌ No built-in versioning
- ❌ Difficult to scale horizontally
- ❌ No audit trail
- ❌ Manual backup/restore
- ❌ File permissions complexity

### 2. Database Storage

**Store templates and configurations as BLOBs/CLOBs with metadata.**

```java
@Entity
@Table(name = "templates")
class TemplateEntity {
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String templateId;
    
    @Enumerated(EnumType.STRING)
    private TemplateType type; // FREEMARKER, ACROFORM, EXCEL
    
    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] content;
    
    private String fileName;
    private String contentType;
    
    @Column(nullable = false)
    private Integer version;
    
    private boolean active;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private String createdBy;
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON metadata
}

@Entity
@Table(name = "template_configurations")
class TemplateConfigEntity {
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String configId;
    
    @Enumerated(EnumType.STRING)
    private ConfigFormat format; // JSON, YAML
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String configContent;
    
    private Integer version;
    private boolean active;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private String createdBy;
}

@Repository
interface TemplateRepository extends JpaRepository<TemplateEntity, Long> {
    Optional<TemplateEntity> findByTemplateIdAndActiveTrue(String templateId);
    List<TemplateEntity> findByTemplateIdOrderByVersionDesc(String templateId);
    Optional<TemplateEntity> findByTemplateIdAndVersion(String templateId, Integer version);
}

@Service
class DatabaseTemplateService implements TemplateService {
    
    @Autowired
    private TemplateRepository templateRepository;
    
    @Autowired
    private TemplateConfigRepository configRepository;
    
    @Cacheable(value = "templates", key = "#templateId")
    public InputStream loadTemplate(String templateId) {
        TemplateEntity template = templateRepository
            .findByTemplateIdAndActiveTrue(templateId)
            .orElseThrow(() -> new TemplateNotFoundException(templateId));
        
        return new ByteArrayInputStream(template.getContent());
    }
    
    @Cacheable(value = "configs", key = "#configId")
    public DocumentTemplate loadConfiguration(String configId) {
        TemplateConfigEntity config = configRepository
            .findByConfigIdAndActiveTrue(configId)
            .orElseThrow(() -> new ConfigNotFoundException(configId));
        
        return parseConfiguration(config.getConfigContent(), config.getFormat());
    }
    
    @Transactional
    public void saveTemplate(String templateId, byte[] content, 
                            TemplateType type, String createdBy) {
        // Deactivate previous versions
        templateRepository.findByTemplateIdAndActiveTrue(templateId)
            .ifPresent(t -> {
                t.setActive(false);
                templateRepository.save(t);
            });
        
        // Create new version
        Integer newVersion = getNextVersion(templateId);
        
        TemplateEntity newTemplate = new TemplateEntity();
        newTemplate.setTemplateId(templateId);
        newTemplate.setType(type);
        newTemplate.setContent(content);
        newTemplate.setVersion(newVersion);
        newTemplate.setActive(true);
        newTemplate.setCreatedBy(createdBy);
        
        templateRepository.save(newTemplate);
    }
    
    @Transactional
    public void rollbackToVersion(String templateId, Integer version) {
        TemplateEntity targetVersion = templateRepository
            .findByTemplateIdAndVersion(templateId, version)
            .orElseThrow(() -> new VersionNotFoundException(templateId, version));
        
        // Deactivate current
        templateRepository.findByTemplateIdAndActiveTrue(templateId)
            .ifPresent(t -> {
                t.setActive(false);
                templateRepository.save(t);
            });
        
        // Activate target version
        targetVersion.setActive(true);
        templateRepository.save(targetVersion);
    }
    
    private Integer getNextVersion(String templateId) {
        return templateRepository.findByTemplateIdOrderByVersionDesc(templateId)
            .stream()
            .findFirst()
            .map(t -> t.getVersion() + 1)
            .orElse(1);
    }
}
```

**Database Schema:**
```sql
CREATE TABLE templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    content BLOB NOT NULL,
    file_name VARCHAR(255),
    content_type VARCHAR(100),
    version INT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    description TEXT,
    metadata JSON,
    INDEX idx_template_id (template_id),
    INDEX idx_active (active),
    UNIQUE KEY uk_template_version (template_id, version)
);

CREATE TABLE template_configurations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_id VARCHAR(255) UNIQUE NOT NULL,
    format VARCHAR(10) NOT NULL,
    config_content TEXT NOT NULL,
    version INT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    INDEX idx_config_id (config_id),
    INDEX idx_active (active)
);
```

**Pros:**
- ✅ Built-in versioning
- ✅ Transactional integrity
- ✅ Easy auditing
- ✅ Centralized management
- ✅ Role-based access control
- ✅ Backup with database

**Cons:**
- ❌ Performance overhead
- ❌ Database size growth
- ❌ Complex queries for large BLOBs
- ❌ Less intuitive for developers

### 3. Cloud Storage (S3, Azure Blob, GCS)

**Scalable, durable storage for distributed systems.**

```java
@Component
class S3TemplateRepository implements TemplateRepository {
    
    @Autowired
    private AmazonS3 s3Client;
    
    @Value("${aws.s3.bucket.templates}")
    private String bucketName;
    
    @Override
    public InputStream loadTemplate(String templatePath) {
        String s3Key = "templates/" + templatePath;
        S3Object s3Object = s3Client.getObject(bucketName, s3Key);
        return s3Object.getObjectContent();
    }
    
    @Override
    public DocumentTemplate loadConfiguration(String configPath) {
        String s3Key = "configs/" + configPath;
        S3Object s3Object = s3Client.getObject(bucketName, s3Key);
        
        try (InputStream is = s3Object.getObjectContent()) {
            if (configPath.endsWith(".json")) {
                return objectMapper.readValue(is, DocumentTemplate.class);
            } else {
                return yamlMapper.readValue(is, DocumentTemplate.class);
            }
        } catch (IOException e) {
            throw new ConfigLoadException("Failed to load config", e);
        }
    }
    
    @Override
    public void saveTemplate(String templatePath, InputStream content, 
                           TemplateMetadata metadata) {
        String s3Key = "templates/" + templatePath;
        
        ObjectMetadata s3Metadata = new ObjectMetadata();
        s3Metadata.setContentType(metadata.getContentType());
        s3Metadata.addUserMetadata("template-type", metadata.getType());
        s3Metadata.addUserMetadata("version", metadata.getVersion().toString());
        
        // Enable versioning
        PutObjectRequest request = new PutObjectRequest(bucketName, s3Key, content, s3Metadata);
        s3Client.putObject(request);
    }
    
    @Override
    public List<TemplateVersion> listVersions(String templatePath) {
        String s3Key = "templates/" + templatePath;
        
        ListVersionsRequest request = new ListVersionsRequest()
            .withBucketName(bucketName)
            .withPrefix(s3Key);
        
        VersionListing versionListing = s3Client.listVersions(request);
        
        return versionListing.getVersionSummaries().stream()
            .map(this::toTemplateVersion)
            .collect(Collectors.toList());
    }
    
    @Override
    public InputStream loadTemplateVersion(String templatePath, String versionId) {
        String s3Key = "templates/" + templatePath;
        GetObjectRequest request = new GetObjectRequest(bucketName, s3Key, versionId);
        S3Object s3Object = s3Client.getObject(request);
        return s3Object.getObjectContent();
    }
}
```

**S3 Bucket Structure:**
```
s3://my-templates-bucket/
├── templates/
│   ├── freemarker/
│   │   ├── invoice/header.ftl (v1, v2, v3...)
│   │   └── invoice/body.ftl
│   ├── acroforms/
│   │   └── employment.pdf
│   └── excel/
│       └── financial.xlsx
├── configs/
│   ├── invoice-template-v1.yaml
│   └── employee-package.json
└── headers/
    └── company-header.ftl
```

**Configuration:**
```yaml
# application.yml
aws:
  s3:
    bucket:
      templates: my-templates-bucket
    region: us-east-1
  credentials:
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}

cloud:
  storage:
    cache:
      enabled: true
      ttl: 3600
      local-path: /tmp/template-cache
```

**Pros:**
- ✅ Highly scalable
- ✅ Built-in versioning (S3 versioning)
- ✅ Durable (99.999999999% durability)
- ✅ CDN integration
- ✅ Cross-region replication
- ✅ Pay-as-you-go

**Cons:**
- ❌ Network latency
- ❌ Costs for frequent access
- ❌ Requires internet connectivity
- ❌ Cloud vendor lock-in

### 4. Git Repository Storage

**Version control with full history and branching.**

```java
@Component
class GitTemplateRepository implements TemplateRepository {
    
    @Value("${git.templates.repo.url}")
    private String repoUrl;
    
    @Value("${git.templates.local.path}")
    private String localPath;
    
    @Value("${git.templates.branch:main}")
    private String branch;
    
    private Git git;
    
    @PostConstruct
    public void initialize() throws GitAPIException {
        File repoDir = new File(localPath);
        
        if (!repoDir.exists()) {
            // Clone repository
            git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(repoDir)
                .setBranch(branch)
                .call();
        } else {
            // Open existing repository
            git = Git.open(repoDir);
            pullLatest();
        }
    }
    
    @Scheduled(fixedRate = 300000) // Refresh every 5 minutes
    public void pullLatest() {
        try {
            git.pull()
                .setRemoteBranchName(branch)
                .call();
        } catch (GitAPIException e) {
            logger.error("Failed to pull latest templates", e);
        }
    }
    
    @Override
    public InputStream loadTemplate(String templatePath) throws IOException {
        pullLatest(); // Ensure we have latest
        Path fullPath = Paths.get(localPath, templatePath);
        return Files.newInputStream(fullPath);
    }
    
    @Override
    public DocumentTemplate loadConfiguration(String configPath) throws IOException {
        pullLatest();
        Path fullPath = Paths.get(localPath, "configs", configPath);
        
        if (configPath.endsWith(".json")) {
            return objectMapper.readValue(fullPath.toFile(), DocumentTemplate.class);
        } else {
            return yamlMapper.readValue(fullPath.toFile(), DocumentTemplate.class);
        }
    }
    
    @Override
    public void saveTemplate(String templatePath, InputStream content, 
                           String commitMessage, String author) throws IOException, GitAPIException {
        Path fullPath = Paths.get(localPath, templatePath);
        Files.createDirectories(fullPath.getParent());
        Files.copy(content, fullPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Git add
        git.add()
            .addFilepattern(templatePath)
            .call();
        
        // Git commit
        git.commit()
            .setMessage(commitMessage)
            .setAuthor(author, author + "@example.com")
            .call();
        
        // Git push
        git.push()
            .setRemote("origin")
            .call();
    }
    
    @Override
    public List<TemplateVersion> listVersions(String templatePath) throws GitAPIException {
        Iterable<RevCommit> commits = git.log()
            .addPath(templatePath)
            .call();
        
        List<TemplateVersion> versions = new ArrayList<>();
        for (RevCommit commit : commits) {
            versions.add(new TemplateVersion(
                commit.getName(),
                commit.getShortMessage(),
                commit.getAuthorIdent().getName(),
                commit.getCommitTime()
            ));
        }
        return versions;
    }
    
    @Override
    public InputStream loadTemplateVersion(String templatePath, String commitHash) 
            throws IOException, GitAPIException {
        // Checkout specific commit
        Repository repository = git.getRepository();
        ObjectId commitId = repository.resolve(commitHash);
        
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(templatePath));
                
                if (!treeWalk.next()) {
                    throw new FileNotFoundException("Template not found in commit");
                }
                
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);
                return loader.openStream();
            }
        }
    }
}
```

**Git Repository Structure:**
```
templates-repo/
├── freemarker/
│   └── invoice/
│       ├── header.ftl
│       └── body.ftl
├── acroforms/
│   └── employment.pdf
├── excel/
│   └── financial.xlsx
├── configs/
│   ├── invoice-template-v1.yaml
│   └── employee-package.json
├── README.md
└── .gitignore
```

**Configuration:**
```yaml
# application.yml
git:
  templates:
    repo:
      url: https://github.com/myorg/document-templates.git
      credentials:
        username: ${GIT_USERNAME}
        password: ${GIT_TOKEN}
    local-path: /opt/templates-repo
    branch: main
    auto-pull:
      enabled: true
      interval: 300000 # 5 minutes
```

**Pros:**
- ✅ Full version history
- ✅ Branching for testing
- ✅ Pull requests for review
- ✅ Merge conflict resolution
- ✅ Collaboration features
- ✅ Audit trail with commit messages

**Cons:**
- ❌ Binary file handling (large PDFs/Excel)
- ❌ Complex integration
- ❌ Git expertise required
- ❌ Merge conflicts on concurrent edits

### 5. Hybrid Approach (Recommended for Production)

**Combine multiple strategies for optimal performance and flexibility.**

```java
@Component
class HybridTemplateRepository implements TemplateRepository {
    
    private final CloudStorageRepository cloudStorage;  // Primary
    private final DatabaseRepository databaseBackup;     // Secondary/Audit
    private final LocalCacheRepository localCache;       // Fast access
    
    @Override
    public InputStream loadTemplate(String templatePath) throws IOException {
        // 1. Try local cache first
        Optional<InputStream> cached = localCache.get(templatePath);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // 2. Load from cloud storage
        InputStream cloudStream = cloudStorage.loadTemplate(templatePath);
        byte[] content = IOUtils.toByteArray(cloudStream);
        
        // 3. Cache locally
        localCache.put(templatePath, content);
        
        return new ByteArrayInputStream(content);
    }
    
    @Override
    @Transactional
    public void saveTemplate(String templatePath, InputStream content, 
                           TemplateMetadata metadata) throws IOException {
        byte[] contentBytes = IOUtils.toByteArray(content);
        
        // 1. Save to cloud storage (primary)
        cloudStorage.saveTemplate(templatePath, 
            new ByteArrayInputStream(contentBytes), metadata);
        
        // 2. Save to database (backup + audit)
        databaseBackup.saveTemplate(templatePath, 
            new ByteArrayInputStream(contentBytes), metadata);
        
        // 3. Invalidate cache
        localCache.invalidate(templatePath);
        
        // 4. Publish event for other instances
        eventPublisher.publishEvent(new TemplateUpdatedEvent(templatePath));
    }
}

@Component
class LocalCacheRepository {
    
    @Autowired
    private CacheManager cacheManager;
    
    public Optional<InputStream> get(String key) {
        Cache cache = cacheManager.getCache("templates");
        if (cache != null) {
            byte[] content = cache.get(key, byte[].class);
            if (content != null) {
                return Optional.of(new ByteArrayInputStream(content));
            }
        }
        return Optional.empty();
    }
    
    public void put(String key, byte[] content) {
        Cache cache = cacheManager.getCache("templates");
        if (cache != null) {
            cache.put(key, content);
        }
    }
    
    public void invalidate(String key) {
        Cache cache = cacheManager.getCache("templates");
        if (cache != null) {
            cache.evict(key);
        }
    }
}
```

**Hybrid Strategy Configuration:**
```yaml
# application.yml
template:
  storage:
    primary: CLOUD_STORAGE     # S3, Azure Blob, GCS
    backup: DATABASE            # For audit and recovery
    cache:
      enabled: true
      type: REDIS               # Distributed cache
      ttl: 3600
      max-size: 1000
    
cloud:
  storage:
    provider: AWS_S3
    bucket: production-templates
    region: us-east-1
    
database:
  audit:
    enabled: true
    retention-days: 365
    
git:
  integration:
    enabled: true
    sync-on-startup: true
    webhook-url: /api/templates/git-webhook
```

### Strategy Selection Matrix

| Scenario | Recommended Strategy |
|----------|---------------------|
| **Small application** | File System |
| **Enterprise with audit** | Database or Hybrid |
| **Multi-region deployment** | Cloud Storage (S3/Azure/GCS) |
| **Team collaboration** | Git Repository |
| **High performance** | Hybrid (Cloud + Cache) |
| **Compliance/Audit** | Database or Hybrid |
| **Version control** | Git or Database |
| **Business user editing** | CMS with Database backend |

### Caching Strategy

```java
@Configuration
@EnableCaching
class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .withCacheConfiguration("templates", config.entryTtl(Duration.ofHours(24)))
            .withCacheConfiguration("configs", config.entryTtl(Duration.ofHours(12)))
            .build();
    }
}
```

### 6. Spring Cloud Config Service

**Centralized, versioned configuration management for distributed systems.**

Spring Cloud Config provides server-side and client-side support for externalized configuration in a distributed system. Perfect for managing template configurations across multiple environments and services.

#### Architecture

```
┌─────────────────────────────────────────┐
│     Spring Cloud Config Server          │
│  ┌────────────────────────────────────┐ │
│  │  Git Backend (configs repo)        │ │
│  │  - invoice-template-v1.yml         │ │
│  │  - employee-package.yml            │ │
│  │  - financial-report.json           │ │
│  └────────────────────────────────────┘ │
└───────────────┬─────────────────────────┘
                │ REST API
    ┌───────────┼───────────┐
    │           │           │
┌───▼────┐  ┌──▼─────┐  ┌──▼─────┐
│ Doc    │  │ Doc    │  │ Doc    │
│ Gen    │  │ Gen    │  │ Gen    │
│ Svc 1  │  │ Svc 2  │  │ Svc 3  │
└────────┘  └────────┘  └────────┘
```

#### Implementation

**1. Config Server Setup**

```xml
<!-- pom.xml for Config Server -->
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-config-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
</dependencies>
```

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
# application.yml for Config Server
server:
  port: 8888

spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/myorg/document-templates-config
          clone-on-start: true
          default-label: main
          search-paths:
            - configs/{application}
            - templates
        # Alternative: Native file system
        native:
          search-locations:
            - file:/opt/config-repo
  security:
    user:
      name: config-admin
      password: ${CONFIG_SERVER_PASSWORD}
```

**2. Client Configuration**

```xml
<!-- pom.xml for Document Generation Service -->
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-config</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

```yaml
# bootstrap.yml (loads before application.yml)
spring:
  application:
    name: document-generation-service
  cloud:
    config:
      uri: http://localhost:8888
      username: config-admin
      password: ${CONFIG_SERVER_PASSWORD}
      fail-fast: true
      retry:
        max-attempts: 6
        max-interval: 2000
  profiles:
    active: production

# Enable refresh endpoint
management:
  endpoints:
    web:
      exposure:
        include: refresh,health,info
```

**3. Template Configuration Service**

```java
@Component
@RefreshScope  // Enables dynamic refresh without restart
public class SpringCloudTemplateConfigService implements TemplateConfigService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${spring.cloud.config.uri}")
    private String configServerUrl;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private YAMLMapper yamlMapper;
    
    @Cacheable(value = "template-configs", key = "#configId")
    public DocumentTemplate loadConfiguration(String configId) {
        // Fetch from Config Server
        String url = String.format("%s/%s/%s/%s.yml", 
            configServerUrl, 
            "document-generation-service",
            getActiveProfile(),
            configId);
        
        try {
            ResponseEntity<String> response = restTemplate
                .withBasicAuth("config-admin", configPassword)
                .getForEntity(url, String.class);
            
            String configContent = response.getBody();
            
            // Parse based on extension
            if (configId.endsWith(".json")) {
                return objectMapper.readValue(configContent, DocumentTemplate.class);
            } else {
                return yamlMapper.readValue(configContent, DocumentTemplate.class);
            }
            
        } catch (Exception e) {
            throw new ConfigLoadException("Failed to load config from Cloud Config", e);
        }
    }
    
    @CacheEvict(value = "template-configs", allEntries = true)
    public void refreshConfigurations() {
        // Triggered by /actuator/refresh endpoint
        logger.info("Template configurations refreshed from Cloud Config");
    }
    
    private String getActiveProfile() {
        return environment.getActiveProfiles().length > 0 
            ? environment.getActiveProfiles()[0] 
            : "default";
    }
}
```

**4. Git Repository Structure**

```
document-templates-config/
├── configs/
│   ├── document-generation-service/
│   │   ├── invoice-template-v1.yml
│   │   ├── employee-package.yml
│   │   ├── financial-report.yml
│   │   └── quarterly-report.json
│   └── document-generation-service-dev/
│       └── test-invoice.yml
├── templates/
│   └── metadata.yml
└── README.md
```

**5. Environment-Specific Configurations**

**Production:** `configs/document-generation-service/invoice-template-v1.yml`
```yaml
templateId: invoice-template-v1
environment: production
sections:
  - sectionId: company-header
    type: FREEMARKER
    templatePath: s3://prod-templates/freemarker/invoice/header.ftl
    # ... rest of config
```

**Development:** `configs/document-generation-service-dev/invoice-template-v1.yml`
```yaml
templateId: invoice-template-v1
environment: development
sections:
  - sectionId: company-header
    type: FREEMARKER
    templatePath: file:/opt/dev-templates/freemarker/invoice/header.ftl
    # ... rest of config
```

**6. Dynamic Configuration Refresh**

```java
@RestController
@RequestMapping("/api/admin/templates")
public class TemplateAdminController {
    
    @Autowired
    private SpringCloudTemplateConfigService configService;
    
    @Autowired
    private ContextRefresher contextRefresher;
    
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshTemplateConfigs() {
        // Trigger refresh from Config Server
        Set<String> refreshedKeys = contextRefresher.refresh();
        
        // Clear template config cache
        configService.refreshConfigurations();
        
        return ResponseEntity.ok("Refreshed keys: " + refreshedKeys);
    }
    
    @GetMapping("/config/{configId}")
    public ResponseEntity<DocumentTemplate> getConfig(@PathVariable String configId) {
        DocumentTemplate config = configService.loadConfiguration(configId);
        return ResponseEntity.ok(config);
    }
}
```

**Refresh without restart:**
```bash
# Git commit: Update invoice template config
git commit -m "Update invoice template"
git push

# Trigger refresh on all instances
curl -X POST http://localhost:8080/actuator/refresh
```

**7. Hybrid: Cloud Config + Template Storage**

```java
@Component
public class HybridCloudConfigTemplateService {
    
    @Autowired
    private SpringCloudTemplateConfigService cloudConfigService;
    
    @Autowired
    private S3TemplateRepository s3Repository;
    
    @Autowired
    private DatabaseTemplateRepository dbRepository;
    
    public byte[] generateDocument(String configId, Map<String, Object> data) {
        // 1. Load configuration from Spring Cloud Config
        DocumentTemplate template = cloudConfigService.loadConfiguration(configId);
        
        // 2. Load actual template files from S3/Database
        for (PageSection section : template.getSections()) {
            if (section.getType() == SectionType.FREEMARKER) {
                section.setTemplateContent(
                    s3Repository.loadTemplate(section.getTemplatePath())
                );
            } else if (section.getType() == SectionType.ACROFORM) {
                section.setTemplateContent(
                    dbRepository.loadTemplate(section.getFormTemplatePath())
                );
            }
        }
        
        // 3. Generate document
        return documentComposer.generateDocument(template);
    }
}
```

#### Configuration Benefits with Spring Cloud Config

**1. Centralized Management**
- Single source of truth for all configurations
- Easy to find and update configurations
- Consistent across all service instances

**2. Environment Isolation**
```yaml
# Different configs per environment automatically
/{application}/{profile}/invoice-template-v1.yml

# Production
/document-generation-service/production/invoice-template-v1.yml

# Development
/document-generation-service/development/invoice-template-v1.yml

# Testing
/document-generation-service/test/invoice-template-v1.yml
```

**3. Version Control**
- Full Git history of all configuration changes
- Who changed what and when
- Easy rollback to previous versions
- Branch-based testing of new configs

**4. Dynamic Updates**
- Update configurations without redeploying
- Refresh endpoint triggers reload
- No service downtime
- Gradual rollout possible

**5. Security**
- Encrypted properties support
- OAuth2/JWT authentication
- Role-based access control via Git
- Audit trail through Git commits

**6. Scalability**
- Config Server can be clustered
- Caching reduces load
- Works in microservices architecture
- Service discovery integration

**7. Multi-tenancy Support**
```yaml
# Tenant-specific configurations
/{application}/{tenant}/{config}.yml

# Tenant A
/document-service/tenant-a/invoice-template.yml

# Tenant B  
/document-service/tenant-b/invoice-template.yml
```

#### Advanced Features

**1. Encryption**
```yaml
# Encrypted sensitive data in configs
templateId: invoice-template-v1
database:
  password: '{cipher}AQATzPPxxx...'  # Encrypted
aws:
  secretKey: '{cipher}AQBQRzzyyy...'  # Encrypted
```

**2. Profiles and Labels**
```bash
# Access specific Git branch/tag
GET /document-service/production/invoice-template.yml?label=v2.0.0

# Multiple profiles
GET /document-service/production,eu-region/invoice-template.yml
```

**3. Webhooks for Auto-Refresh**
```java
@RestController
@RequestMapping("/webhook")
public class GitWebhookController {
    
    @Autowired
    private ContextRefresher contextRefresher;
    
    @PostMapping("/git-push")
    public ResponseEntity<Void> handleGitPush(@RequestBody GitPushEvent event) {
        // Validate webhook signature
        if (isValidWebhook(event)) {
            // Auto-refresh when config repo is updated
            contextRefresher.refresh();
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
```

#### Comparison: Spring Cloud Config vs Other Strategies

| Aspect | Spring Cloud Config | Database | File System | Cloud Storage |
|--------|---------------------|----------|-------------|---------------|
| **Centralization** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐ |
| **Version Control** | ⭐⭐⭐⭐⭐ (Git) | ⭐⭐⭐ | ❌ | ⭐⭐⭐⭐ (S3) |
| **Dynamic Refresh** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| **Environment Mgmt** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| **Setup Complexity** | ⭐⭐⭐ Medium | ⭐⭐ Easy | ⭐ Very Easy | ⭐⭐⭐ Medium |
| **Performance** | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Microservices** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐ | ⭐⭐⭐⭐ |
| **Audit Trail** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ❌ | ⭐⭐ |
| **Binary Templates** | ❌ Configs only | ✅ | ✅ | ✅ |

#### When to Use Spring Cloud Config

**✅ Use Spring Cloud Config when:**
- **Microservices architecture** - Multiple services need same configs
- **Multiple environments** - Dev, Test, Staging, Production
- **Frequent config changes** - Need dynamic updates without restart
- **Team collaboration** - Multiple teams updating configurations
- **Compliance** - Need audit trail and approval workflow
- **Multi-tenancy** - Different configs per tenant/customer
- **Complex deployments** - Kubernetes, multi-region setups

**❌ Don't use Spring Cloud Config when:**
- **Monolithic application** - Single service, simple deployment
- **Static configurations** - Configs rarely change
- **Binary templates** - Need to store PDF/Excel files (use hybrid)
- **Simple prototypes** - Overhead not worth it
- **No Git infrastructure** - Team not comfortable with Git

#### Recommended Production Setup

**Best Practice: Hybrid Approach**

```yaml
# Store in Spring Cloud Config (Git):
# - Template metadata/configuration (YAML/JSON)
# - Field mappings
# - Section definitions
# - Environment-specific settings

# Store in S3/Database:
# - Actual template files (FreeMarker .ftl files)
# - PDF forms (AcroForm templates)
# - Excel templates
# - Images, logos, fonts

# Use Redis/Caffeine:
# - Cache both configs and templates
# - Reduce load on Config Server and storage
```

**Architecture:**
```
┌──────────────────┐
│ Spring Cloud     │──> Git Repo (Configs)
│ Config Server    │
└─────────┬────────┘
          │ Configuration
┌─────────▼────────────────────────┐
│ Document Generation Service      │
│  ┌────────────────────────────┐  │
│  │ Config: Spring Cloud       │  │
│  │ Templates: S3              │  │
│  │ Cache: Redis               │  │
│  │ Audit: Database            │  │
│  └────────────────────────────┘  │
└───────────────────────────────────┘
```

This hybrid approach provides:
- ✅ Config management via Spring Cloud Config
- ✅ Binary template storage via S3/Database
- ✅ Fast access via caching
- ✅ Full audit trail
- ✅ Version control for configs
- ✅ Dynamic updates without restart

### Best Practices

1. **Use caching** - Always cache frequently accessed templates
2. **Version everything** - Track changes for rollback capability
3. **Separate environments** - Dev/Test/Prod template isolation
4. **Access control** - Implement proper authentication/authorization
5. **Backup strategy** - Regular backups of templates and configs
6. **Monitoring** - Track template load times and cache hit rates
7. **Validation** - Validate templates before saving
8. **Documentation** - Document template structure and fields

## FreeMarker Template Design Patterns

### ViewModel Pattern (Recommended)

**Problem:** FreeMarker templates with complex logic, calculations, and formatting become difficult to maintain and test.

**Solution:** Use the ViewModel pattern to pre-process and prepare data specifically for template rendering, keeping templates simple and declarative.

### Without ViewModel (Anti-Pattern)

```html
<!-- invoice.ftl - Complex logic in template -->
<!DOCTYPE html>
<html>
<head>
    <title>Invoice ${invoice.invoiceNumber}</title>
</head>
<body>
    <h1>Invoice</h1>
    
    <!-- Complex date formatting -->
    <p>Date: ${invoice.date?string["MM/dd/yyyy"]}</p>
    
    <!-- Conditional logic -->
    <#if invoice.customer??>
        <#if invoice.customer.type == "CORPORATE">
            <p>Company: ${invoice.customer.companyName}</p>
            <p>Tax ID: ${invoice.customer.taxId}</p>
        <#else>
            <p>Customer: ${invoice.customer.firstName} ${invoice.customer.lastName}</p>
        </#if>
    </#if>
    
    <!-- Complex calculations -->
    <table>
        <#assign subtotal = 0>
        <#list invoice.items as item>
            <#assign itemTotal = item.quantity * item.unitPrice>
            <#assign subtotal = subtotal + itemTotal>
            <tr>
                <td>${item.description}</td>
                <td>${item.quantity}</td>
                <td>${item.unitPrice?string.currency}</td>
                <td>${itemTotal?string.currency}</td>
            </tr>
        </#list>
    </table>
    
    <!-- More calculations -->
    <#assign taxRate = 0.08>
    <#assign taxAmount = subtotal * taxRate>
    <#assign total = subtotal + taxAmount>
    
    <p>Subtotal: ${subtotal?string.currency}</p>
    <p>Tax (8%): ${taxAmount?string.currency}</p>
    <p>Total: ${total?string.currency}</p>
    
    <!-- Status formatting -->
    <#if invoice.isPaid>
        <p class="paid">PAID</p>
    <#elseif invoice.dueDate??>
        <#assign today = .now>
        <#if invoice.dueDate?date < today?date>
            <p class="overdue">OVERDUE</p>
        <#else>
            <p class="pending">PENDING</p>
        </#if>
    </#if>
</body>
</html>
```

**Issues:**
- ❌ Complex business logic in template
- ❌ Calculations mixed with presentation
- ❌ Hard to test logic
- ❌ Difficult to maintain
- ❌ Cannot unit test template logic
- ❌ Logic duplicated across templates

### With ViewModel Pattern (Best Practice)

**ViewModel Class:**
```java
// ViewModel - Pre-processed data for template
public class InvoiceViewModel {
    // Pre-formatted strings
    private String invoiceNumber;
    private String invoiceDate;
    private String customerName;
    private String customerDetails;
    private boolean showTaxId;
    private String taxId;
    
    // Pre-calculated values
    private String subtotal;
    private String taxAmount;
    private String taxRate;
    private String total;
    
    // Pre-processed items
    private List<InvoiceItemViewModel> items;
    
    // Pre-determined status
    private String statusLabel;
    private String statusClass;
    private boolean isPaid;
    
    // Additional display logic
    private String paymentInstructions;
    private boolean showDiscountMessage;
    
    // Getters...
}

public class InvoiceItemViewModel {
    private String description;
    private String quantity;
    private String unitPrice;
    private String total;
    
    // Getters...
}

// ViewModel Builder/Mapper
@Component
public class InvoiceViewModelBuilder {
    
    private final DateFormatter dateFormatter;
    private final CurrencyFormatter currencyFormatter;
    private final TaxCalculator taxCalculator;
    
    public InvoiceViewModel build(Invoice invoice) {
        InvoiceViewModel vm = new InvoiceViewModel();
        
        // Format dates
        vm.setInvoiceNumber(invoice.getInvoiceNumber());
        vm.setInvoiceDate(dateFormatter.format(invoice.getDate(), "MM/dd/yyyy"));
        
        // Build customer details
        vm.setCustomerName(buildCustomerName(invoice.getCustomer()));
        vm.setCustomerDetails(buildCustomerDetails(invoice.getCustomer()));
        vm.setShowTaxId(invoice.getCustomer().getType() == CustomerType.CORPORATE);
        if (vm.isShowTaxId()) {
            vm.setTaxId(invoice.getCustomer().getTaxId());
        }
        
        // Calculate totals
        BigDecimal subtotal = invoice.getItems().stream()
            .map(item -> item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal taxAmount = taxCalculator.calculate(subtotal, invoice.getTaxRate());
        BigDecimal total = subtotal.add(taxAmount);
        
        vm.setSubtotal(currencyFormatter.format(subtotal));
        vm.setTaxAmount(currencyFormatter.format(taxAmount));
        vm.setTaxRate(invoice.getTaxRate().multiply(new BigDecimal(100)) + "%");
        vm.setTotal(currencyFormatter.format(total));
        
        // Build items
        vm.setItems(invoice.getItems().stream()
            .map(this::buildItemViewModel)
            .collect(Collectors.toList()));
        
        // Determine status
        InvoiceStatus status = determineInvoiceStatus(invoice);
        vm.setStatusLabel(status.getLabel());
        vm.setStatusClass(status.getCssClass());
        vm.setIsPaid(status == InvoiceStatus.PAID);
        
        // Additional logic
        vm.setPaymentInstructions(buildPaymentInstructions(invoice));
        vm.setShowDiscountMessage(shouldShowDiscountMessage(invoice));
        
        return vm;
    }
    
    private InvoiceItemViewModel buildItemViewModel(InvoiceItem item) {
        InvoiceItemViewModel vm = new InvoiceItemViewModel();
        vm.setDescription(item.getDescription());
        vm.setQuantity(String.valueOf(item.getQuantity()));
        vm.setUnitPrice(currencyFormatter.format(item.getUnitPrice()));
        
        BigDecimal total = item.getUnitPrice()
            .multiply(new BigDecimal(item.getQuantity()));
        vm.setTotal(currencyFormatter.format(total));
        
        return vm;
    }
    
    private String buildCustomerName(Customer customer) {
        if (customer.getType() == CustomerType.CORPORATE) {
            return customer.getCompanyName();
        } else {
            return customer.getFirstName() + " " + customer.getLastName();
        }
    }
    
    private String buildCustomerDetails(Customer customer) {
        StringBuilder sb = new StringBuilder();
        sb.append(customer.getAddress().getStreet()).append(", ");
        sb.append(customer.getAddress().getCity()).append(", ");
        sb.append(customer.getAddress().getState()).append(" ");
        sb.append(customer.getAddress().getZipCode());
        return sb.toString();
    }
    
    private InvoiceStatus determineInvoiceStatus(Invoice invoice) {
        if (invoice.isPaid()) {
            return InvoiceStatus.PAID;
        } else if (invoice.getDueDate() != null && 
                   invoice.getDueDate().isBefore(LocalDate.now())) {
            return InvoiceStatus.OVERDUE;
        } else {
            return InvoiceStatus.PENDING;
        }
    }
    
    private String buildPaymentInstructions(Invoice invoice) {
        if (invoice.isPaid()) {
            return "Thank you for your payment!";
        } else if (invoice.getDueDate() != null) {
            return "Payment due by " + dateFormatter.format(invoice.getDueDate(), "MM/dd/yyyy");
        } else {
            return "Payment terms: Net 30";
        }
    }
    
    private boolean shouldShowDiscountMessage(Invoice invoice) {
        return !invoice.isPaid() && 
               invoice.getDueDate() != null &&
               invoice.getDueDate().isAfter(LocalDate.now().plusDays(10));
    }
}

enum InvoiceStatus {
    PAID("Paid", "status-paid"),
    PENDING("Pending", "status-pending"),
    OVERDUE("Overdue", "status-overdue");
    
    private final String label;
    private final String cssClass;
    
    InvoiceStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }
    
    public String getLabel() { return label; }
    public String getCssClass() { return cssClass; }
}
```

**Clean Template:**
```html
<!-- invoice.ftl - Simple, declarative template -->
<!DOCTYPE html>
<html>
<head>
    <title>Invoice ${invoiceNumber}</title>
    <style>
        .status-paid { color: green; }
        .status-pending { color: orange; }
        .status-overdue { color: red; }
    </style>
</head>
<body>
    <h1>Invoice ${invoiceNumber}</h1>
    
    <p>Date: ${invoiceDate}</p>
    
    <!-- Simple conditional - no complex logic -->
    <div class="customer-info">
        <p><strong>${customerName}</strong></p>
        <p>${customerDetails}</p>
        <#if showTaxId>
            <p>Tax ID: ${taxId}</p>
        </#if>
    </div>
    
    <!-- Simple iteration - no calculations -->
    <table>
        <thead>
            <tr>
                <th>Description</th>
                <th>Quantity</th>
                <th>Unit Price</th>
                <th>Total</th>
            </tr>
        </thead>
        <tbody>
            <#list items as item>
            <tr>
                <td>${item.description}</td>
                <td>${item.quantity}</td>
                <td>${item.unitPrice}</td>
                <td>${item.total}</td>
            </tr>
            </#list>
        </tbody>
    </table>
    
    <!-- Pre-calculated values -->
    <div class="totals">
        <p>Subtotal: ${subtotal}</p>
        <p>Tax (${taxRate}): ${taxAmount}</p>
        <p><strong>Total: ${total}</strong></p>
    </div>
    
    <!-- Pre-determined status -->
    <p class="${statusClass}">${statusLabel}</p>
    
    <!-- Pre-built instructions -->
    <p class="payment-instructions">${paymentInstructions}</p>
    
    <#if showDiscountMessage>
        <p class="discount-message">Pay within 10 days for 2% discount!</p>
    </#if>
</body>
</html>
```

**FreeMarker Renderer with ViewModel:**
```java
@Component
class FreeMarkerSectionRenderer implements SectionRenderer {
    
    @Autowired
    private Configuration freemarkerConfig;
    
    @Autowired
    private ViewModelFactory viewModelFactory;
    
    @Override
    public PDDocument render(PageSection section, RenderContext context) {
        try {
            // 1. Build ViewModel from raw data
            Object viewModel = viewModelFactory.create(
                section.getData(), 
                section.getViewModelType()
            );
            
            // 2. Process template with ViewModel
            Template template = freemarkerConfig.getTemplate(section.getTemplatePath());
            String html = processTemplate(template, viewModel);
            
            // 3. Convert HTML to PDF
            PDDocument pdf = htmlToPdfConverter.convert(html);
            
            return pdf;
            
        } catch (Exception e) {
            throw new TemplateRenderException("Failed to render FreeMarker template", e);
        }
    }
    
    private String processTemplate(Template template, Object viewModel) 
            throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        template.process(viewModel, writer);
        return writer.toString();
    }
}

// ViewModel Factory
@Component
class ViewModelFactory {
    
    private Map<String, ViewModelBuilder> builders = new HashMap<>();
    
    @PostConstruct
    public void registerBuilders() {
        builders.put("InvoiceViewModel", invoiceViewModelBuilder);
        builders.put("EmployeeViewModel", employeeViewModelBuilder);
        builders.put("ReportViewModel", reportViewModelBuilder);
    }
    
    public Object create(Map<String, Object> data, String viewModelType) {
        ViewModelBuilder builder = builders.get(viewModelType);
        if (builder == null) {
            // Default: use data as-is
            return data;
        }
        return builder.build(data);
    }
}

interface ViewModelBuilder<T> {
    T build(Map<String, Object> rawData);
}
```

**Configuration:**
```yaml
sectionId: invoice-section
type: FREEMARKER
order: 1
templatePath: templates/invoice.ftl
viewModelType: InvoiceViewModel  # Specifies which ViewModel to use
data:
  invoiceNumber: "INV-2026-001"
  date: "2026-01-15T10:30:00"
  customer:
    type: "CORPORATE"
    companyName: "ACME Corp"
    taxId: "12-3456789"
    address:
      street: "123 Main St"
      city: "Boston"
      state: "MA"
      zipCode: "02101"
  items:
    - description: "Consulting Services"
      quantity: 10
      unitPrice: 150.00
    - description: "Software License"
      quantity: 5
      unitPrice: 200.00
  taxRate: 0.08
  isPaid: false
  dueDate: "2026-02-14"
```

### Benefits of ViewModel Pattern

**1. Separation of Concerns**
- Business logic in Java (testable)
- Presentation logic in template (declarative)
- Clear boundary between layers

**2. Testability**
```java
@Test
void testInvoiceViewModelBuilder() {
    // Given
    Invoice invoice = createTestInvoice();
    invoice.getItems().add(new InvoiceItem("Service", 10, new BigDecimal("150.00")));
    
    // When
    InvoiceViewModel vm = invoiceViewModelBuilder.build(invoice);
    
    // Then
    assertEquals("$1,500.00", vm.getSubtotal());
    assertEquals("$120.00", vm.getTaxAmount());
    assertEquals("$1,620.00", vm.getTotal());
    assertEquals("8%", vm.getTaxRate());
    assertEquals("Pending", vm.getStatusLabel());
}
```

**3. Reusability**
```java
// Same ViewModel can be used for different templates
InvoiceViewModel vm = invoiceViewModelBuilder.build(invoice);

// Email template
emailService.send(vm, "email/invoice.ftl");

// PDF template
pdfService.generate(vm, "pdf/invoice.ftl");

// Web view template
return "invoice/view"; // Spring MVC with same ViewModel
```

**4. Type Safety**
```java
// ViewModel provides compile-time type checking
public class InvoiceViewModel {
    private String total; // Always a formatted string
    private boolean isPaid; // Always a boolean
    
    // No runtime type surprises in templates
}
```

**5. Performance**
```java
// Expensive calculations done once
public InvoiceViewModel build(Invoice invoice) {
    // Complex calculation done once
    BigDecimal total = complexTaxCalculation(invoice);
    
    // Formatted once, reused many times
    vm.setTotal(currencyFormatter.format(total));
    
    return vm; // Can be cached
}
```

**6. Consistency**
```java
// Formatting logic centralized
@Component
class CurrencyFormatter {
    public String format(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }
}

// All templates use same formatting
// No inconsistencies like "$1,500.00" vs "1500.0"
```

### Advanced ViewModel Patterns

**Nested ViewModels:**
```java
public class OrderViewModel {
    private CustomerViewModel customer;
    private List<OrderItemViewModel> items;
    private ShippingViewModel shipping;
    private PaymentViewModel payment;
    private TotalsViewModel totals;
}
```

**Conditional Sections:**
```java
public class InvoiceViewModel {
    private boolean showDiscount;
    private DiscountViewModel discount;
    
    private boolean showLateFee;
    private LateFeeViewModel lateFee;
    
    private boolean showPaymentHistory;
    private List<PaymentViewModel> payments;
}
```

**Localization Support:**
```java
@Component
class LocalizedInvoiceViewModelBuilder {
    
    @Autowired
    private MessageSource messageSource;
    
    public InvoiceViewModel build(Invoice invoice, Locale locale) {
        InvoiceViewModel vm = new InvoiceViewModel();
        
        // Localized strings
        vm.setInvoiceLabel(messageSource.getMessage("invoice.title", null, locale));
        vm.setDateLabel(messageSource.getMessage("invoice.date", null, locale));
        
        // Localized formatting
        DateTimeFormatter formatter = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale);
        vm.setInvoiceDate(invoice.getDate().format(formatter));
        
        // Localized currency
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(locale);
        vm.setTotal(currencyFormatter.format(invoice.getTotal()));
        
        return vm;
    }
}
```

### Template Guidelines

**DO:**
- ✅ Use simple conditionals (`<#if>`, `<#else>`)
- ✅ Use simple loops (`<#list>`)
- ✅ Access pre-calculated values
- ✅ Use pre-formatted strings
- ✅ Keep templates declarative
- ✅ Focus on presentation

**DON'T:**
- ❌ Perform calculations in templates
- ❌ Complex nested conditions
- ❌ Date/number formatting in templates
- ❌ Business logic in templates
- ❌ Database queries or external calls
- ❌ String manipulation beyond simple concatenation

### ViewModel vs Direct Data Access

**Use ViewModel when:**
- Complex calculations needed
- Multiple formatting rules
- Business logic required
- Data transformation needed
- Templates shared across contexts
- Need unit testing
- Localization required

**Use Direct Data Access when:**
- Very simple templates
- Prototyping/development
- Data already in perfect format
- One-off reports
- Performance not critical

### Best Practices Summary

1. **One ViewModel per template type** - `InvoiceViewModel`, `ReportViewModel`, etc.
2. **No logic in templates** - All logic in ViewModel builders
3. **Pre-format everything** - Dates, numbers, currencies
4. **Pre-calculate everything** - Totals, taxes, discounts
5. **Pre-determine conditions** - Status, flags, visibility
6. **Use clear naming** - `showTaxId`, `formattedTotal`, `statusClass`
7. **Keep templates flat** - Avoid deep nesting
8. **Test ViewModels** - Unit test all calculations and formatting
9. **Cache ViewModels** - If data doesn't change frequently
10. **Document ViewModels** - Clear javadoc for all fields

## Future Enhancements

1. **Digital Signatures**: Sign generated PDFs
2. **Watermarks**: Add watermarks to pages
3. **Form Field Preservation**: Keep forms editable
4. **OCR Integration**: Add searchable text layer
5. **Batch Generation**: Generate multiple documents in one request
6. **Template Versioning**: Support multiple template versions
7. **Audit Trail**: Track document generation history
