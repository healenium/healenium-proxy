package com.epam.healenium.healenium_proxy.rest;

import com.epam.healenium.healenium_proxy.auth.TenantAuthGlobalFilter;
import com.epam.healenium.healenium_proxy.model.SeleniumHealthCheckDto;
import com.epam.healenium.healenium_proxy.model.SessionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j(topic = "healenium")
@Service
public class HealeniumRestService {

    // Max buffer size for log retrieval (16MB) - prevents DataBufferLimitException for large logs
    private static final int MAX_LOG_BUFFER_SIZE = 16 * 1024 * 1024;

    private static final String HEALENIUM_SESSION_INIT_PATH = "/healenium/session";
    private static final String SELENIUM_HEALTH_CHECK_URI = "/status";
    private static final String BACKEND_SETTINGS_UPDATE_URI = "/healenium/settings/update";
    private static final String AI_SETTINGS_UPDATE_URI = "/healenium-ai/settings/update";
    private static final String BACKEND_LOGS_TIME_RANGE_URI = "/healenium/logs/time-range";
    private static final String BACKEND_LOGS_SESSION_URI = "/healenium/logs/session/{sessionId}";
    private static final String AI_LOGS_SESSION_URI = "/healenium-ai/logs/session/{sessionId}";
    private static final String PLAYWRIGHT_LOGS_SESSION_URI = "/hlm-playwright-proxy/logs/session/{sessionId}";
    private static final String PLAYWRIGHT_SETTINGS_GET_URI = "/hlm-playwright-proxy/settings";
    private static final String PLAYWRIGHT_SETTINGS_UPDATE_URI = "/hlm-playwright-proxy/settings/update";

    @Value("${proxy.healenium.container.url}")
    private String healeniumContainerUrl;

    @Value("${proxy.selenium.url}")
    private String seleniumUrl;

    @Value("${proxy.ai.container.url}")
    private String aiServiceUrl;

    @Value("${proxy.playwright.container.url}")
    private String playwrightServiceUrl;

    @Value("${healenium.m2m.internal-token:}")
    private String internalToken;

    private WebClient.Builder backendClient(String tenantId) {
        return backendClient(tenantId, false);
    }

