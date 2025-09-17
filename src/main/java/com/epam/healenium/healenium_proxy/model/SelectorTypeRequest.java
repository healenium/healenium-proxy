package com.epam.healenium.healenium_proxy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for selector type configuration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectorTypeRequest {
    
    /**
     * Type of selector to use for healing
     * Possible values: "cssSelector" or "xpath"
     */
    private String selectorType = "cssSelector";
}
