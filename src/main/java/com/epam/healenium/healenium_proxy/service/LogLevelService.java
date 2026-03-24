package com.epam.healenium.healenium_proxy.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Service for managing log levels in the application
 */
@Slf4j(topic = "healenium")
@Service
public class LogLevelService {

    private static final List<String> VALID_LOG_LEVELS = Arrays.asList("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
    private static final List<String> HEALENIUM_LOGGERS = Arrays.asList(
            "healenium",
            "com.epam.healenium"
    );

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

            for (String loggerName : HEALENIUM_LOGGERS) {
                ch.qos.logback.classic.Logger logger = loggerContext.getLogger(loggerName);
                logger.setLevel(level);
            }
            System.setProperty("HLM_LOG_LEVEL", logLevel);
            
            return true;
        } catch (Exception e) {
            log.error("Error setting log level", e);
            return false;
        }
    }

    /**
     * Get current log level for healenium logger
     *
     * @return Current log level
     */
    public String getCurrentLogLevel() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger(HEALENIUM_LOGGERS.get(0));
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
