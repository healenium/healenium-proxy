package com.epam.healenium.healenium_proxy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Request model for all healenium configuration parameters
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ConfigDto {

    private String selectorType;
    private Boolean healEnabled;
    private Integer recoveryTries;
    private Double scoreCap;
}





