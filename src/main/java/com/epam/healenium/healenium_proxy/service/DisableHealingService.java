package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j(topic = "healenium")
@AllArgsConstructor
@Service
public class DisableHealingService {

    private final ProxyFilterService proxyFilterService;
    private final SessionContextService sessionContextService;

    public Mono<String> process(ServerWebExchange exchange, String disableHealingValue) {
        String errorString = validateDisableHealingCommand(disableHealingValue, exchange);
        if (errorString != null) {
            log.error("[Execute Script] {}", errorString);
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(400));
            return Mono.just(errorString);
        }
        String sessionId = proxyFilterService.getUriVariable(exchange, "session_id");
        ProxySessionContext proxySessionContext = sessionContextService.getSessionContext(sessionId);
        proxySessionContext.getSelfHealingEngine().getSessionContext().setWaitCommand(Boolean.parseBoolean(disableHealingValue));
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(200));
        log.info("[Execute Script] Disable healing command value: {}", disableHealingValue);
        return Mono.just("{\"value\": true}");
    }

    public String getDisableHealingMessageValue(String responseBody) {
        Pattern pattern = Pattern.compile("\"message\":\"[^\"]*disable_healing_(\\w+)");
        Matcher matcher = pattern.matcher(responseBody);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String validateDisableHealingCommand(String disableHealingValue, ServerWebExchange exchange) {
        Boolean isDisableHealing = parseBooleanExtended(disableHealingValue);
        if (isDisableHealing == null) {
            return "Invalid boolean value: " + disableHealingValue;
        }
        String sessionId = proxyFilterService.getUriVariable(exchange, "session_id");
        ProxySessionContext proxySessionContext = sessionContextService.getSessionContext(sessionId);
        if (proxySessionContext == null) {
            return "There are no session context or invalid session id: " + sessionId;
        }
        return null;
    }

    private static Boolean parseBooleanExtended(String value) {
        String normalizedValue = value.trim().toLowerCase();
        if ("true".equals(normalizedValue)) {
            return true;
        } else if ("false".equals(normalizedValue)) {
            return false;
        } else {
            return null;
        }
    }

}
