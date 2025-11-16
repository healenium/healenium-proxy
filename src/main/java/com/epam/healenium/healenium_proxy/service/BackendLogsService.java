package com.epam.healenium.healenium_proxy.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for retrieving logs from the healenium-backend service
 */
@Slf4j(topic = "healenium")
@Service
public class BackendLogsService {

    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    @Value("${backend.service.url:http://localhost:7878}")
    private String backendServiceUrl;
    
    private WebClient webClient;

    @PostConstruct
    private void init() {
        this.webClient = WebClient.builder()
                .baseUrl(backendServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Get logs from the healenium-backend service for a specific time range
     * @param startTime Start time for log retrieval (can be null)
     * @param endTime End time for log retrieval (can be null)
     * @param sessionId Session ID to use when time range is not available
     * @return Mono<String> containing the backend logs
     */
    public Mono<String> getBackendLogs(LocalDateTime startTime, LocalDateTime endTime, String sessionId) {
        Map<String, String> requestBody = new HashMap<>();

        if (startTime != null && endTime != null) {
            String formattedStartTime = startTime.format(LOG_DATE_FORMAT);
            String formattedEndTime = endTime.format(LOG_DATE_FORMAT);
            
            requestBody.put("startTime", formattedStartTime);
            requestBody.put("endTime", formattedEndTime);
        } else {
            log.info("No time range available, fetching backend logs for session ID: {}", sessionId);
            requestBody.put("sessionId", sessionId);
        }
        
        return webClient.post()
                .uri("/healenium/logs/time-range")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(logs -> {
                    if (logs == null || logs.isEmpty()) {
                        log.warn("Received empty logs from backend service");
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error retrieving backend logs: {}", e.getMessage(), e);
                    return Mono.just("Error retrieving backend logs: " + e.getMessage());
                });
    }
}
