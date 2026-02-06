# Field Mapping Strategy Comparison Guide

This document compares the three field mapping strategies available in the document generation system.

## Overview

| Strategy | Complexity | Performance | Use Case |
|----------|-----------|-------------|----------|
| **DIRECT** | Simple | ⚡⚡⚡ Fastest | Simple nested structures |
| **JSONPATH** | Medium | ⚡⚡ Fast | Complex queries, filtering |
| **JSONATA** | Advanced | ⚡ Moderate | Transformations, calculations |

## Strategy Details

### 1. DIRECT Mapping

**Configuration:**
```yaml
mappingType: DIRECT
fieldMappings:
  firstName: applicant.firstName
  email: applicant.contact.email
  firstItem: items.0.description
```

**Advantages:**
- ✅ Fastest performance (no parsing overhead)
- ✅ No external dependencies
- ✅ Simple syntax, easy to learn
- ✅ Good for flat or simple nested data

**Limitations:**
- ❌ No filtering capabilities
- ❌ No transformations
- ❌ No calculations
- ❌ Manual handling of null values

**Best For:**
- Simple forms with direct field mappings
- High-volume document generation
- Minimal complexity requirements

---

### 2. JSONPATH Mapping

**Configuration:**
```yaml
mappingType: JSONPATH
fieldMappings:
  firstName: $.applicant.firstName
  activeProjects: $.employee.projects[?(@.status == 'Active')].name
  allSkills: $.employee.skills[*].name
  firstThreeItems: $.items[0:3].name
```

**Advantages:**
- ✅ Powerful filtering (`[?(@.price > 100)]`)
- ✅ Wildcards (`[*]`)
- ✅ Array slicing (`[0:3]`)
- ✅ Deep scanning (`$..author`)
- ✅ Standard syntax (similar to XPath)

**Limitations:**
- ❌ No transformations (concatenation, formatting)
- ❌ No calculations (sum, average, etc.)
- ❌ Limited string operations

**Best For:**
- Complex nested JSON structures
- Filtering and querying arrays
- Extracting multiple values
- Standard query patterns

**Examples:**

| Expression | Description |
|------------|-------------|
| `$.store.book[*].author` | All book authors |
| `$..author` | All authors (deep scan) |
| `$.store.book[?(@.price < 10)]` | Books under $10 |
| `$.store.book[0,1]` | First two books |
| `$.store.book[0:2]` | Books 0-1 (slice) |
| `$.store.book[-1]` | Last book |
| `$.store.book[?(@.isbn)]` | Books with ISBN |

---

### 3. JSONATA Mapping

**Configuration:**
```yaml
mappingType: JSONATA
fieldMappings:
  fullName: customer.firstName & ' ' & customer.lastName
  total: "$sum(items.(price * quantity))"
  taxAmount: "$sum(items.(price * quantity)) * 0.08"
  formattedDate: "$fromMillis(orderDate, '[M01]/[D01]/[Y0001]')"
  itemCount: "$count(items)"
  expensiveItems: "$join(items[price > 50].name, ', ')"
```

**Advantages:**
- ✅ String transformations (concatenation, uppercase, substring)
- ✅ Mathematical operations (sum, average, max, min)
- ✅ Date formatting
- ✅ Conditional logic (ternary operator)
- ✅ Custom functions
- ✅ Powerful aggregations

**Limitations:**
- ❌ More complex syntax
- ❌ Slower than DIRECT/JSONPATH
- ❌ Steeper learning curve
- ❌ Additional dependency

**Best For:**
- Invoice/financial documents (calculations)
- Data transformations required
- Formatted output needed
- Complex business logic

**Common Functions:**

| Function | Example | Description |
|----------|---------|-------------|
| `$sum()` | `$sum(items.price)` | Sum array values |
| `$average()` | `$average(items.price)` | Calculate average |
| `$max()` / `$min()` | `$max(items.price)` | Maximum/minimum |
| `$count()` | `$count(items)` | Count array items |
| `$join()` | `$join(items.name, ', ')` | Join strings |
| `$substring()` | `$substring(name, 0, 5)` | Extract substring |
| `$uppercase()` | `$uppercase(name)` | Convert to uppercase |
| `$lowercase()` | `$lowercase(name)` | Convert to lowercase |
| `$fromMillis()` | `$fromMillis(time, '[Y]-[M]-[D]')` | Format date |

