package com.epam.healenium.healenium_proxy.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class IamAuthClient {

    private final WebClient webClient;
    private final Cache<String, CachedAuthResult> authCache;

    public record CachedAuthResult(UUID tenantId, String role, String status) {}

    public IamAuthClient(WebClient.Builder webClientBuilder,
                         @Value("${proxy.iam.url:http://healenium-iam:7070}") String iamUrl,
                         @Value("${proxy.iam.cache-ttl-seconds:300}") int cacheTtlSeconds,
                         @Value("${proxy.iam.cache-max-size:10000}") int cacheMaxSize) {
        this.webClient = webClientBuilder.baseUrl(iamUrl).build();
        this.authCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                .maximumSize(cacheMaxSize)
                .build();
    }

    public Mono<UUID> validateApiKey(String rawApiKey) {
        return validate("key", "apiKey", rawApiKey, "AUTOMATION", "IAM key validation failed");
    }

    public Mono<UUID> validateBearerToken(String bearerToken) {
        return validate("token", "token", bearerToken, "MEMBER", "IAM token validation failed");
    }

    /**
     * Resolves tenant ID from request exchange (bearer token, header, or defaultTenantId fallback).
     */
    public static Mono<String> resolveTenant(ServerWebExchange exchange,
                                             HealeniumAuthProperties properties,
                                             IamAuthClient iamAuthClient) {
        if (!properties.getAuth().isEnabled()) {
            String requested = exchange.getRequest().getHeaders().getFirst(TenantAuthGlobalFilter.TENANT_HEADER);
            return Mono.just(requested != null ? requested.trim() : "");
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return iamAuthClient.validateBearerToken(authHeader.substring(7)).map(UUID::toString);
        }
        String tenantHeader = exchange.getRequest().getHeaders().getFirst(TenantAuthGlobalFilter.TENANT_HEADER);
        if (StringUtils.hasText(tenantHeader)) {
            return Mono.just(tenantHeader.trim());
        }
        if (StringUtils.hasText(properties.getAuth().getDefaultTenantId())) {
            return Mono.just(properties.getAuth().getDefaultTenantId().trim());
        }
        return Mono.empty();
    }

    @SuppressWarnings("unchecked")
    private Mono<UUID> validate(String cachePrefix, String payloadKey, String rawValue,
                                String defaultRole, String logLabel) {
        if (rawValue == null || rawValue.isBlank()) {
            return Mono.empty();
        }
        String cacheKey = cachePrefix + ":" + hashKey(rawValue);
        CachedAuthResult cached = authCache.getIfPresent(cacheKey);
        if (cached != null) {
            return Mono.just(cached.tenantId());
        }

        return webClient.post()
                .uri("/v1/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(payloadKey, rawValue))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    Object validObj = map.get("valid");
                    boolean valid = Boolean.TRUE.equals(validObj) || "true".equalsIgnoreCase(String.valueOf(validObj));
                    Object tenantIdObj = map.get("tenantId");
                    if (valid && tenantIdObj != null) {
                        UUID tenantId = UUID.fromString(tenantIdObj.toString());
                        String role = map.get("role") != null ? map.get("role").toString() : defaultRole;
                        authCache.put(cacheKey, new CachedAuthResult(tenantId, role, "ACTIVE"));
                        return Mono.just(tenantId);
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.warn("{}: {}", logLabel, e.getMessage());
                    return Mono.empty();
                });
    }

    private String hashKey(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
