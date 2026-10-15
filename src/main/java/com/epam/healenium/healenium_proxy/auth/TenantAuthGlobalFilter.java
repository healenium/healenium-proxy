package com.epam.healenium.healenium_proxy.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Validates UI Bearer JWT via IamAuthClient, strips external Authorization and legacy headers,
 * and injects trusted Healenium-Tenant-Id header.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String TENANT_HEADER = "Healenium-Tenant-Id";
    public static final String INTERNAL_TOKEN_HEADER = "Healenium-Internal-Token";

    private final IamAuthClient iamAuthClient;
    private final HealeniumAuthProperties properties;

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

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Bearer token for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        return iamAuthClient.validateBearerToken(token)
                .flatMap(tenantId -> {
                    // Gateway Offloading: strip Authorization, strip Healenium-Internal-Token, inject Healenium-Tenant-Id
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.remove(HttpHeaders.AUTHORIZATION);
                                headers.remove(INTERNAL_TOKEN_HEADER);
                                headers.set(TENANT_HEADER, tenantId.toString());
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Invalid or expired Bearer token for path: {}", path);
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }));
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

