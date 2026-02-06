# Field Mapping Strategies - Quick Start Guide

## Overview

The document generation system supports three field mapping strategies that can be configured in your YAML templates:

1. **DIRECT** - Simple dot notation (fastest)
2. **JSONPATH** - JSONPath expressions (powerful queries)
3. **JSONATA** - JSONata transformations (calculations & transformations)

## How to Configure

In your template YAML file, specify the `mappingType`:

```yaml
sections:
  - sectionId: my-section
    type: ACROFORM
    templatePath: templates/forms/my-form.pdf
    mappingType: DIRECT  # or JSONPATH or JSONATA
    fieldMappings:
      # Your field mappings here
```

---

## Strategy 1: DIRECT Mapping

**Best for:** Simple nested data access, maximum performance

### Syntax
- Use dot notation: `property.nestedProperty`
- Array access: `array.0.property`

### Example

**Template:** `direct-mapping-example.yaml`
```yaml
templateId: direct-mapping-example
sections:
  - sectionId: applicant-info
    type: ACROFORM
    templatePath: templates/forms/applicant-form.pdf
    mappingType: DIRECT
    fieldMappings:
      firstName: applicant.firstName
      lastName: applicant.lastName
      email: applicant.contact.email
      phone: applicant.contact.phone
```

**Request Data:**
```json
{
  "templateId": "direct-mapping-example",
  "data": {
    "applicant": {
      "firstName": "John",
      "lastName": "Doe",
      "contact": {
        "email": "john@example.com",
        "phone": "(555) 123-4567"
      }
    }
  }
}
```

**Test It:**
```bash
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d @src/main/resources/examples/direct-mapping-request.json \
  --output test-direct.pdf
```

---

## Strategy 2: JSONPATH Mapping

**Best for:** Complex queries, filtering, array operations

### Syntax
- Use JSONPath expressions starting with `$`
- Filters: `$.items[?(@.price > 100)]`
- Wildcards: `$.items[*].name`
- Array slicing: `$.items[0:3]`

### Example

**Template:** `jsonpath-mapping-example.yaml`
```yaml
templateId: jsonpath-mapping-example
sections:
  - sectionId: applicant-info
    type: ACROFORM
    templatePath: templates/forms/applicant-form.pdf
    mappingType: JSONPATH
    fieldMappings:
      firstName: $.applicant.firstName
      lastName: $.applicant.lastName
      email: $.applicant.contact.email
      phone: $.applicant.contact.phone
```

**Advanced Examples:**
```yaml
fieldMappings:
  # All skill names (comma-separated)
  allSkills: $.employee.skills[*].name
  
  # Active projects only
  activeProjects: $.projects[?(@.status == 'Active')].name
  
  # First 3 items
  topItems: $.items[0:3].name
  
  # Items over $100
  expensiveItems: $.items[?(@.price > 100)].name
```

**Test It:**
```bash
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": "jsonpath-mapping-example",
    "data": {
      "applicant": {
        "firstName": "Jane",
        "lastName": "Smith",
        "contact": {
          "email": "jane@example.com",
          "phone": "(555) 987-6543"
        }
      }
    }
  }' \
  --output test-jsonpath.pdf
```

---

## Strategy 3: JSONATA Mapping

**Best for:** Calculations, string transformations, date formatting

### Syntax
- String concatenation: `firstName & ' ' & lastName`
- Math functions: `$sum()`, `$average()`, `$max()`, `$min()`
- String functions: `$uppercase()`, `$substring()`
- Date formatting: `$fromMillis()`

### Example

**Template:** `jsonata-mapping-example.yaml`
```yaml
templateId: jsonata-mapping-example
sections:
  - sectionId: applicant-info
    type: ACROFORM
    templatePath: templates/forms/applicant-form.pdf
    mappingType: JSONATA
    fieldMappings:
      # Simple access
      firstName: applicant.firstName
      lastName: applicant.lastName
      
      # String concatenation
      fullName: applicant.firstName & ' ' & applicant.lastName
      
      email: applicant.contact.email
      phone: applicant.contact.phone
```

**Advanced Example:** `advanced-jsonata-example.yaml`
```yaml
templateId: advanced-jsonata-example
sections:
  - sectionId: invoice-summary
    type: ACROFORM
    templatePath: templates/forms/invoice-form.pdf
    mappingType: JSONATA
    fieldMappings:
      # Full name concatenation
      customerFullName: customer.firstName & ' ' & customer.lastName
      
      # Conditional logic
      customerType: customer.isPremium ? 'Premium' : 'Standard'
      
      # Sum all item totals
      subtotal: "$sum(items.(price * quantity))"
      
      # Calculate tax (8%)
      taxAmount: "$sum(items.(price * quantity)) * 0.08"
      
      # Grand total with tax
      grandTotal: "$sum(items.(price * quantity)) * 1.08"
      
      # Count items
      itemCount: "$count(items)"
      
      # Average item price
      avgItemPrice: "$average(items.price)"
      
      # Filter and join - expensive items
      expensiveItems: "$join(items[price > 50].name, ', ')"
```

