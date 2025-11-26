package com.epam.healenium.healenium_proxy.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.model.SettingsDto;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.typesafe.config.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for managing Healenium proxy configuration
 */
@Slf4j(topic = "healenium")
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final List<String> VALID_LOG_LEVELS = Arrays.asList("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
    private static final String HEALENIUM_LOGGER = "com.epam.healenium";

    private final ProxyConfig proxyConfig;
    private final HealeniumRestService restService;

    /**
     * Get all configuration parameters
     * 
     * @return ConfigResponse containing all configuration parameters
     */
    public SettingsDto getAllSettings() {
        Config config = proxyConfig.getConfig();

        return new SettingsDto()
                .setHealEnabled(config.getBoolean("heal-enabled"))
                .setRecoveryTries(config.getInt("recovery-tries"))
                .setScoreCap(config.getDouble("score-cap"))
                .setSelectorType(Objects.requireNonNullElse(config.getString("selector-type"), "cssSelector"))
                .setLogLevel(getCurrentLogLevel());
    }

    /**
     * Update a single configuration parameter
     * 
     * @param key Configuration key to update
     * @param value New value for the configuration
     * @return Map containing result and any validation errors
     */
    public Map<String, Object> updateSingleSetting(String key, String value) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();
        
        try {
            switch (key) {
                case "SELECTOR_TYPE":
                    validateAndUpdateSettingValue("selector-type", value,
                        "selectorType", response, errors, this::validateSelectorType);
                    break;
                    
                case "HEAL_ENABLED":
                    boolean healEnabled = Boolean.parseBoolean(value);
                    proxyConfig.updateConfigValue("heal-enabled", healEnabled);
                    response.put("healEnabled", healEnabled);
                    break;
                    
                case "RECOVERY_TRIES":
                    Integer recoveryTries = Integer.parseInt(value);
                    validateAndUpdateSettingValue("recovery-tries", recoveryTries,
                        "recoveryTries", response, errors, this::validateRecoveryTries);
                    break;
                    
                case "SCORE_CAP":
                    Double scoreCap = Double.parseDouble(value);
                    validateAndUpdateSettingValue("score-cap", scoreCap,
                        "scoreCap", response, errors, this::validateScoreCap);
                    break;
                    
                case "LOG_LEVEL":
                    validateAndUpdateSettingValue("log-level", value.toUpperCase(),
                        "logLevel", response, errors, this::validateLogLevel);
                    break;
                    
                case "KEY_SELECTOR_URL":
                case "COLLECT_METRICS":
                case "FIND_ELEMENTS_AUTO_HEALING":
                    // These settings are handled by healenium-backend
                    response.put("message", "Setting " + key + " is managed by backend service");
                    response.put("success", true);
                    break;
                    
                default:
                    errors.put("key", "Unknown configuration key: " + key);
                    break;
            }
            
            if (!errors.isEmpty()) {
                response.put("errors", errors);
                response.put("success", false);
            } else {
                if (!response.containsKey("message")) {
                    response.put("message", "Configuration updated successfully");
                }
                response.put("success", true);
                response.put("key", key);
                response.put("value", value);
                log.debug("Configuration updated: {} = {}", key, value);
            }
        } catch (NumberFormatException e) {
            errors.put("value", "Invalid value format for " + key);
            response.put("errors", errors);
            response.put("success", false);
        } catch (Exception e) {
            log.error("Error updating setting: " + key, e);
            errors.put("error", "Internal error: " + e.getMessage());
            response.put("errors", errors);
            response.put("success", false);
        }
        
        return response;
    }

    /**
     * Update configuration parameters with validation
     * 
     * @param request Configuration update request
     * @return Map containing updated values and any validation errors
     */
    public Map<String, Object> updateSettings(SettingsDto request) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        if (request.getSelectorType() != null) {
            validateAndUpdateSettingValue("selector-type", request.getSelectorType(),
                "selectorType", response, errors, this::validateSelectorType);
        }

        if (request.getHealEnabled() != null) {
            proxyConfig.updateConfigValue("heal-enabled", request.getHealEnabled());
            response.put("healEnabled", request.getHealEnabled());
        }

        if (request.getRecoveryTries() != null) {
            validateAndUpdateSettingValue("recovery-tries", request.getRecoveryTries(),
                "recoveryTries", response, errors, this::validateRecoveryTries);
        }

        if (request.getScoreCap() != null) {
            validateAndUpdateSettingValue("score-cap", request.getScoreCap(),
                "scoreCap", response, errors, this::validateScoreCap);
        }
        
        if (request.getLogLevel() != null) {
            validateAndUpdateSettingValue("log-level", request.getLogLevel(),
                "logLevel", response, errors, this::validateLogLevel);
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
     * Set log level for healenium loggers
     *
     * @param logLevel The log level to set
     * @return true if successful, false otherwise
     */
    public boolean setLogLevel(String logLevel) {
        if (!isValidLogLevel(logLevel)) {
            log.error("Invalid log level: {}", logLevel);
            return false;
        }

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Level level = Level.toLevel(logLevel);
            ch.qos.logback.classic.Logger logger = loggerContext.getLogger(HEALENIUM_LOGGER);
            logger.setLevel(level);

            System.setProperty("HLM_LOG_LEVEL", logLevel);

            return true;
        } catch (Exception e) {
            log.error("Error setting log level", e);
            return false;
        }
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

    private <T> void validateAndUpdateSettingValue(String configKey, T value, String responseKey,
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
            return null;
        }
        return "Selector type must be 'cssSelector' or 'xpath'";
    }

    private String validateRecoveryTries(Integer recoveryTries) {
        if (recoveryTries >= 0) {
            return null;
        }
        return "Recovery tries must be a non-negative integer";
    }

    private String validateScoreCap(Double scoreCap) {
        if (scoreCap >= 0 && scoreCap <= 1) {
            return null;
        }
        return "Score cap must be between 0 and 1";
    }
    
    private String validateLogLevel(String logLevel) {
        if (isValidLogLevel(logLevel)) {
            updateLogLevel(logLevel.toUpperCase());
            return null;
        }
        return "Log level must be one of: ERROR, WARN, INFO, DEBUG, TRACE";
    }
    
    /**
     * Update the log level for all components
     * 
     * @param newLogLevel New log level to set
     */
    private void updateLogLevel(String newLogLevel) {
        try {
            setLogLevel(newLogLevel);

            updateBackendLogLevel(newLogLevel);
            updateAiLogLevel(newLogLevel);
        } catch (Exception e) {
            log.error("Error updating log level", e);
        }
    }
    
    /**
     * Update the log level in the backend service
     */
    private void updateBackendLogLevel(String logLevel) {
        try {
            restService.updateBackendLogLevel(logLevel, "ROOT")
                .subscribe(
                    result -> log.debug("Backend log level updated successfully: {}", result),
                    error -> log.error("Error updating backend log level", error)
                );
        } catch (Exception e) {
            log.error("Error updating backend log level", e);
        }
    }
    
    /**
     * Update the log level in the AI service
     */
    private void updateAiLogLevel(String logLevel) {
        try {
            restService.updateAiLogLevel(logLevel, "ROOT")
                .subscribe(
                    result -> log.debug("AI service log level updated successfully: {}", result),
                    error -> log.error("Error updating AI service log level", error)
                );
        } catch (Exception e) {
            log.error("Error updating AI service log level", e);
        }
    }

    /**
     * Get current log level for healenium logger
     *
     * @return Current log level
     */
    public String getCurrentLogLevel() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger(HEALENIUM_LOGGER);
        Level level = logger.getLevel();
        return level != null ? level.toString() : "INFO";
    }

    /**
     * Check if the provided log level is valid
     *
     * @param logLevel Log level to check
     * @return true if valid, false otherwise
     */
    public boolean isValidLogLevel(String logLevel) {
        return logLevel != null && VALID_LOG_LEVELS.contains(logLevel.toUpperCase());
    }
}

