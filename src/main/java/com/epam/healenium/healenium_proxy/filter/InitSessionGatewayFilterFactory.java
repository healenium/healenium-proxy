package com.epam.healenium.healenium_proxy.filter;

import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class InitSessionGatewayFilterFactory extends AbstractGatewayFilterFactory<InitSessionGatewayFilterFactory.Config> implements Ordered {
    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory;
    private final SessionContextService sessionContextService;

    public InitSessionGatewayFilterFactory(ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory, SessionContextService sessionContextService) {
        super(Config.class);
        this.modifyResponseBodyFilterFactory = modifyResponseBodyFilterFactory;
        this.sessionContextService = sessionContextService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        final ModifyResponseBodyGatewayFilterFactory.Config modifyResponseBodyFilterFactoryConfig = new ModifyResponseBodyGatewayFilterFactory.Config();
        modifyResponseBodyFilterFactoryConfig.setRewriteFunction(String.class, String.class, (swe, bodyAsString) -> {
            // Parse JSON to extract session ID for better logging
            try {
                // Simple JSON parsing to extract sessionId
                String sessionId = extractSessionId(bodyAsString);
                if (sessionId != null) {
                    log.warn("Init Session: sessionId={}", sessionId);
                    log.debug("Init Session JSON: {}", bodyAsString);
                } else {
                    log.warn("Init Session: {}", bodyAsString);
                }
            } catch (Exception e) {
                log.warn("Init Session: {}", bodyAsString);
            }
            
            ProxySessionContext proxySessionContext = sessionContextService.initSessionContext(bodyAsString);
            sessionContextService.submitSessionContext(bodyAsString, proxySessionContext);

            return Mono.just(bodyAsString);
        });
        return modifyResponseBodyFilterFactory.apply(modifyResponseBodyFilterFactoryConfig);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Extract sessionId from JSON string using simple string parsing
     * @param jsonString The JSON string
     * @return sessionId if found, null otherwise
     */
    private String extractSessionId(String jsonString) {
        try {
            // Look for "sessionId":"value" pattern
            int sessionIdIndex = jsonString.indexOf("\"sessionId\"");
            if (sessionIdIndex == -1) {
                return null;
            }
            
            // Find the colon after sessionId
            int colonIndex = jsonString.indexOf(":", sessionIdIndex);
            if (colonIndex == -1) {
                return null;
            }
            
            // Find the opening quote
            int quoteStart = jsonString.indexOf("\"", colonIndex);
            if (quoteStart == -1) {
                return null;
            }
            
            // Find the closing quote
            int quoteEnd = jsonString.indexOf("\"", quoteStart + 1);
            if (quoteEnd == -1) {
                return null;
            }
            
            return jsonString.substring(quoteStart + 1, quoteEnd);
        } catch (Exception e) {
            return null;
        }
    }

    public static class Config {
    }
}
