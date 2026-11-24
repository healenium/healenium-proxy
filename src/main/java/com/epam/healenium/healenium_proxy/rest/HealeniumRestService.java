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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import com.epam.healenium.healenium_proxy.model.TenantSettingsDto;

@Slf4j(topic = "healenium")
@Service
public class HealeniumRestService {

    // Max buffer size for log retrieval (16MB) - prevents DataBufferLimitException for large logs
    private static final int MAX_LOG_BUFFER_SIZE = 16 * 1024 * 1024;

    private static final String HEALENIUM_SESSION_INIT_PATH = "/healenium/session";
    private static final String SELENIUM_HEALTH_CHECK_URI = "/status";
    private static final String TENANT_SETTINGS_URI = "/healenium/settings";

    @Value("${proxy.healenium.container.url}")
    private String healeniumContainerUrl;

    @Value("${proxy.selenium.url}")
    private String seleniumUrl;

    @Value("${proxy.ai.container.url}")
    private String aiServiceUrl;

    @Value("${proxy.playwright.container.url}")
    private String playwrightServiceUrl;

    private WebClient.Builder backendClient(String tenantId) {
        return backendClient(tenantId, false);
    }

    private WebClient.Builder backendClient(String tenantId, boolean largeBuffer) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (largeBuffer) {
            builder.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_LOG_BUFFER_SIZE));
        }
        if (StringUtils.hasText(tenantId)) {
            builder.defaultHeader(TenantAuthGlobalFilter.TENANT_HEADER, tenantId.trim());
        }
        return builder;
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
     * Get settings for a specific tenant from healenium-backend
     */
    public Mono<TenantSettingsDto> getTenantSettings(String tenantId) {
        return backendCall(tenantId, () -> backendClient(tenantId).build()
                .get()
                .uri(TENANT_SETTINGS_URI)
                .retrieve()
                .bodyToMono(TenantSettingsDto.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> {
                    log.debug("Error fetching tenant settings from backend for tenant {}: {}", tenantId, e.getMessage());
                    return Mono.empty();
                }));
    }

}
