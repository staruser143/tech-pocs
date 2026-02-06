Read [](file:///workspaces/demo/OVERFLOW_STRATEGIES.md#1-1), lines 1 to 100

Read [](file:///workspaces/demo/src/main/java/com/example/demo/docgen/model/PageSection.java#1-1), lines 1 to 50

Read [](file:///workspaces/demo/src/main/java/com/example/demo/docgen/model/PageSection.java#51-51), lines 51 to 98

Read [](file:///workspaces/demo/src/main/java/com/example/demo/docgen/model/OverflowConfig.java)

Read [](file:///workspaces/demo/src/main/java/com/example/demo/docgen/renderer/FreeMarkerRenderer.java)

Searched for files matching `**/DocumentGeneratorService.java`, no matches

Read [](file:///workspaces/demo/src/main/java/com/example/demo/docgen/service)

Read [](file:///workspaces/demo/src/main/java/com/example/demo/docgen/service/DocumentComposer.java#1-1), lines 1 to 100

Read [](file:///workspaces/demo/src/main/java/com/example/demo/docgen/service/DocumentComposer.java#150-150), lines 150 to 230

When using FreeMarker for table-heavy sections, you have two primary ways to handle overflow and pagination:

### 1. Automatic Flow (Recommended for FreeMarker)
Since we use **OpenHtmlToPdf**, the engine naturally handles page breaks for long tables. You don't need to split the data manually; you just need to apply the correct CSS to ensure the table looks professional across pages.

Add this CSS to your FreeMarker template:

```html
<style>
    table {
        width: 100%;
        border-collapse: collapse;
        /* Forces the table to paginate correctly */
        -fs-table-paginate: paginate; 
    }
    thead {
        /* Repeats the table header on every new page */
        display: table-header-group; 
    }
    tr {
        /* Prevents a single row from being split across two pages */
        page-break-inside: avoid; 
    }
    
    /* Page numbering (Page X of Y) */
    @page {
        @bottom-right {
            content: "Page " counter(page) " of " counter(pages);
            font-size: 9pt;
        }
    }
</style>
```

### 2. Data-Driven Overflow (Addendum Strategy)
If your business requirement is to show only a fixed number of rows (e.g., 10) in the "Main" section and move the rest to a separate "Addendum" page with a different layout, you can use the **Overflow Strategy** we implemented in the `DocumentComposer`.

Configure your YAML template like this:

```yaml
templateId: enrollment-with-overflow
sections:
  - sectionId: main-table
    type: FREEMARKER
    templatePath: templates/main-table.ftl
    overflowConfigs:
      - arrayPath: "$.items"
        mappingType: JSONPATH
        maxItemsInMain: 10             # Show first 10 rows here
        itemsPerOverflowPage: 20       # Show 20 rows per addendum page
        addendumTemplatePath: templates/table-addendum.ftl
```

**How it works in the background:**
1.  The `DocumentComposer` detects that `$.items` has more than 10 entries.
2.  It renders the `main-table.ftl` with only the first 10 items.
3.  It automatically creates new pages using `table-addendum.ftl` for the remaining items, partitioning them into groups of 20.
4.  In your `table-addendum.ftl`, the items are available as a variable named `${overflowItems}`.

### Which one should you use?
*   **Use Automatic Flow (CSS)** if the table is the primary content and it's okay for it to take up as many pages as needed.
*   **Use Data-Driven Overflow** if you are filling a **pre-defined box** on a specific page (like a small "Dependents" section on a 1-page form) and need to move the "spillover" to the end of the document.