package com.epam.healenium.healenium_proxy.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.model.ProxyDefaultsDto;
import com.typesafe.config.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This proxy's own settings: its defaults and its log level. Settings that belong to a tenant, or
 * that span services, are owned by hlm-iam (spec 004, Requirement 3). hlm-iam calls this service's
 * endpoints, and nothing else does.
 */
@Slf4j(topic = "healenium")
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final List<String> VALID_LOG_LEVELS = Arrays.asList("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
    private static final String HEALENIUM_LOGGER = "healenium"; // Must match @Slf4j(topic = "healenium")

    private final ProxyConfig proxyConfig;

    /** The proxy's defaults, read from its configuration. */
    public ProxyDefaultsDto defaults() {
        Config config = proxyConfig.getConfig();
        return new ProxyDefaultsDto(
                config.getBoolean("heal-enabled"),
                config.getInt("recovery-tries"),
                config.getDouble("score-cap"),
                Objects.requireNonNullElse(config.getString("selector-type"), "css"),
                getCurrentLogLevel(),
                testPlatform(config));
    }

    /** Null when the level is valid, otherwise the message the caller returns to hlm-iam. */
    public static String logLevelError(String logLevel) {
        if (logLevel != null && VALID_LOG_LEVELS.contains(logLevel.toUpperCase())) {
            return null;
        }
        return "Log level must be one of: ERROR, WARN, INFO, DEBUG, TRACE";
    }

    /** Applies a log level to this process only. The caller validates it first. */
    public void applyLogLevel(String logLevel) {
        proxyConfig.updateConfigValue("log-level", logLevel);
        setLogLevel(logLevel);
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

    private static String testPlatform(Config config) {
        try {
            return config.getString("test-platform");
        } catch (Exception e) {
            return null;
        }
    }
}
