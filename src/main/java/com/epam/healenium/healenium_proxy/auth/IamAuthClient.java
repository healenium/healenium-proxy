package com.epam.healenium.healenium_proxy.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class IamAuthClient {

    private final WebClient webClient;
    private final Cache<String, CachedAuthResult> authCache;
    private final Clock clock;

    public record CachedAuthResult(UUID tenantId, String role, String status, Instant expiresAt) {}

    @Autowired
    public IamAuthClient(WebClient.Builder webClientBuilder,
                         @Value("${proxy.iam.url:http://healenium-iam:7070}") String iamUrl,
                         @Value("${proxy.iam.cache-ttl-seconds:300}") int cacheTtlSeconds,
                         @Value("${proxy.iam.cache-max-size:10000}") int cacheMaxSize) {
        this(webClientBuilder, iamUrl, cacheTtlSeconds, cacheMaxSize, Clock.systemUTC());
    }

    IamAuthClient(WebClient.Builder webClientBuilder,
                  String iamUrl,
                  int cacheTtlSeconds,
                  int cacheMaxSize,
                  Clock clock) {
        this.webClient = webClientBuilder.baseUrl(iamUrl).build();
        this.clock = clock;
        long defaultTtlNanos = Duration.ofSeconds(Math.max(0, cacheTtlSeconds)).toNanos();
        this.authCache = Caffeine.newBuilder()
                .ticker(() -> {
                    Instant now = clock.instant();
                    return now.getEpochSecond() * 1_000_000_000L + now.getNano();
                })
                .expireAfter(new Expiry<String, CachedAuthResult>() {
                    @Override
                    public long expireAfterCreate(String key, CachedAuthResult value, long currentTime) {
                        return resolveTtlNanos(value, defaultTtlNanos);
                    }

                    @Override
                    public long expireAfterUpdate(String key, CachedAuthResult value,
                                                  long currentTime, long currentDuration) {
                        return resolveTtlNanos(value, defaultTtlNanos);
                    }

                    @Override
                    public long expireAfterRead(String key, CachedAuthResult value,
                                                long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .maximumSize(cacheMaxSize)
                .build();
    }

    private long resolveTtlNanos(CachedAuthResult value, long defaultTtlNanos) {
        if (value.expiresAt() == null) {
            return defaultTtlNanos;
        }
        long remainingNanos = Duration.between(clock.instant(), value.expiresAt()).toNanos();
        if (remainingNanos <= 0) {
            return 0L;
        }
        return Math.min(defaultTtlNanos, remainingNanos);
    }

    public Mono<UUID> validateApiKey(String rawApiKey) {
        return validate("key", "apiKey", rawApiKey, "AUTOMATION", "IAM key validation failed", false);
    }

    public Mono<UUID> validateBearerToken(String bearerToken) {
        return validate("token", "token", bearerToken, "MEMBER", "IAM token validation failed", true);
    }

    /**
     * Resolves tenant ID from request exchange
     * when auth is disabled - from tenant header or default
     * when auth is enabled  - from validated bearer token
     */
    public static Mono<String> resolveTenant(ServerWebExchange exchange,
                                             HealeniumAuthProperties properties,
                                             IamAuthClient iamAuthClient) {
        if (!properties.getAuth().isEnabled()) {
            String requested = exchange.getRequest().getHeaders().getFirst(TenantAuthGlobalFilter.TENANT_HEADER);
            return Mono.just(requested != null ? requested.trim() : properties.getAuth().getDefaultTenantId().trim());
        }
        String token = TenantAuthGlobalFilter.extractBearerToken(exchange);
        if (StringUtils.hasText(token)) {
            return iamAuthClient.validateBearerToken(token).map(UUID::toString);
        }
        return Mono.empty();
    }

    @SuppressWarnings("unchecked")
    private Mono<UUID> validate(String cachePrefix, String payloadKey, String rawValue,
                                String defaultRole, String logLabel, boolean capAtJwtExp) {
        if (rawValue == null || rawValue.isBlank()) {
            return Mono.empty();
        }
        String cacheKey = cachePrefix + ":" + hashKey(rawValue);
        CachedAuthResult cached = authCache.getIfPresent(cacheKey);
        if (cached != null) {
            if (cached.expiresAt() != null && !clock.instant().isBefore(cached.expiresAt())) {
                authCache.invalidate(cacheKey);
            } else {
                return Mono.just(cached.tenantId());
            }
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
                        Instant expiresAt = capAtJwtExp ? extractJwtExpiration(rawValue) : null;
                        if (expiresAt == null || clock.instant().isBefore(expiresAt)) {
                            authCache.put(cacheKey, new CachedAuthResult(tenantId, role, "ACTIVE", expiresAt));
                        }
                        return Mono.just(tenantId);
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.warn("{}: {}", logLabel, e.getMessage());
                    return Mono.empty();
                });
    }

    static Instant extractJwtExpiration(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JSONObject payload = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
            if (payload.has("exp") && !payload.isNull("exp")) {
                Number exp = payload.optNumber("exp");
                if (exp != null && exp.longValue() > 0) {
                    return Instant.ofEpochSecond(exp.longValue());
                }
            }
        } catch (Exception e) {
            log.debug("Unable to parse JWT exp claim for cache capping: {}", e.getMessage());
        }
        return null;
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
