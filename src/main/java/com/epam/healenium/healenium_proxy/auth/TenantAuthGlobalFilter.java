package com.epam.healenium.healenium_proxy.auth;

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

import java.util.UUID;

/**
 * Pro-mode gateway for CI/CD traffic (spec 004, Requirement 5). Only the SDK endpoints in
 * {@link SdkEndpoints} are forwarded, and only with a valid {@code X-Api-Key}, which is validated by
 * hlm-iam. Every other backend, AI, IAM or proxy-management path answers 404. The dashboard no longer
 * reaches this proxy, so there is no bearer-token path here.
 *
 * <p>Community mode (auth disabled) passes every request through unchanged.
 * WebDriver paths are not backend data paths and pass through in both modes.
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
        if (!properties.getAuth().isEnabled()) {
            return chain.filter(exchange);
        }

        String path = normalizePath(exchange.getRequest().getURI().getPath());

        if (isNotServedHere(path)) {
            log.debug("Not served on the CI/CD port: {}", path);
            return reject(exchange, HttpStatus.NOT_FOUND);
        }
        if (!isBackendDataPath(path)) {
            return chain.filter(exchange);
        }
        if (!SdkEndpoints.isAllowed(exchange.getRequest().getMethod(), path)) {
            log.debug("Not an SDK endpoint: {} {}", exchange.getRequest().getMethod(), path);
            return reject(exchange, HttpStatus.NOT_FOUND);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Missing X-Api-Key for {}", path);
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        return iamAuthClient.validateApiKey(apiKey.trim())
                .flatMap(tenantId -> forwardWithTenant(exchange, chain, tenantId).then(Mono.just(true)))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Invalid X-Api-Key for {}", path);
                    return reject(exchange, HttpStatus.UNAUTHORIZED).then(Mono.just(false));
                }))
                .then();
    }

    private Mono<Void> forwardWithTenant(ServerWebExchange exchange, GatewayFilterChain chain, UUID tenantId) {
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

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    /** IAM paths moved to hlm-iam, and proxy management paths that have no controller here. */
    static boolean isNotServedHere(String path) {
        return path.equals("/iam") || path.startsWith("/iam/")
                || path.equals("/hlm-proxy") || path.startsWith("/hlm-proxy/")
                || path.equals("/actuator") || path.startsWith("/actuator/");
    }

    static boolean isBackendDataPath(String path) {
        return path.startsWith("/healenium/")
                || path.equals("/healenium")
                || path.startsWith("/healenium-ai/")
                || path.equals("/healenium-ai")
                || path.startsWith("/screenshots/")
                || path.equals("/screenshots");
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return (path.length() > 1 && path.endsWith("/")) ? path.substring(0, path.length() - 1) : path;
    }

    @Override
    public int getOrder() {
        return -10;
    }
}
