package com.epam.healenium.healenium_proxy.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class MembershipClient {

    public static final String INTERNAL_TOKEN_HEADER = "Healenium-Internal-Token";

    private final WebClient.Builder webClientBuilder;
    private final HealeniumAuthProperties properties;
    private final String backendBaseUrl;
    private final Cache<String, List<UUID>> cache;

    public MembershipClient(WebClient.Builder webClientBuilder,
                            HealeniumAuthProperties properties,
                            @Value("${proxy.healenium.container.url}") String backendBaseUrl) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        this.backendBaseUrl = backendBaseUrl;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getMembership().getCacheMaxSize())
                .expireAfterWrite(Duration.parse(properties.getMembership().getCacheTtl()))
                .build();
    }

    public Mono<List<UUID>> resolveTenantIds(String issuer, String sub) {
        String key = issuer + "|" + sub;
        List<UUID> cached = cache.getIfPresent(key);
        if (cached != null) {
            return Mono.just(cached);
        }
        return fetch(issuer, sub)
                .doOnNext(list -> cache.put(key, list));
    }

    /**
     * Auto-provisions a tenant for the user on first login.
     * Idempotent — returns existing tenant if already provisioned.
     */
    public Mono<UUID> provisionTenant(String issuer, String sub) {
        WebClient.RequestHeadersSpec<?> request = webClientBuilder
                .baseUrl(backendBaseUrl)
                .build()
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/membership/provision")
                        .queryParam("issuer", issuer)
                        .queryParam("sub", sub)
                        .build())
                .accept(MediaType.APPLICATION_JSON);

        String token = properties.getM2m().getInternalToken();
        if (StringUtils.hasText(token)) {
            request = request.header(INTERNAL_TOKEN_HEADER, token);
        }

        return request.retrieve()
                .bodyToMono(Map.class)
                .map(body -> UUID.fromString(String.valueOf(body.get("tenantId"))))
                .doOnNext(id -> cache.invalidate(issuer + "|" + sub))
                .onErrorResume(ex -> {
                    log.error("Provisioning failed for iss={} sub={}: {}", issuer, sub, ex.getMessage());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unchecked")
    private Mono<List<UUID>> fetch(String issuer, String sub) {
        WebClient.RequestHeadersSpec<?> request = webClientBuilder
                .baseUrl(backendBaseUrl)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/membership")
                        .queryParam("issuer", issuer)
                        .queryParam("sub", sub)
                        .build())
                .accept(MediaType.APPLICATION_JSON);

        String token = properties.getM2m().getInternalToken();
        if (StringUtils.hasText(token)) {
            request = request.header(INTERNAL_TOKEN_HEADER, token);
        }

        return request.retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    Object tenants = body.get("tenants");
                    if (!(tenants instanceof List<?> list) || list.isEmpty()) {
                        return List.<UUID>of();
                    }
                    return list.stream()
                            .map(String::valueOf)
                            .filter(StringUtils::hasText)
                            .map(UUID::fromString)
                            .toList();
                })
                .onErrorResume(ex -> {
                    log.error("Membership lookup failed for iss={} sub={}: {}", issuer, sub, ex.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }
}
