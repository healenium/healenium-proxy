package com.epam.healenium.healenium_proxy.auth;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Validates UI Bearer JWT via IamAuthClient, strips external Authorization and API key headers,
 * and injects trusted Healenium-Tenant-Id header.
 */
@Slf4j
@Component
public class TenantAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String TENANT_HEADER = "Healenium-Tenant-Id";
    public static final String API_KEY_HEADER = "X-Api-Key";

    private final IamAuthClient iamAuthClient;
    private final HealeniumAuthProperties properties;

    public TenantAuthGlobalFilter(IamAuthClient iamAuthClient, HealeniumAuthProperties properties) {
        this.iamAuthClient = iamAuthClient;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isBackendDataPath(path)) {
            return chain.filter(exchange);
        }

        // Authentication disabled in Community mode
        if (!properties.getAuth().isEnabled()) {
            return chain.filter(exchange);
        }

        // 1. Check for SDK X-Api-Key header (used by healenium-web REST calls)
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (StringUtils.hasText(apiKey)) {
            return iamAuthClient.validateApiKey(apiKey.trim())
                    .flatMap(tenantId -> forwardWithTenant(exchange, chain, tenantId, API_KEY_HEADER).then(Mono.just(true)))
                    .switchIfEmpty(Mono.defer(() -> rejectUnauthorized(exchange, "Invalid X-Api-Key provided for path: " + path)))
                    .then();
        }

        // 2. Check for UI Bearer JWT (Authorization header, query parameter 'token' / 'access_token', or cookie 'hlm_token')
        String token = extractBearerToken(exchange);
        if (StringUtils.hasText(token) && !"undefined".equalsIgnoreCase(token) && !"null".equalsIgnoreCase(token)) {
            return iamAuthClient.validateBearerToken(token)
                    .flatMap(tenantId -> forwardWithTenant(exchange, chain, tenantId, HttpHeaders.AUTHORIZATION).then(Mono.just(true)))
                    .switchIfEmpty(Mono.defer(() -> rejectUnauthorized(exchange, "Invalid or expired Bearer token for path: " + path)))
                    .then();
        }

        log.warn("Missing or invalid authentication credentials for path: {}", path);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forwardWithTenant(ServerWebExchange exchange, GatewayFilterChain chain,
                                         UUID tenantId, String headerToRemove) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    if (headerToRemove != null) {
                        headers.remove(headerToRemove);
                    }
                    headers.set(TENANT_HEADER, tenantId.toString());
                })
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Boolean> rejectUnauthorized(ServerWebExchange exchange, String logMessage) {
        log.warn(logMessage);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete().then(Mono.just(false));
    }

    public static String extractBearerToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        String queryToken = exchange.getRequest().getQueryParams().getFirst("token");
        if (!StringUtils.hasText(queryToken)) {
            queryToken = exchange.getRequest().getQueryParams().getFirst("access_token");
        }
        if (StringUtils.hasText(queryToken)) {
            return queryToken.trim();
        }
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst("hlm_token");
        if (cookie != null && StringUtils.hasText(cookie.getValue())) {
            return cookie.getValue().trim();
        }
        return null;
    }

    private static boolean isBackendDataPath(String path) {
        return path.startsWith("/healenium/")
                || path.equals("/healenium")
                || path.startsWith("/healenium-ai/")
                || path.equals("/healenium-ai")
                || path.startsWith("/screenshots/")
                || path.equals("/screenshots");
    }

    @Override
    public int getOrder() {
        return -10;
    }
}

