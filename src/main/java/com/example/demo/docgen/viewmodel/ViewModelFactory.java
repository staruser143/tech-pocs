package com.example.demo.docgen.viewmodel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating ViewModels using registered ViewModelBuilders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewModelFactory {

    private final List<ViewModelBuilder<?>> builders;
    private final Map<String, ViewModelBuilder<?>> builderMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (ViewModelBuilder<?> builder : builders) {
            String name = builder.getClass().getSimpleName();
            // Remove "Builder" suffix if present to match the viewModelType in YAML
            if (name.endsWith("Builder")) {
                name = name.substring(0, name.length() - 7);
            }
            builderMap.put(name, builder);
            log.info("Registered ViewModelBuilder: {} as {}", builder.getClass().getName(), name);
        }
    }

    /**
     * Creates a ViewModel for the given type and data.
     * If no builder is found for the type, returns the raw data.
     * 
     * @param viewModelType The name of the ViewModel type
     * @param rawData The raw input data
     * @return The ViewModel or raw data
     */
    public Object createViewModel(String viewModelType, Map<String, Object> rawData) {
        if (viewModelType == null || viewModelType.isEmpty()) {
            return rawData;
        }

        ViewModelBuilder<?> builder = builderMap.get(viewModelType);
        if (builder == null) {
            log.warn("No ViewModelBuilder found for type: {}. Using raw data.", viewModelType);
            return rawData;
        }

        log.debug("Using ViewModelBuilder: {} for type: {}", builder.getClass().getName(), viewModelType);
        return builder.build(rawData);
    }
}
