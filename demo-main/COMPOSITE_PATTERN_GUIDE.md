# Composite Document Generation Guide

This guide explains the **Composite Pattern** architecture implemented for the Document Generation Service. This approach is specifically designed to handle high-variability domains like Healthcare Insurance, where documents change based on Market Category, Product Type, and State.

## 1. Architectural Overview

The system has moved from a "Flat Template" model (one YAML per document) to a "Composite Model" (modular assembly).

### Core Components

*   **Master Template**: The entry point for a business process (e.g., `enrollment-package.yaml`).
*   **Base Template**: Contains common sections shared across all variations (e.g., Applicant Info, Signatures).
*   **Fragments**: Modular YAML snippets for specific features (e.g., `medical-benefits.yaml`, `ca-disclosures.yaml`).
*   **Conditional Logic**: JSONPath/JSONata expressions that determine if a section should render based on the input data.

---

## 2. How to Use the Composite Approach

### Step 1: Define the Base Template
Create a `base-enrollment.yaml` with sections that appear in every document. Use **Repeating Groups** for automated indexing of arrays (like children).

```yaml
templateId: base-enrollment
sections:
  - sectionId: applicant-info
    type: ACROFORM
    templatePath: forms/enrollment-form.pdf
    order: 10
    fieldMappingGroups:
      - mappingType: JSONPATH
        basePath: "$.application"
        fields:
          primaryFirstName: "firstName"
          primaryLastName: "lastName"
      - mappingType: JSONPATH
        basePath: "$.application.children"
        repeatingGroup:
          indexSeparator: "."
          indexPosition: AFTER_FIELD
          fields:
            childFirstName: "firstName"
            childLastName: "lastName"
```

### Step 2: Create Modular Fragments
Create small YAML files for specific products or states.

**`medical-fragment.yaml`**:
```yaml
sections:
  - sectionId: medical-benefits
    type: FREEMARKER
    templatePath: templates/medical.ftl
    condition: "$.application.productType == 'MEDICAL'"
    order: 20
```

### Step 3: Assemble and Override
Use `baseTemplateId` to inherit and `sections` to **override** specific mappings or logic.

**`overridden-enrollment.yaml`**:
```yaml
templateId: overridden-enrollment
baseTemplateId: base-enrollment
sections:
  - sectionId: applicant-info
    fieldMappingGroups:
      - mappingType: JSONATA
        fields:
          # Override base mapping with a transformation
          primaryFirstName: "'MEMBER: ' & application.firstName"
```

---

## 3. Advanced Features

### 1. Deep Merging
When a template inherits from a base, the system performs a **Deep Merge** on sections with the same `sectionId`. You can override:
*   **Mappings**: Replace base mappings with new ones (e.g., switching from JSONPath to JSONata).
*   **Conditions**: Change the visibility rules for a base section.
*   **Template Paths**: Point a base section to a different PDF/FTL file.

### 2. Automated Indexing (Repeating Groups)
The `repeatingGroup` feature eliminates the need to manually map `childFirstName.1`, `childFirstName.2`, etc.
*   **`indexPosition`**: Place the index `BEFORE_FIELD` (1.firstName) or `AFTER_FIELD` (firstName.1).
*   **`indexSeparator`**: Use any character (., _, -) or none.
*   **Scaling**: Automatically handles any number of items in the source array.

### 3. Data-Driven Selection Logic

Instead of writing Java code to pick a template, the **data itself** drives the document structure:

| Data Input | Resulting PDF Structure |
| :--- | :--- |
| `productType: MEDICAL`, `state: NY` | Applicant Info + Medical Benefits + Signature |
| `productType: DENTAL`, `state: CA` | Applicant Info + Dental Benefits + CA Disclosures + Signature |
| `productType: BOTH`, `state: CA` | Applicant Info + Medical + Dental + CA Disclosures + Signature |

---

## 4. Performance & Caching

To handle complex inheritance and remote fetching without latency, the system includes a multi-layer caching strategy.

### 1. Centralized Caching
All document assets are cached in memory using Spring Cache:
*   **YAML Definitions**: Merged template objects are cached after the first resolution.
*   **Raw Resources**: PDF forms and FreeMarker templates are cached as byte arrays.

### 2. Configuration
Enable or disable caching in `application.properties`:
```properties
docgen.templates.cache-enabled=true
```

### 3. Cache Warming (Pre-loading)
To avoid latency on the very first request, you can configure specific templates to be pre-loaded into memory at application startup:
```properties
docgen.templates.preload-ids=base-enrollment,invoice-with-viewmodel
```
The system will automatically fetch the YAML, resolve all inheritance/fragments, and pre-load all associated PDF and FTL files into the cache.

---

## 4. Externalizing Templates (Spring Cloud Config)

For production environments, you can externalize your YAML templates to a Git repository and access them via **Spring Cloud Config Server**.

### 1. Configuration
Add the following to your `application.properties`:

```properties
# Enable Spring Cloud Config Client
spring.cloud.config.uri=http://config-server:8888
spring.cloud.config.label=main
spring.application.name=doc-gen-service
```

### 2. Git Repository Structure
Store your templates in your Git-based config repo:
```text
doc-gen-service/
  templates/
    base-enrollment.yaml
    medical-fragment.yaml
    ca-disclosures.yaml
```

### 3. How it Works
The `TemplateLoader` uses the Config Server's **Plain Text API** to fetch templates. It follows this priority:
1.  **Local Classpath**: Checks `src/main/resources/templates`.
2.  **Local File System**: Checks the current working directory.
3.  **Remote Config Server**: Fetches from `/{application}/{profile}/{label}/{path}`.

This allows you to update document rules and fragments in Git and have them reflected in the service **without a restart or redeploy**.

---

## 5. Key Benefits

### 1. Elimination of Template Sprawl
*   **Before**: 3 Products × 50 States × 2 Market Categories = **300 YAML files**.
*   **After**: 1 Master + 3 Product Fragments + 10 State Fragments = **14 YAML files**.

### 2. Simplified Maintenance
*   **Global Changes**: To update the company logo or footer, you only edit the `base-enrollment.yaml` or the `headerFooterConfig` in the master template.
*   **State Mandates**: If California changes its legal disclosure, you only update `ca-disclosures.yaml`. It automatically reflects in all packages (Medical, Dental, Vision) that include that fragment.

### 3. Business Logic in Configuration
*   Non-developers (Business Analysts) can modify the `condition` strings in YAML to change when a section appears, without requiring a code deployment or Java changes.

### 4. Performance Optimization
*   The `TemplateLoader` performs a deep merge at load time, and the `DocumentComposer` skips non-matching sections early in the process, ensuring fast generation even for complex packages.

---

## 5. Best Practices

1.  **Order Management**: Use `order` values with gaps (10, 20, 30) to allow fragments to "slot in" between base sections.
2.  **Granular Fragments**: Keep fragments focused on a single feature or requirement.
3.  **Condition Syntax**: Use simple equality checks for performance, but leverage JSONPath for complex logic (e.g., checking if an array contains a specific value).