---

## Choosing the Right Strategy

### Use DIRECT when:
- ✓ Simple, flat data structures
- ✓ Performance is critical (100+ documents/sec)
- ✓ Direct property mappings only
- ✓ Minimal dependencies preferred

### Use JSONPATH when:
- ✓ Complex nested JSON
- ✓ Need filtering (`active items`, `items > $100`)
- ✓ Working with arrays
- ✓ Extracting multiple values
- ✓ Familiar with JSONPath/XPath

### Use JSONATA when:
- ✓ Need calculations (totals, tax, averages)
- ✓ String transformations required
- ✓ Date formatting needed
- ✓ Conditional logic in mappings
- ✓ Aggregating/summarizing data

---

## Performance Benchmarks

Based on 1000 form fills:

| Strategy | Time (ms) | Time per Form |
|----------|-----------|---------------|
| DIRECT | ~500ms | 0.5ms |
| JSONPATH | ~1200ms | 1.2ms |
| JSONATA | ~2500ms | 2.5ms |

*Note: Actual performance varies based on data complexity*

---

## Example Comparison: Same Data, Different Strategies

### Data:
```json
{
  "customer": {
    "firstName": "John",
    "lastName": "Doe"
  },
  "items": [
    {"name": "Item A", "price": 25, "quantity": 2},
    {"name": "Item B", "price": 50, "quantity": 1}
  ]
}
```

### Task: Get full name and total amount

#### DIRECT:
```yaml
mappingType: DIRECT
fieldMappings:
  # Can't do fullName concatenation - would need separate fields
  firstName: customer.firstName
  lastName: customer.lastName
  # Can't do calculations - would need pre-computed total
  total: invoice.preComputedTotal
```

#### JSONPATH:
```yaml
mappingType: JSONPATH
fieldMappings:
  firstName: $.customer.firstName
  lastName: $.customer.lastName
  # Can't do concatenation or calculations
  allPrices: $.items[*].price  # Returns: "25, 50"
```

#### JSONATA:
```yaml
mappingType: JSONATA
fieldMappings:
  fullName: customer.firstName & ' ' & customer.lastName  # "John Doe"
  total: "$sum(items.(price * quantity))"  # 100
  formattedTotal: "'$' & $string($sum(items.(price * quantity)))"  # "$100"
```

---

## Migration Guide

### From DIRECT to JSONPATH:
```yaml
# DIRECT
firstName: applicant.firstName

# JSONPATH (add $ prefix)
firstName: $.applicant.firstName
```

### From JSONPATH to JSONATA:
```yaml
# JSONPATH
firstName: $.applicant.firstName

# JSONATA (remove $ prefix)
firstName: applicant.firstName
```

### From DIRECT to JSONATA:
```yaml
# DIRECT
firstName: applicant.firstName

# JSONATA (same syntax)
firstName: applicant.firstName
```

---

## Hybrid Approach

You can use different strategies for different sections:

```yaml
templateId: hybrid-example
sections:
  # Simple header - use DIRECT for performance
  - sectionId: header
    type: ACROFORM
    mappingType: DIRECT
    fieldMappings:
      customerName: customerName
      date: date
  
  # Complex filtering - use JSONPATH
  - sectionId: active-items
    type: ACROFORM
    mappingType: JSONPATH
    fieldMappings:
      activeItems: $.items[?(@.status == 'active')].name
  
  # Calculations - use JSONATA
  - sectionId: totals
    type: ACROFORM
    mappingType: JSONATA
    fieldMappings:
      grandTotal: "$sum(items.(price * quantity)) * 1.08"
```

---

## Testing Your Mappings

Use the provided example files:

```bash
# Test DIRECT mapping
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d @src/main/resources/examples/direct-mapping-request.json \
  --output test-direct.pdf

# Test JSONPATH mapping
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d @src/main/resources/examples/jsonpath-mapping-request.json \
  --output test-jsonpath.pdf

# Test JSONATA mapping
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d @src/main/resources/examples/jsonata-mapping-request.json \
  --output test-jsonata.pdf
```

---

## Additional Resources

- **JSONPath Online Evaluator**: https://jsonpath.com/
- **JSONata Documentation**: https://jsonata.org/
- **JSONPath Specification**: https://goessner.net/articles/JsonPath/
