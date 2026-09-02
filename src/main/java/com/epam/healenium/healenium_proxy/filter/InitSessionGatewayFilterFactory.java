package com.epam.healenium.healenium_proxy.filter;

import com.epam.healenium.healenium_proxy.auth.HealeniumAuthProperties;
import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class InitSessionGatewayFilterFactory
        extends AbstractGatewayFilterFactory<InitSessionGatewayFilterFactory.Config>
        implements Ordered {

    private static final String API_KEY_CAPABILITY = "hlm:api_key";
    private static final String WEBDRIVER_SESSION_ERROR =
            "{\"value\":{\"error\":\"session not created\",\"message\":\"Invalid or missing Healenium API key\"}}";

    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory;
    private final SessionContextService sessionContextService;
    private final HealeniumAuthProperties properties;

    public InitSessionGatewayFilterFactory(
            ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory,
            SessionContextService sessionContextService,
            HealeniumAuthProperties properties) {
        super(Config.class);
        this.modifyResponseBodyFilterFactory = modifyResponseBodyFilterFactory;
        this.sessionContextService = sessionContextService;
        this.properties = properties;
    }

    @Override
    public GatewayFilter apply(Config config) {
        GatewayFilter filter = (exchange, chain) -> {
            log.info("Init session filter");
            if (!properties.getAuth().isEnabled()) {
                log.info("Init session filter isEnabled = false");
                return buildResponseRewrite(null).filter(exchange, chain);
            }
            log.info("Init session filter 2");
            // auth.enabled=true: validate API key before forwarding to Selenium
            // Read request body to extract capability-based API key
            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                    .flatMap(dataBuffer -> {
                        log.info("Init session filter 3");
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String apiKey = extractApiKeyFromRequestBody(new String(bytes, StandardCharsets.UTF_8));
                        log.info("Init session filter apiKey: {}", apiKey);
                        if (!isValidUuid(apiKey)) {
                            log.warn("InitSession: invalid or missing API key in capabilities");
                            return rejectSession(exchange);
                        }
                        log.info("Init session filter isValidUuid");
                        // Reconstruct request with cached body so Selenium can still read it
                        ServerHttpRequestDecorator cachedRequest =
                                new ServerHttpRequestDecorator(exchange.getRequest()) {
                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                                    }
                                };
                        log.info("Init session filter cachedRequest: {}", cachedRequest);
                        ServerWebExchange mutatedExchange = exchange.mutate().request(cachedRequest).build();
                        return buildResponseRewrite(apiKey.trim()).filter(mutatedExchange, chain);
                    });
        };
        return new OrderedGatewayFilter(filter, NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1);
    }

    private GatewayFilter buildResponseRewrite(String preResolvedTenantId) {
        log.info("Init session filter start buildResponseRewrite");
        ModifyResponseBodyGatewayFilterFactory.Config responseConfig =
                new ModifyResponseBodyGatewayFilterFactory.Config();
        responseConfig.setRewriteFunction(String.class, String.class, (swe, bodyAsString) -> {
            log.info("Init Session: response rewrite invoked, bodyLength={}", bodyAsString != null ? bodyAsString.length() : null);
            ProxySessionContext proxySessionContext = sessionContextService.initSessionContext(bodyAsString);
            String sessionId = null;
            String tenantId = preResolvedTenantId;

            try {
                sessionId = sessionContextService.peekSessionId(bodyAsString);
                if (tenantId == null) {
                    // auth.enabled=false: read tenant from response capabilities
                    Map<String, Object> capabilities = sessionContextService.peekCapabilities(bodyAsString);
                    tenantId = resolveTenantId(capabilities);
                }

                if (sessionId != null) {
                    log.info("Init Session: sessionId={}", sessionId);
                } else {
                    log.warn("Init Session: unable to extract sessionId");
                }

                if (tenantId != null) {
                    log.info("Init Healenium-Tenant-Id={}", tenantId);
                } else if (isM2mConfigured()) {
                    log.error("Init Session: Pro M2M configured but capability '{}' missing or invalid UUID; "
                                    + "skipping Healenium session context (healing disabled for this session)",
                            API_KEY_CAPABILITY);
                    return Mono.just(bodyAsString);
                } else {
                    log.debug("Init Healenium-Tenant-Id: capability '{}' not found", API_KEY_CAPABILITY);
                }
            } catch (Exception e) {
                log.warn("Init Session: unable to parse response body. msg={}", e.getMessage());
            }

            if (isM2mConfigured() && !StringUtils.hasText(tenantId)) {
                log.error("Init Session: refusing to register Healenium session without tenant when M2M is configured");
                return Mono.just(bodyAsString);
            }

            proxySessionContext.setTenantId(tenantId);
            try {
                sessionContextService.submitSessionContext(bodyAsString, proxySessionContext);
            } catch (Exception e) {
                log.error("Init Session: submitSessionContext failed, context not saved: {}", e.getMessage(), e);
            }
            return Mono.just(bodyAsString);
        });
        log.info("Init session filter end buildResponseRewrite");
        return modifyResponseBodyFilterFactory.apply(responseConfig);
    }

    private Mono<Void> rejectSession(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = WEBDRIVER_SESSION_ERROR.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private boolean isM2mConfigured() {
        return StringUtils.hasText(properties.getM2m().getInternalToken());
    }

    static String resolveTenantId(Map<String, Object> capabilities) {
        String tenantId = getCapabilityAsString(capabilities, API_KEY_CAPABILITY);
        if (tenantId == null) {
            return null;
        }
        try {
            return UUID.fromString(tenantId.trim()).toString();
        } catch (IllegalArgumentException e) {
            log.error("Init Session: capability tenant value is not a UUID: {}", tenantId);
            return null;
        }
    }

    private static String extractApiKeyFromRequestBody(String requestBody) {
        if (!StringUtils.hasText(requestBody)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(requestBody);
            if (json.has("capabilities")) {
                JSONObject caps = json.getJSONObject("capabilities");
                // W3C: capabilities.alwaysMatch
                if (caps.has("alwaysMatch")) {
                    String val = getJsonCapability(caps.getJSONObject("alwaysMatch"));
                    if (val != null) return val;
                }
                // W3C: capabilities.firstMatch[0] — Selenium 4 puts custom caps here
                if (caps.has("firstMatch")) {
                    org.json.JSONArray firstMatch = caps.getJSONArray("firstMatch");
                    if (firstMatch.length() > 0) {
                        String val = getJsonCapability(firstMatch.getJSONObject(0));
                        if (val != null) return val;
                    }
                }
            }
            // Legacy: desiredCapabilities
            if (json.has("desiredCapabilities")) {
                String val = getJsonCapability(json.getJSONObject("desiredCapabilities"));
                if (val != null) return val;
            }
        } catch (Exception e) {
            log.debug("Failed to parse request body for API key: {}", e.getMessage());
        }
        return null;
    }

    private static String getJsonCapability(JSONObject caps) {
        return caps.optString(API_KEY_CAPABILITY, null);
    }

    private static boolean isValidUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String getCapabilityAsString(Map<String, Object> capabilities, String name) {
        if (capabilities == null) {
            return null;
        }
        Object value = capabilities.get(name);
        return value != null ? String.valueOf(value) : null;
    }

    public static class Config {
    }
}
