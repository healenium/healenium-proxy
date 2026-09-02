package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.auth.TenantResolver;
import com.epam.healenium.healenium_proxy.model.SessionLogResultDto;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@CrossOrigin(origins = {"http://localhost:5173"})
@RestController
@RequiredArgsConstructor
@RequestMapping("/hlm-proxy/logs")
public class LogController {

    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final HealeniumRestService restService;
    private final LogService logService;
    private final TenantResolver tenantResolver;

    /**
     * Get logs for a specific session ID
     * @param sessionId The session ID to get logs for
     * @return Mono<ResponseEntity> containing the session logs
     */
    @GetMapping("/session/{sessionId}")
    public Mono<ResponseEntity<Map<String, Object>>> getSessionLogs(
            @PathVariable String sessionId,
            ServerWebExchange exchange) {
        return tenantResolver.resolve(exchange)
                .flatMap(tenantId -> fetchSessionLogs(sessionId, tenantId))
                .onErrorResume(TenantResolver.TenantResolutionException.class, e ->
                        Mono.just(ResponseEntity.status(e.getStatus())
                                .body(Map.of("error", e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error getting logs for session: {}", sessionId, e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of("error", e.getMessage())));
                });
    }

    private Mono<ResponseEntity<Map<String, Object>>> fetchSessionLogs(String sessionId, String tenantId) {
        SessionLogResultDto logResult = logService.getLogsForSession(sessionId);

        Map<String, Mono<String>> logSources = new LinkedHashMap<>();
        logSources.put("backendLogs", (logResult.getStartTime() != null && logResult.getEndTime() != null)
                ? restService.getBackendLogsForTimeRange(logResult.getStartTime(), logResult.getEndTime(), tenantId)
                : restService.getBackendLogsForSession(sessionId, tenantId));
        logSources.put("aiLogs", restService.getAILogsForSession(sessionId));
        logSources.put("playwrightLogs", restService.getPlaywrightLogsForSession(sessionId));

        List<String> keys = List.copyOf(logSources.keySet());
        List<Mono<String>> logSourcesMono = List.copyOf(logSources.values());

        return Mono.zip(logSourcesMono, logs -> {
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("proxyLogs", logResult.getLogs());

            String[] timestamps = resolveTimestamps(logResult, (String) logs[2]);
            result.put("startTime", timestamps[0]);
            result.put("endTime", timestamps[1]);

            for (int i = 0; i < keys.size(); i++) {
                result.put(keys.get(i), logs[i]);
            }
            return ResponseEntity.ok(result);
        });
    }

    private String[] resolveTimestamps(SessionLogResultDto logResult, String playwrightLogs) {
        if (logResult.getStartTime() != null && logResult.getEndTime() != null) {
            return new String[]{
                    logResult.getStartTime().format(LOG_DATE_FORMAT),
                    logResult.getEndTime().format(LOG_DATE_FORMAT)
            };
        }
        LocalDateTime[] times = extractTimestampsFromLogs(playwrightLogs);
        return new String[]{
                times[0] != null ? times[0].format(LOG_DATE_FORMAT) : null,
                times[1] != null ? times[1].format(LOG_DATE_FORMAT) : null
        };
    }

    private LocalDateTime[] extractTimestampsFromLogs(String logContent) {
        if (logContent == null || logContent.isEmpty()) {
            return new LocalDateTime[]{null, null};
        }

        Pattern timestampPattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3})");
        String[] lines = logContent.split("\n");
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;

        for (String line : lines) {
            Matcher matcher = timestampPattern.matcher(line);
            if (matcher.find()) {
                try {
                    startTime = LocalDateTime.parse(matcher.group(1), LOG_DATE_FORMAT);
                    break;
                } catch (Exception ignored) {
                }
            }
        }

        for (int i = lines.length - 1; i >= 0; i--) {
            Matcher matcher = timestampPattern.matcher(lines[i]);
            if (matcher.find()) {
                try {
                    endTime = LocalDateTime.parse(matcher.group(1), LOG_DATE_FORMAT);
                    break;
                } catch (Exception ignored) {
                }
            }
        }

        return new LocalDateTime[]{startTime, endTime};
    }
}
