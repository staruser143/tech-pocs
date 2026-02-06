package com.example.demo.docgen.viewmodel;

import java.util.Map;

/**
 * Interface for building a ViewModel from raw data.
 * ViewModels are used to pre-process data before passing it to a template engine like FreeMarker.
 * 
 * @param <T> The type of the ViewModel
 */
public interface ViewModelBuilder<T> {
    /**
     * Builds a ViewModel from the provided raw data.
     * 
     * @param rawData The raw input data (usually from the JSON request)
     * @return The processed ViewModel object
     */
    T build(Map<String, Object> rawData);
}
