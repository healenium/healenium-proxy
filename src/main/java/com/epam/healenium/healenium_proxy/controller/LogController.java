package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.model.SessionLogResultDto;
import com.epam.healenium.healenium_proxy.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This proxy's own session log lines, for hlm-iam's session-log view (spec 004, Requirement 3.6).
 * Timestamps are returned as {@code yyyy-MM-dd HH:mm:ss.SSS} text, or null.
 */
@Slf4j(topic = "healenium")
@RestController
@RequiredArgsConstructor
@RequestMapping("/healenium-proxy/logs")
public class LogController {

    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final LogService logService;

    @GetMapping("/session/{sessionId}")
    public Mono<ResponseEntity<Map<String, Object>>> getSessionLogs(@PathVariable String sessionId) {
        return Mono.fromCallable(() -> logService.getLogsForSession(sessionId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> ResponseEntity.ok(body(result)))
                .onErrorResume(e -> {
                    log.error("Error getting proxy logs for session: {}", sessionId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", String.valueOf(e.getMessage()))));
                });
    }

    private static Map<String, Object> body(SessionLogResultDto result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("logs", result.getLogs());
        body.put("startTime", format(result.getStartTime()));
        body.put("endTime", format(result.getEndTime()));
        return body;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(LOG_DATE_FORMAT);
    }
}
