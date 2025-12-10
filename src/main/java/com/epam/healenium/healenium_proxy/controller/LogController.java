package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.model.SessionLogResultDto;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = {"http://localhost:5173"})
@RestController
@RequiredArgsConstructor
@RequestMapping("/hlm-proxy/logs")
public class LogController {

    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final HealeniumRestService restService;
    private final LogService logService;
    
    /**
     * Get logs for a specific session ID
     * @param sessionId The session ID to get logs for
     * @return Mono<ResponseEntity> containing the session logs
     */
    @GetMapping("/session/{sessionId}")
    public Mono<ResponseEntity<Map<String, Object>>> getSessionLogs(@PathVariable String sessionId) {
        try {
            SessionLogResultDto logResult = logService.getLogsForSession(sessionId);
            
            // Define log sources - easily extensible for future additions
            Map<String, Mono<String>> logSources = new LinkedHashMap<>();
            logSources.put("backendLogs", (logResult.getStartTime() != null && logResult.getEndTime() != null)
                    ? restService.getBackendLogsForTimeRange(logResult.getStartTime(), logResult.getEndTime())
                    : restService.getBackendLogsForSession(sessionId));
            logSources.put("aiLogs", restService.getAILogsForSession(sessionId));
            logSources.put("playwrightLogs", restService.getPlaywrightLogsForSession(sessionId));
            // Add more log sources here as needed:
            // logSources.put("newServiceLogs", restService.getNewServiceLogs(sessionId));
            
            List<String> keys = List.copyOf(logSources.keySet());
            List<Mono<String>> monos = List.copyOf(logSources.values());
            
            // Fetch all logs in parallel using Mono.zip with Iterable
            return Mono.zip(monos, results -> {
                Map<String, Object> result = new HashMap<>();
                result.put("sessionId", sessionId);
                result.put("proxyLogs", logResult.getLogs());
                result.put("startTime", logResult.getStartTime() != null ? logResult.getStartTime().format(LOG_DATE_FORMAT) : null);
                result.put("endTime", logResult.getEndTime() != null ? logResult.getEndTime().format(LOG_DATE_FORMAT) : null);
                
                // Map results back to their keys
                for (int i = 0; i < keys.size(); i++) {
                    result.put(keys.get(i), results[i]);
                }
                return ResponseEntity.ok(result);
            });
        } catch (Exception e) {
            log.error("Error getting logs for session: {}", sessionId, e);
            return Mono.just(ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())));
        }
    }
}