    private WebClient.Builder backendClient(String tenantId, boolean largeBuffer) {
        requireTenantWhenM2m(tenantId);
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (largeBuffer) {
            builder.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_LOG_BUFFER_SIZE));
        }
        if (StringUtils.hasText(internalToken)) {
            builder.defaultHeader(TenantAuthGlobalFilter.INTERNAL_TOKEN_HEADER, internalToken);
        }
        if (StringUtils.hasText(tenantId)) {
            builder.defaultHeader(TenantAuthGlobalFilter.TENANT_HEADER, tenantId.trim());
        }
        return builder;
    }

    private void requireTenantWhenM2m(String tenantId) {
        if (StringUtils.hasText(internalToken) && !StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException(
                    "Healenium-Tenant-Id is required when M2M internal token is configured");
        }
    }

    private <T> Mono<T> backendCall(String tenantId, java.util.function.Supplier<Mono<T>> call) {
        try {
            return call.get();
        } catch (IllegalArgumentException e) {
            return Mono.error(e);
        }
    }

    public void restoreSessionOnServer(URL addressOfRemoteServer,
                                       String sessionId,
                                       Map<String, Object> sessionCapabilities,
                                       String tenantId) {
        SessionDto sessionDto = new SessionDto(addressOfRemoteServer, sessionId, sessionCapabilities);
        backendClient(tenantId)
                .build()
                .post()
                .uri(HEALENIUM_SESSION_INIT_PATH)
                .bodyValue(sessionDto)
                .retrieve()
                .bodyToMono(Void.TYPE)
                .block();
    }

    public Mono<SeleniumHealthCheckDto> healthCheckSelenium() {
        return WebClient.builder()
                .baseUrl(seleniumUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(SELENIUM_HEALTH_CHECK_URI)
                .retrieve()
                .bodyToMono(SeleniumHealthCheckDto.class);
    }

    public Mono<byte[]> getImage(String screenshotPath, String tenantId) {
        return backendCall(tenantId, () -> backendClient(tenantId)
                .build()
                .get()
                .uri(screenshotPath)
                .retrieve()
                .bodyToMono(byte[].class));
    }

    /**
     * Update the log level in the backend service
     *
     * @param logLevel   The log level to set (ERROR, WARN, INFO, DEBUG, TRACE)
     * @param loggerName The name of the logger to update (not used anymore, kept for compatibility)
     * @param tenantId   Tenant UUID for Pro data-plane (may be blank in Free/dev)
     * @return Mono with the response from the backend service
     */
    public Mono<Map<String, String>> updateBackendLogLevel(String logLevel, String loggerName, String tenantId) {
        Map<String, String> requestBody = Map.of(
                "key", "LOG_LEVEL",
                "value", logLevel
        );

        return backendCall(tenantId, () -> backendClient(tenantId)
                .build()
                .post()
                .uri(BACKEND_SETTINGS_UPDATE_URI)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {
                })
                .onErrorResume(e -> {
                    log.error("Error updating backend log level: {}", e.getMessage(), e);
                    return Mono.just(Map.of(
                            "status", "error",
                            "message", "Error updating backend log level: " + e.getMessage()
                    ));
                }));
    }

    /**
     * Update the log level in the AI service
     *
     * @param logLevel   The log level to set (ERROR, WARN, INFO, DEBUG, TRACE)
     * @param loggerName The name of the logger to update (not used anymore, kept for compatibility)
     * @return Mono with the response from the AI service
     */
    public Mono<Map<String, String>> updateAiLogLevel(String logLevel, String loggerName) {
        Map<String, String> requestBody = Map.of(
                "key", "LOG_LEVEL",
                "value", logLevel
        );

        return WebClient.builder()
                .baseUrl(aiServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri(AI_SETTINGS_UPDATE_URI)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {
                })
                .onErrorResume(e -> {
                    log.error("Error updating AI service log level: {}", e.getMessage(), e);
                    return Mono.just(Map.of(
                            "status", "error",
                            "message", "Error updating AI service log level: " + e.getMessage()
                    ));
                });
    }

    /**
     * Get logs from the healenium-backend service for a specific time range
     */
    public Mono<String> getBackendLogsForTimeRange(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        String formattedStartTime = startTime.format(LOG_DATE_FORMAT);
        String formattedEndTime = endTime.format(LOG_DATE_FORMAT);

        return backendCall(tenantId, () -> backendClient(tenantId, true)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(BACKEND_LOGS_TIME_RANGE_URI)
                        .queryParam("startTime", formattedStartTime)
                        .queryParam("endTime", formattedEndTime)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.error("Error retrieving backend logs: {}", e.getMessage(), e);
                    return Mono.just("Error retrieving backend logs: " + e.getMessage());
                }));
    }

    /**
     * Get logs from the healenium-backend service for a specific session ID
     */
    public Mono<String> getBackendLogsForSession(String sessionId, String tenantId) {
        return backendCall(tenantId, () -> backendClient(tenantId, true)
                .build()
                .get()
                .uri(BACKEND_LOGS_SESSION_URI, sessionId)
                .header("X-Session-Id", sessionId)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.error("Error retrieving backend logs: {}", e.getMessage(), e);
                    return Mono.just("Error retrieving backend logs: " + e.getMessage());
                }));
    }

    /**
     * Get logs from the healenium-ai service for a specific session ID
     */
    public Mono<String> getAILogsForSession(String sessionId) {
        return WebClient.builder()
                .baseUrl(aiServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_LOG_BUFFER_SIZE))
                .build()
                .get()
                .uri(AI_LOGS_SESSION_URI, sessionId)
                .header("X-Session-Id", sessionId)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.error("Error retrieving AI logs: {}", e.getMessage(), e);
                    return Mono.just("Error retrieving AI logs: " + e.getMessage());
                });
    }

    /**
     * Get logs from the hlm-playwright-proxy service for a specific session ID
     */
    public Mono<String> getPlaywrightLogsForSession(String sessionId) {
        return WebClient.builder()
                .baseUrl(playwrightServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_LOG_BUFFER_SIZE))
                .build()
                .get()
                .uri(PLAYWRIGHT_LOGS_SESSION_URI, sessionId)
                .header("X-Session-Id", sessionId)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.error("Error retrieving Playwright Proxy logs: {}", e.getMessage(), e);
                    return Mono.just("Error retrieving Playwright Proxy logs: " + e.getMessage());
                });
    }

    /**
     * Get all settings from the playwright-proxy service
     */
    public Mono<Map<String, Object>> getPlaywrightSettings() {
        return WebClient.builder()
                .baseUrl(playwrightServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(PLAYWRIGHT_SETTINGS_GET_URI)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .onErrorResume(e -> {
                    log.error("Error retrieving playwright-proxy settings: {}", e.getMessage(), e);
                    return Mono.just(Map.of(
                            "status", "error",
                            "message", "Error retrieving playwright-proxy settings: " + e.getMessage()
                    ));
                });
    }

    /**
     * Update a setting in the playwright-proxy service
     */
    public Mono<Map<String, Object>> updatePlaywrightSetting(String key, String value) {
        Map<String, String> requestBody = Map.of(
                "key", key,
                "value", value
        );

        return WebClient.builder()
                .baseUrl(playwrightServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri(PLAYWRIGHT_SETTINGS_UPDATE_URI)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .onErrorResume(e -> {
                    log.error("Error updating playwright-proxy setting: {}", e.getMessage(), e);
                    return Mono.just(Map.of(
                            "status", "error",
                            "message", "Error updating playwright-proxy setting: " + e.getMessage()
                    ));
                });
    }
}
