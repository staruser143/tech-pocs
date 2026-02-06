# Document Generation System - Design Documentation

## Overview

A flexible PDF document generation system that supports multiple template types (FreeMarker, AcroForms, Excel, PDFBox components) with the ability to compose multi-page/multi-section documents with headers and footers.

## Documentation Structure

This documentation is organized into the following sections for better readability and maintenance:

### 📋 Core Documentation

1. **[Architecture Overview](01-architecture-overview.md)**
   - High-level architecture
   - Core components and patterns
   - Technology stack
   - Document generation flow

2. **[Section Renderers](02-section-renderers.md)**
   - FreeMarker renderer
   - AcroForm renderer
   - Excel renderer
   - PDFBox direct renderer
   - Overflow & Addendum system
   - Renderer comparison

3. **[Field Mapping Strategies](03-field-mapping-strategies.md)**
   - Direct access mapping
   - JSONPath mapping
   - JSONata mapping
   - Automated repeating groups
   - Overflow detection logic

4. **[Headers and Footers](04-headers-footers.md)**
   - PDFBox rendering (Multi-line/Multi-alignment)
   - FreeMarker rendering
   - Pagination and placeholders
   - Configuration options

5. **[Storage Strategies](05-storage-strategies.md)** - *Coming soon*
   - File system storage
   - Database storage
   - Cloud storage (S3/Azure/GCS)
   - Git repository storage
   - Spring Cloud Config
   - Hybrid approach

6. **[Template Design Patterns](06-template-design-patterns.md)**
   - ViewModel pattern for FreeMarker
   - Base path optimization
   - Hybrid section composition
   - Best practices

### 📚 Additional Resources

7. **[API Reference](07-api-reference.md)**
   - REST API endpoints
   - Request/response examples
   - Error handling

8. **[Configuration Examples](08-configuration-examples.md)**
   - Template definitions (JSON/YAML)
   - Common use cases
   - Multi-section documents

9. **[Future Enhancements](09-future-enhancements.md)**
   - Planned features
   - Digital signatures
   - Watermarks
   - Batch generation

## Quick Start

### Basic Document Generation Flow

```
1. Define Template Configuration (YAML/JSON)
   ↓
2. Configure Sections (FreeMarker/AcroForm/Excel/PDFBox)
   ↓
3. Map Data to Template Fields
   ↓
4. Generate Document via API
   ↓
5. Download Generated PDF
```

### Minimal Example

```yaml
templateId: simple-invoice
sections:
  - sectionId: invoice-body
    type: FREEMARKER
    templatePath: templates/invoice.ftl
    data:
      invoiceNumber: "INV-001"
      customer: "John Doe"
      total: "$1,500.00"
```

## Architecture at a Glance

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
│ Renderer       │  │  Renderer   │  │  Renderers      │
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

## Key Features

✅ **Multiple Template Types** - FreeMarker, AcroForms, Excel, PDFBox  
✅ **Flexible Composition** - Mix different section types in one document  
✅ **Headers & Footers** - Global or page-specific  
✅ **Field Mapping** - Direct, JSONPath, JSONata strategies  
✅ **Storage Options** - File system, Database, S3, Git, Spring Cloud Config  
✅ **Dynamic Updates** - Refresh templates without restart  
✅ **Version Control** - Full template versioning and rollback  
✅ **Multi-tenant** - Support for different configurations per tenant  

## Getting Started

1. Read the [Architecture Overview](01-architecture-overview.md) to understand the system design
2. Review [Section Renderers](02-section-renderers.md) to choose your template types
3. Check [Configuration Examples](08-configuration-examples.md) for your use case
4. Implement using the [API Reference](07-api-reference.md)

## Contributing

When updating documentation:
- Keep each document focused on a single topic
- Update cross-references when moving content
- Add examples for new features
- Update this README with new sections

## Support

For questions or issues, please refer to the specific documentation section or contact the development team.
