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
        Map<String, Object> result = new HashMap<>();
        
        try {
            SessionLogResultDto logResult = logService.getLogsForSession(sessionId);
            result.put("sessionId", sessionId);
            result.put("proxyLogs", logResult.getLogs());
            result.put("startTime", logResult.getStartTime() != null ? logResult.getStartTime().format(LOG_DATE_FORMAT) : null);
            result.put("endTime", logResult.getEndTime() != null ? logResult.getEndTime().format(LOG_DATE_FORMAT) : null);

            Mono<String> backendLogsMono;
            if (logResult.getStartTime() != null && logResult.getEndTime() != null) {
                backendLogsMono = restService.getBackendLogsForTimeRange(logResult.getStartTime(), logResult.getEndTime());
            } else {
                backendLogsMono = restService.getBackendLogsForSession(sessionId);
            }
            
            return backendLogsMono
                    .flatMap(backendLogs -> {
                        result.put("backendLogs", backendLogs);
                        // Call getAILogs only with sessionId
                        return restService.getAILogsForSession(sessionId)
                                .map(aiLogs -> {
                                    result.put("aiLogs", aiLogs);
                                    return ResponseEntity.ok(result);
                                })
                                .onErrorResume(e -> {
                                    log.error("Error getting AI logs for session: {}", sessionId, e);
                                    result.put("aiLogsError", "Error retrieving AI logs: " + e.getMessage());
                                    return Mono.just(ResponseEntity.ok(result));
                                });
                    })
                    .onErrorResume(e -> {
                        log.error("Error getting backend logs for session: {}", sessionId, e);
                        result.put("backendLogsError", "Error retrieving backend logs: " + e.getMessage());
                        
                        // Still try to get AI logs even if backend logs failed
                        return restService.getAILogsForSession(sessionId)
                                .map(aiLogs -> {
                                    result.put("aiLogs", aiLogs);
                                    return ResponseEntity.ok(result);
                                })
                                .onErrorResume(aiError -> {
                                    log.error("Error getting AI logs for session: {}", sessionId, aiError);
                                    result.put("aiLogsError", "Error retrieving AI logs: " + aiError.getMessage());
                                    return Mono.just(ResponseEntity.ok(result));
                                });
                    });
        } catch (Exception e) {
            log.error("Error getting logs for session: {}", sessionId, e);
            return Mono.just(ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())));
        }
    }
}
