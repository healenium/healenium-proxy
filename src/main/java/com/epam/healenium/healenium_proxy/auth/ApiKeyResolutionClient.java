package com.epam.healenium.healenium_proxy.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Resolves a tenant UUID from an API key string by calling the backend
 * {@code GET /internal/api-keys/resolve?key=…} endpoint.
 * Results are cached to avoid a round-trip on every element-find request.
 */
@Slf4j
@Service
public class ApiKeyResolutionClient {

    private final WebClient.Builder webClientBuilder;
    private final HealeniumAuthProperties properties;
    private final String backendBaseUrl;
    private final Cache<String, String> cache;

    public ApiKeyResolutionClient(WebClient.Builder webClientBuilder,
                                  HealeniumAuthProperties properties,
                                  @Value("${proxy.healenium.container.url}") String backendBaseUrl) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        this.backendBaseUrl = backendBaseUrl;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getApikey().getCacheMaxSize())
                .expireAfterWrite(Duration.parse(properties.getApikey().getCacheTtl()))
                .build();
    }

    /**
     * Returns the tenant UUID string for the given API key, or empty if not found / DISABLED.
     */
    public Mono<String> resolve(String apiKey) {
        String cached = cache.getIfPresent(apiKey);
        if (cached != null) {
            return Mono.just(cached);
        }
        return fetch(apiKey)
                .doOnNext(tenantId -> cache.put(apiKey, tenantId));
    }

    public void invalidate(String apiKey) {
        cache.invalidate(apiKey);
    }

    @SuppressWarnings("unchecked")
    private Mono<String> fetch(String apiKey) {
        WebClient.RequestHeadersSpec<?> request = webClientBuilder
                .baseUrl(backendBaseUrl)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/api-keys/resolve")
                        .queryParam("key", apiKey)
                        .build())
                .accept(MediaType.APPLICATION_JSON);

        String token = properties.getM2m().getInternalToken();
        if (StringUtils.hasText(token)) {
            request = request.header("Healenium-Internal-Token", token);
        }

        return request.retrieve()
                .bodyToMono(Map.class)
                .map(body -> String.valueOf(body.get("tenantId")))
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("API key not found or disabled: key={}", apiKey);
                    return Mono.empty();
                })
                .onErrorResume(ex -> {
                    log.error("API key resolution failed for key={}: {}", apiKey, ex.getMessage());
                    return Mono.empty();
                });
    }
}
