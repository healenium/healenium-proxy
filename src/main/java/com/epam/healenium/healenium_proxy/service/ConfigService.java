package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.model.ConfigDto;
import com.typesafe.config.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for managing Healenium proxy configuration
 */
@Slf4j(topic = "healenium")
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ProxyConfig proxyConfig;

    /**
     * Get all configuration parameters
     * 
     * @return ConfigResponse containing all configuration parameters
     */
    public ConfigDto getAllConfiguration() {
        Config config = proxyConfig.getConfig();

        return new ConfigDto()
                .setHealEnabled(config.getBoolean("heal-enabled"))
                .setRecoveryTries(config.getInt("recovery-tries"))
                .setScoreCap(config.getDouble("score-cap"))
                .setSelectorType(Objects.requireNonNullElse(config.getString("selector-type"), "cssSelector"));
    }

    /**
     * Update configuration parameters with validation
     * 
     * @param request Configuration update request
     * @return Map containing updated values and any validation errors
     */
    public Map<String, Object> updateConfiguration(ConfigDto request) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        if (request.getSelectorType() != null) {
            validateAndUpdateConfigValue("selector-type", request.getSelectorType(), 
                "selectorType", response, errors, this::validateSelectorType);
        }

        if (request.getHealEnabled() != null) {
            proxyConfig.updateConfigValue("heal-enabled", request.getHealEnabled());
            response.put("healEnabled", request.getHealEnabled());
        }

        if (request.getRecoveryTries() != null) {
            validateAndUpdateConfigValue("recovery-tries", request.getRecoveryTries(), 
                "recoveryTries", response, errors, this::validateRecoveryTries);
        }

        if (request.getScoreCap() != null) {
            validateAndUpdateConfigValue("score-cap", request.getScoreCap(), 
                "scoreCap", response, errors, this::validateScoreCap);
        }
        
        if (!errors.isEmpty()) {
            response.put("errors", errors);
        } else {
            response.put("message", "Configuration updated successfully");
            log.debug("Configuration updated: {}", response);
        }
        
        return response;
    }

    /**
     * Check if configuration update has validation errors
     * 
     * @param result Result map from updateConfiguration
     * @return true if there are errors, false otherwise
     */
    public boolean hasErrors(Map<String, Object> result) {
        return result.containsKey("errors");
    }

    private <T> void validateAndUpdateConfigValue(String configKey, T value, String responseKey, 
                                                   Map<String, Object> response, Map<String, String> errors,
                                                   java.util.function.Function<T, String> validator) {
        String errorMessage = validator.apply(value);
        if (errorMessage == null) {
            proxyConfig.updateConfigValue(configKey, value);
            response.put(responseKey, value);
        } else {
            errors.put(responseKey, errorMessage);
        }
    }

    private String validateSelectorType(String selectorType) {
        if (selectorType.equals("cssSelector") || selectorType.equals("xpath")) {
            return null; // Valid
        }
        return "Selector type must be 'cssSelector' or 'xpath'";
    }

    private String validateRecoveryTries(Integer recoveryTries) {
        if (recoveryTries >= 0) {
            return null; // Valid
        }
        return "Recovery tries must be a non-negative integer";
    }

    private String validateScoreCap(Double scoreCap) {
        if (scoreCap >= 0 && scoreCap <= 1) {
            return null; // Valid
        }
        return "Score cap must be between 0 and 1";
    }
}