**Request Data:**
```json
{
  "templateId": "advanced-jsonata-example",
  "data": {
    "customer": {
      "firstName": "Robert",
      "lastName": "Johnson",
      "isPremium": true
    },
    "items": [
      {"name": "Widget A", "price": 25.00, "quantity": 3},
      {"name": "Widget B", "price": 75.00, "quantity": 2},
      {"name": "Widget C", "price": 40.00, "quantity": 1}
    ]
  }
}
```

**Test It:**
```bash
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d @src/main/resources/examples/advanced-jsonata-request.json \
  --output test-jsonata.pdf
```

---

## Quick Comparison

| Feature | DIRECT | JSONPATH | JSONATA |
|---------|--------|----------|---------|
| **Nested Access** | `address.city` | `$.address.city` | `address.city` |
| **Array Index** | `items.0.name` | `$.items[0].name` | `items[0].name` |
| **Filtering** | ❌ | `$.items[?(@.price > 50)]` | `items[price > 50]` |
| **Concatenation** | ❌ | ❌ | `firstName & ' ' & lastName` |
| **Calculations** | ❌ | ❌ | `$sum(items.price)` |
| **Performance** | ⚡⚡⚡ | ⚡⚡ | ⚡ |

---

## When to Use Each Strategy

### Use DIRECT when:
- ✓ You have simple, flat data
- ✓ Performance is critical
- ✓ You don't need transformations
- ✓ Minimal complexity

### Use JSONPATH when:
- ✓ Complex nested structures
- ✓ Need filtering (active items, price ranges)
- ✓ Working with arrays
- ✓ You know JSONPath syntax

### Use JSONATA when:
- ✓ Need calculations (invoices, totals)
- ✓ String transformations required
- ✓ Date formatting needed
- ✓ Conditional logic in mappings

---

## Example Templates

All example templates are in `/src/main/resources/templates/`:

1. **direct-mapping-example.yaml** - Simple DIRECT strategy
2. **jsonpath-mapping-example.yaml** - Basic JSONPATH
3. **advanced-jsonpath-example.yaml** - Advanced filters & queries
4. **jsonata-mapping-example.yaml** - Basic JSONATA
5. **advanced-jsonata-example.yaml** - Calculations & transformations

Test data files are in `/src/main/resources/examples/`:

1. **direct-mapping-request.json**
2. **jsonata-mapping-request.json**
3. **advanced-jsonata-request.json**

---

## Complete Documentation

For detailed documentation, see:
- **MAPPING_STRATEGY_GUIDE.md** - Comprehensive guide with examples
- **DOCUMENT_GENERATION_DESIGN_ARCHIVE.md** - Full design documentation

---

## Testing All Strategies

```bash
# Start the application
cd /workspaces/demo
mvn spring-boot:run

# In another terminal, test each strategy:

# 1. Test DIRECT mapping
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d @src/main/resources/examples/direct-mapping-request.json \
  --output output-direct.pdf

# 2. Test JSONATA mapping
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d @src/main/resources/examples/jsonata-mapping-request.json \
  --output output-jsonata.pdf

# 3. Test advanced JSONATA (calculations)
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d @src/main/resources/examples/advanced-jsonata-request.json \
  --output output-advanced-jsonata.pdf
```

---

## Common JSONata Functions

| Function | Example | Result |
|----------|---------|--------|
| `$sum()` | `$sum([1,2,3])` | `6` |
| `$average()` | `$average([1,2,3])` | `2` |
| `$max()` | `$max([1,2,3])` | `3` |
| `$min()` | `$min([1,2,3])` | `1` |
| `$count()` | `$count([1,2,3])` | `3` |
| `$join()` | `$join(['a','b'], ',')` | `"a,b"` |
| `$substring()` | `$substring('hello', 0, 3)` | `"hel"` |
| `$uppercase()` | `$uppercase('hello')` | `"HELLO"` |
| `$lowercase()` | `$lowercase('HELLO')` | `"hello"` |

---

## Troubleshooting

### Error: "No mapping strategy found"
- Check that `mappingType` is set to `DIRECT`, `JSONPATH`, or `JSONATA`
- Ensure the strategy classes are in the classpath

### JSONPATH returns empty string
- Verify the path starts with `$`
- Check the path matches your data structure
- Use online tester: https://jsonpath.com/

### JSONATA calculation fails
- Check syntax - use `$sum()` not `sum()`
- Verify field names match your data
- Test expressions at: https://jsonata.org/

---

## Next Steps

1. Review [MAPPING_STRATEGY_GUIDE.md](MAPPING_STRATEGY_GUIDE.md) for detailed comparison
2. Try the example templates with your own data
3. Create custom templates using the strategy that fits your needs
4. Mix strategies across different sections for optimal performance

---

## Support

For questions or issues:
1. Check the [MAPPING_STRATEGY_GUIDE.md](MAPPING_STRATEGY_GUIDE.md)
2. Review example templates in `/src/main/resources/templates/`
3. See full design in [DOCUMENT_GENERATION_DESIGN_ARCHIVE.md](DOCUMENT_GENERATION_DESIGN_ARCHIVE.md)
