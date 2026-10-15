package com.epam.healenium.healenium_proxy.filter;

import com.epam.healenium.healenium_proxy.auth.HealeniumAuthProperties;
import com.epam.healenium.healenium_proxy.auth.IamAuthClient;
import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
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

    public static final String API_KEY_CAPABILITY = "hlm:api_key";
    private static final String WEBDRIVER_SESSION_ERROR =
            "{\"value\":{\"error\":\"session not created\",\"message\":\"Invalid or missing Healenium API key\"}}";

    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory;
    private final SessionContextService sessionContextService;
    private final HealeniumAuthProperties properties;
    private final IamAuthClient iamAuthClient;

    public InitSessionGatewayFilterFactory(
            ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory,
            SessionContextService sessionContextService,
            HealeniumAuthProperties properties,
            IamAuthClient iamAuthClient) {
        super(Config.class);
        this.modifyResponseBodyFilterFactory = modifyResponseBodyFilterFactory;
        this.sessionContextService = sessionContextService;
        this.properties = properties;
        this.iamAuthClient = iamAuthClient;
    }

    @Override
    public GatewayFilter apply(Config config) {
        GatewayFilter filter = (exchange, chain) -> {
            // Read request body to extract capability-based API key & strip it
            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        String requestBody = bytes.length == 0 ? "" : new String(bytes, StandardCharsets.UTF_8);
                        String apiKey = extractApiKeyFromRequestBody(requestBody);
                        String cleanedBody = stripApiKeyFromRequestBody(requestBody);
                        byte[] cleanedBytes = cleanedBody.getBytes(StandardCharsets.UTF_8);

                        ServerHttpRequest.Builder reqBuilder = exchange.getRequest().mutate();
                        if (cleanedBytes.length != bytes.length) {
                            reqBuilder.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(cleanedBytes.length));
                        }
                        ServerHttpRequestDecorator cachedRequest =
                                new ServerHttpRequestDecorator(reqBuilder.build()) {
                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        return Flux.just(exchange.getResponse().bufferFactory().wrap(cleanedBytes));
                                    }
                                };
                        ServerWebExchange mutatedExchange = exchange.mutate().request(cachedRequest).build();

                        if (!properties.getAuth().isEnabled()) {
                            // Open Source mode: permit session with default anonymous tenant
                            return buildResponseRewrite(null).filter(mutatedExchange, chain);
                        }

                        // Commercial mode: auth.enabled=true
                        if (!StringUtils.hasText(apiKey)) {
                            log.warn("Unauthorized session creation attempt. Missing {}", API_KEY_CAPABILITY);
                            return rejectSession(exchange);
                        }

                        return iamAuthClient.validateApiKey(apiKey.trim())
                                .flatMap(tenantId -> {
                                    log.info("Session init authorized for tenant: {}", tenantId);
                                    return buildResponseRewrite(tenantId.toString()).filter(mutatedExchange, chain);
                                })
                                .switchIfEmpty(Mono.defer(() -> {
                                    log.warn("Unauthorized session creation attempt. Invalid or disabled {}", API_KEY_CAPABILITY);
                                    return rejectSession(exchange);
                                }));
                    });
        };
        return new OrderedGatewayFilter(filter, NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1);
    }

    private GatewayFilter buildResponseRewrite(String preResolvedTenantId) {
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
                } else {
                    log.debug("Init Healenium-Tenant-Id: capability '{}' not found", API_KEY_CAPABILITY);
                }
            } catch (Exception e) {
                log.warn("Init Session: unable to parse response body. msg={}", e.getMessage());
            }

            proxySessionContext.setTenantId(tenantId);
            try {
                sessionContextService.submitSessionContext(bodyAsString, proxySessionContext);
            } catch (Exception e) {
                log.error("Init Session: submitSessionContext failed, context not saved: {}", e.getMessage(), e);
            }
            return Mono.just(bodyAsString);
        });
        return modifyResponseBodyFilterFactory.apply(responseConfig);
    }

    private Mono<Void> rejectSession(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = WEBDRIVER_SESSION_ERROR.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
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

    static String extractApiKeyFromRequestBody(String requestBody) {
        if (!StringUtils.hasText(requestBody)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(requestBody);
            if (json.has("capabilities")) {
                JSONObject caps = json.optJSONObject("capabilities");
                if (caps != null) {
                    if (caps.has("alwaysMatch")) {
                        JSONObject alwaysMatch = caps.optJSONObject("alwaysMatch");
                        if (alwaysMatch != null) {
                            String val = getJsonCapability(alwaysMatch);
                            if (val != null) return val;
                        }
                    }
                    if (caps.has("firstMatch")) {
                        JSONArray firstMatch = caps.optJSONArray("firstMatch");
                        if (firstMatch != null) {
                            for (int i = 0; i < firstMatch.length(); i++) {
                                JSONObject matchObj = firstMatch.optJSONObject(i);
                                if (matchObj != null) {
                                    String val = getJsonCapability(matchObj);
                                    if (val != null) return val;
                                }
                            }
                        }
                    }
                }
            }
            if (json.has("desiredCapabilities")) {
                JSONObject desired = json.optJSONObject("desiredCapabilities");
                if (desired != null) {
                    String val = getJsonCapability(desired);
                    if (val != null) return val;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse request body for API key: {}", e.getMessage());
        }
        return null;
    }

    static String stripApiKeyFromRequestBody(String requestBody) {
        if (!StringUtils.hasText(requestBody)) {
            return requestBody;
        }
        try {
            JSONObject json = new JSONObject(requestBody);
            boolean modified = false;
            if (json.has("capabilities")) {
                JSONObject caps = json.optJSONObject("capabilities");
                if (caps != null) {
                    if (caps.has("alwaysMatch")) {
                        JSONObject alwaysMatch = caps.optJSONObject("alwaysMatch");
                        if (alwaysMatch != null && alwaysMatch.has(API_KEY_CAPABILITY)) {
                            alwaysMatch.remove(API_KEY_CAPABILITY);
                            modified = true;
                        }
                    }
                    if (caps.has("firstMatch")) {
                        JSONArray firstMatch = caps.optJSONArray("firstMatch");
                        if (firstMatch != null) {
                            for (int i = 0; i < firstMatch.length(); i++) {
                                JSONObject matchObj = firstMatch.optJSONObject(i);
                                if (matchObj != null && matchObj.has(API_KEY_CAPABILITY)) {
                                    matchObj.remove(API_KEY_CAPABILITY);
                                    modified = true;
                                }
                            }
                        }
                    }
                }
            }
            if (json.has("desiredCapabilities")) {
                JSONObject desired = json.optJSONObject("desiredCapabilities");
                if (desired != null && desired.has(API_KEY_CAPABILITY)) {
                    desired.remove(API_KEY_CAPABILITY);
                    modified = true;
                }
            }
            return modified ? json.toString() : requestBody;
        } catch (Exception e) {
            log.debug("Failed to strip API key from request body: {}", e.getMessage());
            return requestBody;
        }
    }

    private static String getJsonCapability(JSONObject caps) {
        return caps.optString(API_KEY_CAPABILITY, null);
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

