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
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.epam.healenium.healenium_proxy.model.TenantSettingsDto;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Autowired(required = false)
    private TenantSettingsClient tenantSettingsClient;

    /**
     * Settings of the default tenant (anonymous user): healing values from {@link ProxyConfig},
     * keySelectorUrl / collectMetrics / findElementsAutoHealing from the backend defaults.
     *
     * @return Mono containing all configuration parameters
     */
    public Mono<SettingsDto> getAllSettings() {
        return getAllSettings("");
    }

    private Mono<SettingsDto> getAllSettings(String tenantId) {
        Config config = proxyConfig.getConfig();

        String selectorType = Objects.requireNonNullElse(config.getString("selector-type"), "css");
        SettingsDto settings = new SettingsDto()
                .setHealEnabled(config.getBoolean("heal-enabled"))
                .setRecoveryTries(config.getInt("recovery-tries"))
                .setScoreCap(config.getDouble("score-cap"))
                .setSelectorType(selectorType)
                .setHealedType("xpath".equalsIgnoreCase(selectorType) ? "xpath" : "css")
                .setKeySelectorUrl(false)
                .setCollectMetrics(true)
                .setFindElementsAutoHealing(false)
                .setLogLevel(getCurrentLogLevel());

        return restService.getTenantSettings(tenantId)
                .map(defaults -> settings
                        .setKeySelectorUrl(Objects.requireNonNullElse(defaults.getKeySelectorUrl(), false))
                        .setCollectMetrics(Objects.requireNonNullElse(defaults.getCollectMetrics(), true))
                        .setFindElementsAutoHealing(Objects.requireNonNullElse(defaults.getFindElementsAutoHealing(), false)))
                .defaultIfEmpty(settings);
    }

    /**
     * Get settings for a specific tenant with fallback to system defaults
     */
    public Mono<SettingsDto> getSettings(String tenantId) {
        if (isAnonymous(tenantId)) {
            return getAllSettings(tenantId);
        }
        return tenantSettingsClient.getSettingsReactive(tenantId)
                .map(ts -> {
                    Config config = proxyConfig.getConfig();
                    String healedType = ts.getHealedType() != null ? ts.getHealedType() : "css";
                    String selectorType = "xpath".equalsIgnoreCase(healedType) ? "xpath" : "css";
                    return new SettingsDto()
                            .setHealEnabled(ts.getHealEnabled() != null ? ts.getHealEnabled() : config.getBoolean("heal-enabled"))
                            .setRecoveryTries(ts.getRecoveryTries() != null ? ts.getRecoveryTries() : config.getInt("recovery-tries"))
                            .setScoreCap(ts.getScoreCap() != null ? ts.getScoreCap() : config.getDouble("score-cap"))
                            .setSelectorType(selectorType)
                            .setHealedType(healedType)
                            .setKeySelectorUrl(ts.getKeySelectorUrl() != null ? ts.getKeySelectorUrl() : false)
                            .setCollectMetrics(ts.getCollectMetrics() != null ? ts.getCollectMetrics() : true)
                            .setFindElementsAutoHealing(ts.getFindElementsAutoHealing() != null ? ts.getFindElementsAutoHealing() : false)
                            .setLogLevel(getCurrentLogLevel());
                })
                .switchIfEmpty(Mono.defer(this::getAllSettings))
                .onErrorResume(e -> {
                    log.debug("Failed to get tenant settings: {}, falling back to defaults", e.getMessage());
                    return getAllSettings();
                });
    }

    /**
     * No tenant, or the default tenant: changes go to in-memory defaults only, never to a tenant's row.
     */
    public boolean isAnonymous(String tenantId) {
        return tenantSettingsClient == null || tenantSettingsClient.isDefaultTenant(tenantId);
    }

    /**
     * Update a single configuration parameter (reactive).
     * Authenticated tenant: the value is saved to that tenant's backend row and its caches are evicted
     * (hlm-proxy, backend, hlm-playwright-proxy); defaults are untouched.
     * Anonymous (default tenant): only the defaults change (ProxyConfig, backend and hlm-playwright-proxy DynamicSettings).
     *
     * @param key Configuration key to update
     * @param value New value for the configuration
     * @return Mono containing result and any validation errors
     */
    public Mono<Map<String, Object>> updateSingleSetting(String key, String value, String tenantId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            Map<String, String> errors = new HashMap<>();

            try {
                switch (key) {
                    case "HEALED_TYPE", "SELECTOR_TYPE" -> handleHealedType(value, response, errors, tenantId);
                    case "HEAL_ENABLED" -> handleHealEnabled(Boolean.parseBoolean(value), response, errors, tenantId);
                    case "RECOVERY_TRIES" -> handleRecoveryTries(Integer.parseInt(value), response, errors, tenantId);
                    case "SCORE_CAP" -> handleScoreCap(Double.parseDouble(value), response, errors, tenantId);
                    case "LOG_LEVEL" -> handleLogLevel(value.toUpperCase(), response, errors, tenantId);
                    case "KEY_SELECTOR_URL" -> {
                        boolean keyUrl = Boolean.parseBoolean(value);
                        saveSetting(tenantId, key, String.valueOf(keyUrl), new TenantSettingsDto().setKeySelectorUrl(keyUrl));
                        response.put("keySelectorUrl", keyUrl);
                    }
                    case "COLLECT_METRICS" -> {
                        boolean metrics = Boolean.parseBoolean(value);
                        saveSetting(tenantId, key, String.valueOf(metrics), new TenantSettingsDto().setCollectMetrics(metrics));
                        response.put("collectMetrics", metrics);
                    }
                    case "FIND_ELEMENTS_AUTO_HEALING" -> {
                        boolean autoHealing = Boolean.parseBoolean(value);
                        saveSetting(tenantId, key, String.valueOf(autoHealing), new TenantSettingsDto().setFindElementsAutoHealing(autoHealing));
                        response.put("findElementsAutoHealing", autoHealing);
                    }
                    default -> errors.put("key", "Unknown configuration key: " + key);
                }
                return buildResponse(key, value, response, errors);
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
     * Authenticated tenant: save to its backend row (the backend and hlm-proxy caches take the saved value),
     * then evict the tenant in hlm-playwright-proxy. Anonymous: update the backend defaults.
     */
    private void saveSetting(String tenantId, String backendKey, String backendValue, TenantSettingsDto dto) {
        if (isAnonymous(tenantId)) {
            restService.updateBackendSetting(backendKey, backendValue, tenantId)
                    .subscribe(
                            result -> log.debug("Backend default {} = {}: {}", backendKey, backendValue, result),
                            err -> log.warn("Failed to update backend default {}: {}", backendKey, err.getMessage())
                    );
            return;
        }
        tenantSettingsClient.updateSettings(tenantId, dto)
                .flatMap(saved -> isPlaywrightPlatform()
                        ? restService.invalidatePlaywrightTenantSettings(tenantId).thenReturn(saved)
                        : Mono.just(saved))
                .subscribe(
                        saved -> log.debug("Tenant setting successfully persisted for tenant {}", tenantId),
                        err -> log.warn("Failed to persist tenant setting for tenant {}: {}", tenantId, err.getMessage())
                );
    }

    /**
     * Handle selector type / healed type setting update
     */
    private void handleHealedType(String value, Map<String, Object> response, Map<String, String> errors, String tenantId) {
        String validationError = validateSelectorType(value);
        if (validationError == null) {
            String normalizedSelector = "xpath".equalsIgnoreCase(value) ? "xpath" : "css";
            String normalizedHealed = "xpath".equalsIgnoreCase(value) ? "xpath" : "css";
            saveSetting(tenantId, "SELECTOR_TYPE", normalizedHealed, new TenantSettingsDto().setHealedType(normalizedHealed));
            if (isAnonymous(tenantId)) {
                proxyConfig.updateConfigValue("selector-type", normalizedSelector);
                updatePlaywrightProxySetting("SELECTOR_TYPE", normalizedSelector);
            }
            response.put("selectorType", normalizedSelector);
            response.put("healedType", normalizedHealed);
        } else {
            errors.put("selectorType", validationError);
            errors.put("healedType", validationError);
        }
    }

    /**
     * Handle heal enabled setting update. hlm-playwright-proxy HEAL_ENABLED is proxy-wide (wired with DISCOVERY_STATE).
     */
    private void handleHealEnabled(Boolean value, Map<String, Object> response, Map<String, String> errors, String tenantId) {
        if (isAnonymous(tenantId)) {
            proxyConfig.updateConfigValue("heal-enabled", value);
        } else {
            saveSetting(tenantId, "HEAL_ENABLED", value.toString(), new TenantSettingsDto().setHealEnabled(value));
        }
        response.put("healEnabled", value);
        updatePlaywrightProxySetting("HEAL_ENABLED", value.toString());
    }

    /**
     * Handle recovery tries setting update
     */
    private void handleRecoveryTries(Integer value, Map<String, Object> response, Map<String, String> errors, String tenantId) {
        String validationError = validateRecoveryTries(value);
        if (validationError == null) {
            saveSetting(tenantId, "RECOVERY_TRIES", value.toString(), new TenantSettingsDto().setRecoveryTries(value));
            if (isAnonymous(tenantId)) {
                proxyConfig.updateConfigValue("recovery-tries", value);
            }
            response.put("recoveryTries", value);
        } else {
            errors.put("recoveryTries", validationError);
        }
    }

    /**
     * Handle score cap setting update
     */
    private void handleScoreCap(Double value, Map<String, Object> response, Map<String, String> errors, String tenantId) {
        String validationError = validateScoreCap(value);
        if (validationError == null) {
            saveSetting(tenantId, "SCORE_CAP", value.toString(), new TenantSettingsDto().setScoreCap(value));
            if (isAnonymous(tenantId)) {
                proxyConfig.updateConfigValue("score-cap", value);
            }
            response.put("scoreCap", value);
        } else {
            errors.put("scoreCap", validationError);
        }
    }

    /**
     * Handle log level setting update
     */
    private void handleLogLevel(String value,
                                Map<String, Object> response,
                                Map<String, String> errors,
                                String tenantId) {
        String validationError = validateLogLevel(value);
        if (validationError == null) {
            proxyConfig.updateConfigValue("log-level", value);
            response.put("logLevel", value);

            setLogLevel(value);
            updateBackendLogLevel(value, tenantId);
            updateAiLogLevel(value);
            updatePlaywrightProxySetting("LOG_LEVEL", value);
        } else {
            errors.put("logLevel", validationError);
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
        return updateSettings(request, "");
    }

    public Map<String, Object> updateSettings(SettingsDto request, String tenantId) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        if (request.getHealedType() != null) {
            handleHealedType(request.getHealedType(), response, errors, tenantId);
        } else if (request.getSelectorType() != null) {
            handleHealedType(request.getSelectorType(), response, errors, tenantId);
        }

        if (request.getHealEnabled() != null) {
            handleHealEnabled(request.getHealEnabled(), response, errors, tenantId);
        }

        if (request.getRecoveryTries() != null) {
            handleRecoveryTries(request.getRecoveryTries(), response, errors, tenantId);
        }

        if (request.getScoreCap() != null) {
            handleScoreCap(request.getScoreCap(), response, errors, tenantId);
        }

        if (request.getLogLevel() != null) {
            handleLogLevel(request.getLogLevel(), response, errors, tenantId);
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
        if (selectorType != null && ("cssSelector".equalsIgnoreCase(selectorType) || "xpath".equalsIgnoreCase(selectorType) || "css".equalsIgnoreCase(selectorType))) {
            return null;
        }
        return "Selector type must be 'css' or 'xpath'";
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
    private void updateBackendLogLevel(String logLevel, String tenantId) {
        try {
            restService.updateBackendLogLevel(logLevel, "ROOT", tenantId)
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
        return proxyConfig.isPlaywrightPlatform();
    }
}

