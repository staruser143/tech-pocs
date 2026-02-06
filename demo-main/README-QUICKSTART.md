# Document Generation System - Quick Start

## Overview

A flexible PDF document generation system supporting multiple template types (AcroForms, FreeMarker, Excel, PDFBox) with template composition and inheritance.

## Project Structure

```
src/main/java/com/example/demo/docgen/
├── controller/
│   └── DocumentController.java       # REST API endpoints
├── core/
│   └── RenderContext.java           # Shared rendering context
├── model/
│   ├── DocumentTemplate.java        # Template model
│   ├── PageSection.java            # Section definition
│   ├── DocumentGenerationRequest.java
│   └── ...                          # Other models
├── renderer/
│   ├── SectionRenderer.java         # Renderer interface
│   └── AcroFormRenderer.java        # AcroForm implementation
└── service/
    ├── DocumentComposer.java        # Main orchestrator
    └── TemplateLoader.java          # Template loading
```

## Getting Started

### 1. Build the Project

```bash
mvn clean install
```

### 2. Run the Application

For development, use the provided helper script which automatically sets the correct Java version (17) and clears the port:

```bash
./dev.sh
```

Or use the standard Maven command (ensure JAVA_HOME is set to Java 17):

```bash
mvn spring-boot:run
```

The service will start on `http://localhost:8080`

### 3. Test the Health Endpoint

```bash
curl http://localhost:8080/api/documents/health
```

### 4. Generate a Document

Create a request payload:

```json
{
  "templateId": "templates/simple-enrollment-form.yaml",
  "data": {
    "applicant": {
      "firstName": "John",
      "lastName": "Doe",
      "ssn": "123-45-6789",
      "dateOfBirth": "1980-01-15",
      "email": "john.doe@example.com",
      "phone": "(555) 123-4567"
    }
  }
}
```

Send the request:

```bash
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d @src/main/resources/example-request.json \
  --output document.pdf
```

## Template Definition

Templates are defined in YAML or JSON format:

**YAML Example:**
```yaml
templateId: enrollment-form
sections:
  - sectionId: applicant-info
    type: ACROFORM
    templatePath: templates/forms/applicant-form.pdf
    order: 1
    fieldMappings:
      firstName: $.applicant.firstName
      lastName: $.applicant.lastName
      ssn: $.applicant.ssn
```

**JSON Example:**
```json
{
  "templateId": "enrollment-form",
  "sections": [
    {
      "sectionId": "applicant-info",
      "type": "ACROFORM",
      "templatePath": "templates/forms/applicant-form.pdf",
      "order": 1,
      "fieldMappings": {
        "firstName": "$.applicant.firstName",
        "lastName": "$.applicant.lastName"
      }
    }
  ]
}
```

## Creating Your First Template

### Step 1: Create a PDF Form with AcroForm Fields

Use Adobe Acrobat or similar tool to create a PDF with form fields:
- Field names: `firstName`, `lastName`, `ssn`, `dateOfBirth`, etc.

Save as: `src/main/resources/templates/forms/applicant-form.pdf`

### Step 2: Create Template Definition

Create: `src/main/resources/templates/my-enrollment.yaml`

```yaml
templateId: my-enrollment-form
sections:
  - sectionId: applicant
    type: ACROFORM
    templatePath: templates/forms/applicant-form.pdf
    order: 1
    fieldMappings:
      firstName: $.applicant.firstName
      lastName: $.applicant.lastName
      ssn: $.applicant.ssn
```

### Step 3: Generate Document

```bash
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": "templates/my-enrollment.yaml",
    "data": {
      "applicant": {
        "firstName": "Jane",
        "lastName": "Smith",
        "ssn": "987-65-4321"
      }
    }
  }' \
  --output my-document.pdf
```

## Advanced Features

### Template Inheritance

```yaml
# Base template
templateId: enrollment-base
sections:
  - sectionId: applicant
    type: ACROFORM
    templatePath: forms/applicant.pdf
    order: 1

---
# Extended template
templateId: enrollment-ca
baseTemplateId: enrollment-base
metadata:
  state: CA
sections:
  - sectionId: ca-disclosure
    type: ACROFORM
    templatePath: forms/ca-disclosure.pdf
    order: 2
```

### Overflow Handling

```yaml
sections:
  - sectionId: dependents
    type: ACROFORM
    templatePath: forms/dependents.pdf
    order: 2
    fieldMappings:
      dependent1_firstName: $.dependents[0].firstName
      dependent2_firstName: $.dependents[1].firstName
      dependent3_firstName: $.dependents[2].firstName
    overflowConfig:
      enabled: true
      dataPath: $.dependents
      maxSlots: 3
      strategy: CONTINUATION_SHEET
      addendumTemplatePath: forms/dependents-continuation.pdf
```

## Next Steps

1. **Add More Renderers**: Implement FreeMarker, Excel, or PDFBox renderers
2. **Template Composition**: Use section groups and fragments
3. **Field Mapping**: Explore JSONPath expressions for complex data
4. **Headers/Footers**: Add header and footer processing
5. **Storage**: Implement template repository (database, S3, etc.)

## Documentation

See the [docs](docs/) folder for comprehensive documentation:
- [Architecture Overview](docs/01-architecture-overview.md)
- [Section Renderers](docs/02-section-renderers.md)
- [Field Mapping Strategies](docs/03-field-mapping-strategies.md)
- [Template Composition](docs/01-architecture-overview.md#template-composition--inheritance)
- [Overflow Handling](docs/01-architecture-overview.md#overflow-handling-for-repeating-data)

## API Reference

### POST `/api/documents/generate`

Generate a PDF document from a template and data.

**Request:**
```json
{
  "templateId": "string",
  "data": { /* runtime data */ },
  "options": { /* optional settings */ }
}
```

**Response:**
- Content-Type: `application/pdf`
- Body: PDF binary data

### GET `/api/documents/health`

Health check endpoint.

**Response:**
```
Document generation service is running
```

## Troubleshooting

### Template not found
- Ensure template file is in `src/main/resources/templates/`
- Check file extension (.yaml, .yml, or .json)
- Verify templateId path in request

### Field not filled
- Check field name in PDF form matches fieldMappings key
- Verify JSONPath expression extracts correct data
- Check console logs for warnings

### PDF not generated
- Verify PDF template file exists and is valid
- Check server logs for detailed error messages
- Ensure all dependencies are properly installed

## License

This is a demo project for learning purposes.
