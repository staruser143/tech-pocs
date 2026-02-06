# Template Design Patterns

To ensure maintainability and scalability of document templates, the system supports several design patterns.

## 1. ViewModel Pattern

The ViewModel pattern decouples the raw business data from the template's rendering logic. Instead of passing the raw data directly to the template, a `ViewModelBuilder` transforms the data into a structure optimized for the template.

### Benefits:
- **Decoupling**: Templates don't need to know about complex business object structures.
- **Reusability**: The same business data can be transformed into different ViewModels for different templates.
- **Logic Centralization**: Complex formatting, calculations, and conditional logic are moved from the template (FTL) to Java code.

### Implementation:
1. Create a `ViewModel` class (usually a POJO or Record).
2. Implement a `ViewModelBuilder` to populate the ViewModel from raw data.
3. Register the builder in the `ViewModelRegistry`.

### Example:
```java
public class InvoiceViewModelBuilder implements ViewModelBuilder {
    @Override
    public Object build(Map<String, Object> data) {
        // Transform raw data into InvoiceViewModel
        return new InvoiceViewModel(...);
    }
}
```

---

## 2. Base Path Optimization

When working with deeply nested data in AcroForms, you can define a `basePath` for a group of fields to reduce redundancy.

### Example:
```yaml
- mappingType: JSONPATH
  basePath: "application.primaryApplicant.demographic"
  fields:
    FirstName: "firstName"
    LastName: "lastName"
```

---

## 3. Hybrid Section Composition

Combine different section types to leverage the strengths of each:
- **AcroForm**: For the main application form (fixed layout).
- **FreeMarker**: For dynamic addendums or cover letters.
- **PDFBox Component**: For complex dynamic tables.

This "Best of Both Worlds" approach ensures that documents are both professional-looking and highly dynamic.
