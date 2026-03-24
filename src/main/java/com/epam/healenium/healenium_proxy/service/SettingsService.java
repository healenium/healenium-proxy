package com.epam.healenium.healenium_proxy.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.model.SettingsDto;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.typesafe.config.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for managing Healenium proxy configuration
 */
@Slf4j(topic = "healenium")
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final List<String> VALID_LOG_LEVELS = Arrays.asList("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
    private static final String HEALENIUM_LOGGER = "healenium"; // Must match @Slf4j(topic = "healenium")
    public static final String ERROR = "error";
    public static final String ERRORS = "errors";
    public static final String SUCCESS = "success";
    public static final String MESSAGE = "message";

    private final ProxyConfig proxyConfig;
    private final HealeniumRestService restService;

    /**
     * Get all configuration parameters (reactive)
     * 
     * @return Mono containing all configuration parameters
     */
    public Mono<SettingsDto> getAllSettings() {
        Config config = proxyConfig.getConfig();

        SettingsDto settings = new SettingsDto()
                .setHealEnabled(config.getBoolean("heal-enabled"))
                .setRecoveryTries(config.getInt("recovery-tries"))
                .setScoreCap(config.getDouble("score-cap"))
                .setSelectorType(Objects.requireNonNullElse(config.getString("selector-type"), "cssSelector"))
                .setLogLevel(getCurrentLogLevel());
        
        // Only add playwright-proxy settings if test platform is PLAYWRIGHT
        if (isPlaywrightPlatform()) {
            return addPlaywrightProxySettings(settings);
        } else {
            // Playwright-specific fields will be null and excluded from JSON response due to @JsonInclude(NON_NULL)
            return Mono.just(settings);
        }
    }

    private @NotNull Mono<SettingsDto> addPlaywrightProxySettings(SettingsDto settings) {
        return restService.getPlaywrightSettings()
                .timeout(java.time.Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.debug("Could not fetch playwright-proxy settings: {}", e.getMessage());
                    // Return empty Mono to skip map and use defaultIfEmpty with already-populated settings
                    return Mono.empty();
                })
                .map(playwrightSettings -> {
                    if (!playwrightSettings.containsKey(ERROR)) {
                        // add Playwright-proxy setting to the common
                        settings.setNodePathShortcut(getBooleanFromMap(playwrightSettings, "NODE_PATH_SHORTCUT"))
                                .setHealingScreenshot(getBooleanFromMap(playwrightSettings, "HEALING_SCREENSHOT"))
                                .setHealingHighlight(getBooleanFromMap(playwrightSettings, "HEALING_HIGHLIGHT"));
                    }
                    return settings;
                })
                // Playwright-specific fields will be null and excluded from JSON response due to @JsonInclude(NON_NULL)
                .defaultIfEmpty(settings);
    }

    /**
     * Safely extract boolean value from map with null-safe handling
     */
    private boolean getBooleanFromMap(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key))
                .map(value -> Boolean.parseBoolean(String.valueOf(value)))
                .orElse(false);
    }
    
    /**
     * Update a single configuration parameter (reactive)
     * 
     * @param key Configuration key to update
     * @param value New value for the configuration
     * @return Mono containing result and any validation errors
     */
    public Mono<Map<String, Object>> updateSingleSetting(String key, String value) {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            Map<String, String> errors = new HashMap<>();
            
            try {
                switch (key) {
                    case "SELECTOR_TYPE":
                        handleSelectorType(value, response, errors);
                        return buildResponse(key, value, response, errors);
                        
                    case "HEAL_ENABLED":
                        handleHealEnabled(Boolean.parseBoolean(value), response, errors);
                        return buildResponse(key, value, response, errors);
                        
                    case "RECOVERY_TRIES":
                        handleRecoveryTries(Integer.parseInt(value), response, errors);
                        return buildResponse(key, value, response, errors);
                        
                    case "SCORE_CAP":
                        handleScoreCap(Double.parseDouble(value), response, errors);
                        return buildResponse(key, value, response, errors);
                        
                    case "LOG_LEVEL":
                        handleLogLevel(value.toUpperCase(), response, errors);
                        return buildResponse(key, value, response, errors);
                        
                    case "KEY_SELECTOR_URL",
                    "COLLECT_METRICS",
                    "FIND_ELEMENTS_AUTO_HEALING":
                        response.put(MESSAGE, "Setting " + key + " is managed by backend service");
                        response.put(SUCCESS, true);
                        return buildResponse(key, value, response, errors);
                    
                    case "NODE_PATH_SHORTCUT",
                    "HEALING_SCREENSHOT",
                    "HEALING_HIGHLIGHT":
                        handlePlaywrightOnlySetting(key, value, response, errors);
                        return buildResponse(key, value, response, errors);
                        
                    default:
                        errors.put("key", "Unknown configuration key: " + key);
                        return buildResponse(key, value, response, errors);
                }
            } catch (NumberFormatException e) {
                errors.put("value", "Invalid value format for " + key);
                response.put(ERRORS, errors);
                response.put(SUCCESS, false);
                return response;
            } catch (Exception e) {
                log.error("Error updating setting: {}", key, e);
                errors.put(ERROR, "Internal error: " + e.getMessage());
                response.put(ERRORS, errors);
                response.put(SUCCESS, false);
                return response;
            }
        });
    }
    
    /**
     * Build response map with common fields
     */
    private Map<String, Object> buildResponse(String key, String value, Map<String, Object> response, Map<String, String> errors) {
        if (!errors.isEmpty()) {
            response.put(ERRORS, errors);
            response.put(SUCCESS, false);
        } else {
            if (!response.containsKey(MESSAGE)) {
                response.put(MESSAGE, "Configuration updated successfully");
            }
            response.put(SUCCESS, true);
            response.put("key", key);
            response.put("value", value);
            log.debug("Configuration updated: {} = {}", key, value);
        }
        return response;
    }
    
    // ==================== Setting Handlers ====================
    
    /**
     * Handle selector type setting update
     */
    private void handleSelectorType(String value, Map<String, Object> response, Map<String, String> errors) {
        String validationError = validateSelectorType(value);
        if (validationError == null) {
            proxyConfig.updateConfigValue("selector-type", value);
            response.put("selectorType", value);
            updatePlaywrightProxySetting("SELECTOR_TYPE", value);
        } else {
            errors.put("selectorType", validationError);
        }
    }
    
    /**
     * Handle heal enabled setting update (shared with Playwright proxy)
     */
    private void handleHealEnabled(Boolean value, Map<String, Object> response, Map<String, String> errors) {
        proxyConfig.updateConfigValue("heal-enabled", value);
        response.put("healEnabled", value);
        updatePlaywrightProxySetting("HEAL_ENABLED", value.toString());
    }
    
    /**
     * Handle recovery tries setting update (shared with Playwright proxy)
     */
    private void handleRecoveryTries(Integer value, Map<String, Object> response, Map<String, String> errors) {
        String validationError = validateRecoveryTries(value);
        if (validationError == null) {
            proxyConfig.updateConfigValue("recovery-tries", value);
            response.put("recoveryTries", value);
            updatePlaywrightProxySetting("RECOVERY_TRIES", value.toString());
        } else {
            errors.put("recoveryTries", validationError);
        }
    }
    
    /**
     * Handle score cap setting update (shared with Playwright proxy)
     */
    private void handleScoreCap(Double value, Map<String, Object> response, Map<String, String> errors) {
        String validationError = validateScoreCap(value);
        if (validationError == null) {
            proxyConfig.updateConfigValue("score-cap", value);
            response.put("scoreCap", value);
            updatePlaywrightProxySetting("SCORE_CAP", value.toString());
        } else {
            errors.put("scoreCap", validationError);
        }
    }
    
    /**
     * Handle log level setting update
     */
    private void handleLogLevel(String value, Map<String, Object> response, Map<String, String> errors) {
        String validationError = validateLogLevel(value);
        if (validationError == null) {
            proxyConfig.updateConfigValue("log-level", value);
            response.put("logLevel", value);

            setLogLevel(value);
            updateBackendLogLevel(value);
            updateAiLogLevel(value);
            updatePlaywrightProxySetting("LOG_LEVEL", value);
        } else {
            errors.put("logLevel", validationError);
        }
    }
    
    /**
     * Handle Playwright-only setting update (NODE_PATH_SHORTCUT, HEALING_SCREENSHOT, HEALING_HIGHLIGHT)
     */
    private void handlePlaywrightOnlySetting(String key, String value, Map<String, Object> response, Map<String, String> errors) {
        if (isPlaywrightPlatform()) {
            updatePlaywrightProxySetting(key, value);
            response.put(MESSAGE, "Setting " + key + " is managed by playwright-proxy service");
            response.put(SUCCESS, true);
        } else {
            errors.put("key", "Setting " + key + " is only available for PLAYWRIGHT platform");
        }
    }

    /**
     * Update configuration parameters with validation (batch update)
     * Note: This method only handles healenium-proxy settings.
     * Playwright-specific settings should be updated via updateSingleSetting() for proper reactive handling.
     * 
     * @param request Configuration update request
     * @return Map containing updated values and any validation errors
     */
    public Map<String, Object> updateSettings(SettingsDto request) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        // Handle healenium proxy settings (also synced to Playwright proxy for shared settings)
        if (request.getSelectorType() != null) {
            handleSelectorType(request.getSelectorType(), response, errors);
        }

        if (request.getHealEnabled() != null) {
            handleHealEnabled(request.getHealEnabled(), response, errors);
        }

        if (request.getRecoveryTries() != null) {
            handleRecoveryTries(request.getRecoveryTries(), response, errors);
        }

        if (request.getScoreCap() != null) {
            handleScoreCap(request.getScoreCap(), response, errors);
        }
        
        if (request.getLogLevel() != null) {
            handleLogLevel(request.getLogLevel(), response, errors);
        }
        
        if (!errors.isEmpty()) {
            response.put(ERRORS, errors);
        } else {
            response.put(MESSAGE, "Configuration updated successfully");
            log.debug("Configuration updated: {}", response);
        }
        
        return response;
    }

    /**
     * Set log level for healenium loggers
     *
     * @param logLevel The log level to set
     */
    public void setLogLevel(String logLevel) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Level level = Level.toLevel(logLevel);
            ch.qos.logback.classic.Logger logger = loggerContext.getLogger(HEALENIUM_LOGGER);
            logger.setLevel(level);

            System.setProperty("HLM_LOG_LEVEL", logLevel);
        } catch (Exception e) {
            log.error("Error setting log level", e);
        }
    }

    /**
     * Check if configuration update has validation errors
     * 
     * @param result Result map from updateConfiguration
     * @return true if there are errors, false otherwise
     */
    public boolean hasErrors(Map<String, Object> result) {
        return result.containsKey(ERRORS);
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
        if (logLevel != null && VALID_LOG_LEVELS.contains(logLevel.toUpperCase())) {
            return null;
        }
        return "Log level must be one of: ERROR, WARN, INFO, DEBUG, TRACE";
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
     * Update a setting in the Playwright proxy service asynchronously (only if platform is PLAYWRIGHT)
     */
    private void updatePlaywrightProxySetting(String key, String value) {
        if (!isPlaywrightPlatform()) {
            return;
        }
        try {
            restService.updatePlaywrightSetting(key, value)
                .subscribe(
                    result -> log.debug("Playwright-proxy setting updated successfully: {} = {}", key, value),
                    error -> log.warn("Could not update playwright-proxy setting {} = {}: {}", key, value, error.getMessage())
                );
        } catch (Exception e) {
            log.error("Error updating playwright-proxy setting: " + key, e);
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
     * Check if the test platform is PLAYWRIGHT
     * 
     * @return true if test platform is PLAYWRIGHT, false otherwise
     */
    private boolean isPlaywrightPlatform() {
        try {
            Config config = proxyConfig.getConfig();
            String testPlatform = config.getString("test-platform");
            return "PLAYWRIGHT".equalsIgnoreCase(testPlatform);
        } catch (Exception e) {
            log.debug("Could not determine test platform, assuming SELENIUM: {}", e.getMessage());
            return false;
        }
    }
}

