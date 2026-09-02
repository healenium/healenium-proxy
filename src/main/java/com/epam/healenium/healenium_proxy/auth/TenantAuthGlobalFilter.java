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
 * For backend UI routes: reads pre-resolved tenantId from exchange attribute (set by AlbOidcTenantFilter),
 * sets {@code Healenium-Tenant-Id} and {@code Healenium-Internal-Token}, strips user Authorization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String TENANT_HEADER = "Healenium-Tenant-Id";
    public static final String INTERNAL_TOKEN_HEADER = "Healenium-Internal-Token";

    private final HealeniumAuthProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isBackendDataPath(path)) {
            return chain.filter(exchange);
        }

        if (!properties.getAuth().isEnabled()) {
            return chain.filter(withInternalTokenOnly(exchange));
        }

        String tenantId = exchange.getAttribute(AlbOidcTenantFilter.RESOLVED_TENANT_ATTR);
        if (!StringUtils.hasText(tenantId)) {
            return unauthorized(exchange, "Tenant not resolved — OIDC header required");
        }

        ServerHttpRequest request = mutateBackendHeaders(exchange.getRequest(), tenantId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    private ServerWebExchange withInternalTokenOnly(ServerWebExchange exchange) {
        String token = properties.getM2m().getInternalToken();
        if (!StringUtils.hasText(token)) {
            return exchange;
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(INTERNAL_TOKEN_HEADER, token))
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerHttpRequest mutateBackendHeaders(ServerHttpRequest original, String tenantId) {
        return original.mutate()
                .headers(headers -> {
                    headers.set(TENANT_HEADER, tenantId);
                    String token = properties.getM2m().getInternalToken();
                    if (StringUtils.hasText(token)) {
                        headers.set(INTERNAL_TOKEN_HEADER, token);
                    }
                    headers.remove(HttpHeaders.AUTHORIZATION);
                })
                .build();
    }

    private static boolean isBackendDataPath(String path) {
        return path.startsWith("/healenium/")
                || path.equals("/healenium")
                || path.startsWith("/screenshots/")
                || path.equals("/screenshots");
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        log.warn("TenantAuth 401: {}", message);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 50;
    }
}
