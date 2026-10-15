package com.epam.healenium.healenium_proxy.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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

    @SuppressWarnings("unchecked")
    public Mono<UUID> validateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Mono.empty();
        }
        String cacheKey = "key:" + hashKey(rawApiKey);
        CachedAuthResult cached = authCache.getIfPresent(cacheKey);
        if (cached != null) {
            return Mono.just(cached.tenantId());
        }

        return webClient.post()
                .uri("/v1/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("apiKey", rawApiKey))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    Object validObj = map.get("valid");
                    boolean valid = Boolean.TRUE.equals(validObj) || "true".equalsIgnoreCase(String.valueOf(validObj));
                    Object tenantIdObj = map.get("tenantId");
                    if (valid && tenantIdObj != null) {
                        UUID tenantId = UUID.fromString(tenantIdObj.toString());
                        String role = map.get("role") != null ? map.get("role").toString() : "AUTOMATION";
                        authCache.put(cacheKey, new CachedAuthResult(tenantId, role, "ACTIVE"));
                        return Mono.just(tenantId);
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.warn("IAM key validation failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unchecked")
    public Mono<UUID> validateBearerToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Mono.empty();
        }
        String cacheKey = "token:" + hashKey(bearerToken);
        CachedAuthResult cached = authCache.getIfPresent(cacheKey);
        if (cached != null) {
            return Mono.just(cached.tenantId());
        }

        return webClient.post()
                .uri("/v1/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", bearerToken))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    Object validObj = map.get("valid");
                    boolean valid = Boolean.TRUE.equals(validObj) || "true".equalsIgnoreCase(String.valueOf(validObj));
                    Object tenantIdObj = map.get("tenantId");
                    if (valid && tenantIdObj != null) {
                        UUID tenantId = UUID.fromString(tenantIdObj.toString());
                        String role = map.get("role") != null ? map.get("role").toString() : "MEMBER";
                        authCache.put(cacheKey, new CachedAuthResult(tenantId, role, "ACTIVE"));
                        return Mono.just(tenantId);
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.warn("IAM token validation failed: {}", e.getMessage());
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
