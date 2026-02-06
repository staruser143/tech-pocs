# Headers and Footers

The system provides a consistent way to add headers and footers across all document sections, regardless of whether they were generated via FreeMarker, AcroForms, or PDFBox.

## Configuration

Headers and footers are configured at the template level:

```yaml
headerFooterConfig:
  headers:
    - renderType: PDFBOX
      content: "CONFIDENTIAL DOCUMENT"
      alignment: "CENTER"
      marginTop: 20
  footers:
    - renderType: PDFBOX
      content: "Page {page} of {total}"
      alignment: "RIGHT"
      marginBottom: 20
```

---

## Rendering Types

### 1. PDFBox (`PDFBOX`)
Directly renders text onto the PDF page. This is the fastest and most common option for simple headers and footers.

**Features:**
- **Placeholders**: Supports `{page}` and `{total}` for pagination.
- **Multi-line Support**: Use `\n` in the content string to render multiple lines.
- **Multi-alignment**: You can define multiple header/footer entries with different alignments (LEFT, CENTER, RIGHT) to appear on the same page.

### 2. FreeMarker (`FREEMARKER`)
Renders a FreeMarker template to HTML and then overlays it on the PDF page. This allows for rich branding, logos, and complex layouts.

---

## Advanced Layouts

### Multi-line Headers
The system automatically handles vertical spacing for multi-line content:

```yaml
headers:
  - renderType: PDFBOX
    content: "Line 1\nLine 2\nLine 3"
    alignment: "LEFT"
```

### Multi-alignment on One Page
You can combine multiple entries to create complex layouts:

```yaml
headers:
  - renderType: PDFBOX
    content: "Company Name"
    alignment: "LEFT"
  - renderType: PDFBOX
    content: "Internal Use Only"
    alignment: "CENTER"
  - renderType: PDFBOX
    content: "Date: 2026-01-05"
    alignment: "RIGHT"
```

---

## Page Exclusion
You can exclude headers and footers from specific pages (e.g., the cover page):

```yaml
headerFooterConfig:
  excludePages: [1] # 1-based index
```
