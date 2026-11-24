package com.epam.healenium.healenium_proxy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defaults this proxy holds in its own configuration. hlm-iam assembles the configuration page from
 * this and the tenant rows (spec 004, Requirement 3.1). {@code testPlatform} is the proxy's
 * {@code test-platform} value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProxyDefaultsDto {

    private Boolean healEnabled;
    private Integer recoveryTries;
    private Double scoreCap;
    private String selectorType;
    private String logLevel;
    private String testPlatform;
}
