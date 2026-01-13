package com.epam.healenium.healenium_proxy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Request model for healenium proxy configuration parameters
 * Note: Shared settings (healEnabled, recoveryTries, scoreCap) are also synced to Playwright proxy
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class SettingsDto {

    // Healenium proxy settings
    private String selectorType;
    private Boolean healEnabled;
    private Integer recoveryTries;
    private Double scoreCap;
    private String logLevel;
    
    // Playwright proxy specific settings (read-only, displayed from playwright-proxy service)
    private Boolean nodePathShortcut;
    private Boolean healingScreenshot;
    private Boolean healingHighlight;
}





