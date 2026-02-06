# Field Mapping Strategies

Field mapping strategies define how source data is extracted and transformed to fill template fields, particularly for `ACROFORM` and `EXCEL` sections.

## Supported Strategies

| Strategy | Type | Description |
|----------|------|-------------|
| **Direct Access** | `DIRECT` | Simple dot-notation access (e.g., `user.name`) |
| **JSONPath** | `JSONPATH` | Powerful querying and filtering (e.g., `$.items[?(@.price > 10)]`) |
| **JSONata** | `JSONATA` | Advanced transformations and computations |

---

## 1. Direct Access (`DIRECT`)

The simplest and fastest strategy. It supports nested maps and lists using dot notation and numeric indices.

**Example:** `application.primaryApplicant.firstName` or `items.0.description`.

---

## 2. JSONPath (`JSONPATH`)

Uses the Jayway JSONPath library to query complex JSON structures.

**Key Features:**
- **Filtering**: `$.applicants[?(@.type == 'CHILD')]`
- **Wildcards**: `$.items[*].price`
- **Deep Scan**: `$..ssn`

---

## 3. JSONata (`JSONATA`)

Provides a powerful transformation language for JSON data.

**Key Features:**
- **String Concatenation**: `firstName & ' ' & lastName`
- **Aggregations**: `$sum(items.price)`
- **Conditionals**: `status == 'ACTIVE' ? 'Yes' : 'No'`

---

## Automated Repeating Groups

AcroForms often have numbered fields for repeating data (e.g., `childFirstName.1`, `childFirstName.2`). The system can automate this mapping.

### Configuration:
```yaml
repeatingGroup:
  prefix: "child"
  startIndex: 1
  indexSeparator: "."
  indexPosition: AFTER_FIELD
  maxItems: 3
  fields:
    FirstName: "demographic.firstName"
    LastName: "demographic.lastName"
```

This will automatically map:
- `childFirstName.1` -> `applicants[0].demographic.firstName`
- `childFirstName.2` -> `applicants[1].demographic.firstName`
- ... up to `maxItems`.

---

## Overflow Detection

The system can detect when a collection exceeds the capacity of the main form and trigger addendum pages.

### Configuration:
```yaml
overflowConfigs:
  - arrayPath: "application.applicants[type='CHILD']"
    mappingType: JSONPATH
    maxItemsInMain: 3
    itemsPerOverflowPage: 5
    addendumTemplatePath: templates/child-addendum.ftl
```

The `arrayPath` is evaluated using the specified `mappingType`. If the resulting list size exceeds `maxItemsInMain`, the remaining items are passed to the addendum template.
