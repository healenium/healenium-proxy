package com.epam.healenium.healenium_proxy.auth;

import java.util.UUID;
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
        String normalizedPath = normalizePath(path);

        // 1. Block external access to internal IAM endpoints (e.g. auth validation)
        if (isInternalPath(normalizedPath)) {
            log.warn("Blocked external request to internal endpoint: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // 2. Allow public IAM endpoints (exchange, refresh, logout) without auth,
        // but sanitize untrusted tenant headers from external clients
        if (isPublicIamPath(normalizedPath)) {
            ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(TENANT_HEADER);
                        headers.remove("X-Tenant-Id");
                    })
                    .build();
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }

        boolean isBackendPath = isBackendDataPath(normalizedPath);
        boolean isIamPath = isProtectedIamPath(normalizedPath);

        // Not a protected IAM route and not a backend data route (e.g. webdriver /wd/hub, local actuator)
        if (!isBackendPath && !isIamPath) {
            return chain.filter(exchange);
        }

        // Authentication disabled in Community mode
        if (!properties.getAuth().isEnabled()) {
            return chain.filter(exchange);
        }

        // Handle protected IAM paths (/iam/users/me, /iam/api-keys, /iam/tenants, /hlm-proxy/me, etc.)
        if (isIamPath) {
            return handleIamAuth(exchange, chain, path);
        }

        // Handle backend data paths (/healenium/**, /healenium-ai/**, /screenshots/**)
        return handleBackendAuth(exchange, chain, path);
    }

    private Mono<Void> handleIamAuth(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        String token = extractBearerToken(exchange);
        if (StringUtils.hasText(token) && !"undefined".equalsIgnoreCase(token) && !"null".equalsIgnoreCase(token)) {
            return iamAuthClient.validateBearerToken(token)
                    .flatMap(tenantId -> forwardToIam(exchange, chain, tenantId, token).then(Mono.just(true)))
                    .switchIfEmpty(Mono.defer(() -> rejectUnauthorized(exchange, "Invalid or expired Bearer token for IAM path: " + path)))
                    .then();
        }

        if (StringUtils.hasText(properties.getAuth().getDefaultTenantId())) {
            try {
                UUID fallbackTenant = UUID.fromString(properties.getAuth().getDefaultTenantId().trim());
                return forwardToIam(exchange, chain, fallbackTenant, null);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid defaultTenantId configured: {}", properties.getAuth().getDefaultTenantId());
            }
        }

        return rejectUnauthorized(exchange, "Missing or invalid Bearer token for IAM path: " + path).then();
    }

    private Mono<Void> handleBackendAuth(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        // 1. Check for SDK X-Api-Key header (used by healenium-web REST calls)
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (StringUtils.hasText(apiKey)) {
            return iamAuthClient.validateApiKey(apiKey.trim())
                    .flatMap(tenantId -> forwardWithTenant(exchange, chain, tenantId).then(Mono.just(true)))
                    .switchIfEmpty(Mono.defer(() -> rejectUnauthorized(exchange, "Invalid X-Api-Key provided for path: " + path)))
                    .then();
        }

        // 2. Check for UI Bearer JWT (Authorization header only)
        String token = extractBearerToken(exchange);
        if (StringUtils.hasText(token) && !"undefined".equalsIgnoreCase(token) && !"null".equalsIgnoreCase(token)) {
            return iamAuthClient.validateBearerToken(token)
                    .flatMap(tenantId -> forwardWithTenant(exchange, chain, tenantId).then(Mono.just(true)))
                    .switchIfEmpty(Mono.defer(() -> rejectUnauthorized(exchange, "Invalid or expired Bearer token for path: " + path)))
                    .then();
        }

        // 3. Fallback to defaultTenantId if configured (single-tenant / local development mode)
        if (StringUtils.hasText(properties.getAuth().getDefaultTenantId())) {
            try {
                UUID fallbackTenant = UUID.fromString(properties.getAuth().getDefaultTenantId().trim());
                return forwardWithTenant(exchange, chain, fallbackTenant);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid defaultTenantId configured: {}", properties.getAuth().getDefaultTenantId());
            }
        }

        log.warn("Missing or invalid authentication credentials for path: {}", path);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forwardToIam(ServerWebExchange exchange, GatewayFilterChain chain,
                                    UUID tenantId, String token) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(TENANT_HEADER);
                    headers.remove("X-Tenant-Id");
                    headers.set(TENANT_HEADER, tenantId.toString());
                    headers.set("X-Tenant-Id", tenantId.toString());
                    if (StringUtils.hasText(token)) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> forwardWithTenant(ServerWebExchange exchange, GatewayFilterChain chain,
                                         UUID tenantId) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.remove(API_KEY_HEADER);
                    headers.remove(HttpHeaders.COOKIE);
                    headers.remove("X-Tenant-Id");
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

    private String extractBearerToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return (path.length() > 1 && path.endsWith("/")) ? path.substring(0, path.length() - 1) : path;
    }

    private static boolean isInternalPath(String path) {
        return path.equals("/iam/auth/validate") || path.startsWith("/iam/auth/validate/");
    }

    private static boolean isPublicIamPath(String path) {
        return path.equals("/iam/auth/exchange")
                || path.equals("/iam/auth/refresh")
                || path.equals("/iam/auth/logout");
    }

    private static boolean isProtectedIamPath(String path) {
        return path.startsWith("/iam/") || path.equals("/iam") || path.equals("/hlm-proxy/me");
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

