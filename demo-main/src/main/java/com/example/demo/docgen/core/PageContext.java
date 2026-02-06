package com.example.demo.docgen.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Context for rendering headers and footers
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageContext {
    private int pageNumber;
    private int totalPages;
    private Map<String, Object> data;
}
