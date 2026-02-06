# Section Renderers

The Document Generation System uses a Strategy Pattern to support different types of document sections. Each section type is handled by a specific `SectionRenderer` implementation.

## Supported Section Types

| Type | Renderer | Description |
|------|----------|-------------|
| `FREEMARKER` | `FreeMarkerSectionRenderer` | HTML-based templates converted to PDF |
| `ACROFORM` | `AcroFormSectionRenderer` | Existing PDF forms with fillable fields |
| `PDFBOX_COMPONENT` | `PdfBoxDirectSectionRenderer` | Programmatic PDF generation using PDFBox |
| `EXCEL` | `ExcelSectionRenderer` | Excel templates populated and converted to PDF |

---

## 1. FreeMarker Renderer (`FREEMARKER`)

The FreeMarker renderer is the most flexible option, allowing for rich layouts using HTML and CSS.

### How it works:
1. The renderer processes a `.ftl` template using the provided data.
2. The resulting HTML is passed to the **OpenHtmlToPDF** library.
3. A `PDDocument` is generated from the HTML.

### Configuration Example:
```yaml
sectionId: invoice-body
type: FREEMARKER
templatePath: templates/invoice.ftl
```

---

## 2. AcroForm Renderer (`ACROFORM`)

The AcroForm renderer is ideal for official forms or documents where the layout is fixed and provided as a PDF.

### How it works:
1. Loads an existing PDF template with form fields.
2. Maps source data to PDF field names using a `FieldMappingStrategy`.
3. Fills the fields and optionally flattens the form.

### Advanced Feature: Overflow & Addendum
When a collection of data (e.g., dependents, prior coverages) exceeds the available slots in an AcroForm, the system can automatically trigger "Addendum" pages.

#### Configuration:
```yaml
overflowConfigs:
  - arrayPath: "application.applicants[type='CHILD']"
    mappingType: JSONPATH
    maxItemsInMain: 3
    itemsPerOverflowPage: 5
    addendumTemplatePath: templates/child-addendum.ftl
```

---

## 3. PDFBox Direct Renderer (`PDFBOX_COMPONENT`)

The PDFBox Direct renderer allows for programmatic control over PDF generation. It is useful for complex components like dynamic tables or charts that are difficult to express in HTML or fixed forms.

### How it works:
1. The renderer looks up a named component in the `ComponentRegistry`.
2. The component's `render` method is called, receiving the `PDDocument` and `PDPage`.

### Configuration Example:
```yaml
sectionId: dynamic-table
type: PDFBOX_COMPONENT
componentName: invoiceTable
```

---

## 4. Excel Renderer (`EXCEL`)

The Excel renderer allows users to design templates in Microsoft Excel, which are then populated and converted to PDF.

### How it works:
1. Loads an `.xlsx` or `.xls` template.
2. Populates cells based on cell references (e.g., `Sheet1!A1`) or named ranges.
3. Evaluates formulas and converts the workbook to PDF.

---

## Renderer Comparison

| Aspect | FreeMarker | AcroForm | PDFBox Direct | Excel |
|--------|------------|----------|---------------|-------|
| **Layout Control** | High (HTML/CSS) | Fixed (PDF) | Absolute (Code) | High (Excel) |
| **Dynamic Content** | Excellent | Limited | Excellent | Good |
| **Performance** | Medium | Fast | Very Fast | Slow |
| **Ease of Design** | Easy (Web Devs) | Easy (Acrobat) | Hard (Java Devs) | Easy (Business) |
| **Best For** | Invoices, Letters | Official Forms | Complex Tables | Financial Reports |
