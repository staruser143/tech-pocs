The choice between using a **ViewModel** or **Direct Data Access** depends on the complexity and scale of your document generation needs. Here is a comparison to help you decide:

### 1. Using ViewModel (Recommended for Complex Documents)
In this approach, you use a `ViewModelBuilder` to transform raw JSON/Map data into a typed Java object before rendering.

*   **Pros**:
    *   **Type Safety**: You avoid repetitive casting (e.g., `(String) data.get("name")`) and potential `ClassCastException` at runtime.
    *   **Shared Logic**: If you have multiple sections (e.g., a PDFBox header and a FreeMarker body) that need the same calculated data (like "Total with Tax"), you calculate it once in the `ViewModelBuilder`.
    *   **Decoupling**: If your backend API changes its JSON structure, you only need to update the `ViewModelBuilder`, not every single rendering component.
    *   **Cleaner Code**: Components focus purely on drawing/layout rather than data parsing.
*   **Cons**:
    *   **Boilerplate**: Requires creating a Java class, a Builder, and registering it in the Factory.
    *   **Overhead**: Slightly more code to maintain for very simple "Hello World" style sections.

### 2. Direct Data Access (Better for Simple/Dynamic Sections)
In this approach, the component receives the raw `Map<String, Object>` directly from the request.

*   **Pros**:
    *   **Speed of Development**: No extra classes or builders needed. Just read from the map and draw.
    *   **Flexibility**: Good for highly dynamic data where the fields aren't known in advance.
    *   **Zero Boilerplate**: Ideal for one-off components or simple text overlays.
*   **Cons**:
    *   **Fragile**: Easy to break if a field is missing or has the wrong type (e.g., `Integer` vs `Long`).
    *   **Logic Duplication**: If you need to format a date or currency, you might end up writing that logic inside the rendering code, making it harder to test.

---

### Comparison Matrix

| Feature | ViewModel Pattern | Direct Access |
| :--- | :--- | :--- |
| **Type Safety** | ✅ High (Typed Objects) | ❌ Low (Map Casting) |
| **Maintenance** | ✅ Easier (Centralized Logic) | ❌ Harder (Logic in Renderer) |
| **Reusability** | ✅ High (Share across renderers) | ❌ Low (Specific to Map) |
| **Setup Time** | 🐌 Slower (More files) | ⚡ Faster (Direct) |
| **Best For** | Invoices, Reports, Applications | Watermarks, Simple Headers, Debugging |

### Final Recommendation:
*   **Use ViewModel** if your component needs to perform calculations, format data (dates/currency), or if the same data is used by other sections (like FreeMarker). It makes the system much more robust and professional.
*   **Use Direct Access** only for very simple, static, or highly experimental components where the overhead of a ViewModel isn't justified.