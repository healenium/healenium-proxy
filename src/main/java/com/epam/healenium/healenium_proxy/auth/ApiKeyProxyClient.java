package com.epam.healenium.healenium_proxy.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proxy-side client for the backend /internal/api-keys endpoint.
 * Called by ApiKeysController to manage API keys on behalf of the authenticated user.
 */
@Slf4j
@Service
public class ApiKeyProxyClient {

    private final WebClient webClient;

    public ApiKeyProxyClient(WebClient.Builder webClientBuilder,
                             HealeniumAuthProperties properties,
                             @Value("${proxy.healenium.container.url}") String backendBaseUrl) {
        WebClient.Builder builder = webClientBuilder.baseUrl(backendBaseUrl);
        String token = properties.getM2m().getInternalToken();
        if (StringUtils.hasText(token)) {
            builder = builder.defaultHeader(MembershipClient.INTERNAL_TOKEN_HEADER, token);
        }
        this.webClient = builder.build();
    }

    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> list(UUID tenantId) {
        return webClient.get()
                .uri(b -> b.path("/internal/api-keys").queryParam("tenantId", tenantId).build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(Map.class)
                .cast((Class<Map<String, Object>>) (Class<?>) Map.class)
                .collectList()
                .onErrorResume(ex -> {
                    log.error("api-keys list failed tenantId={}: {}", tenantId, ex.getMessage());
                    return Mono.just(List.of());
                });
    }

    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> create(UUID tenantId, String name) {
        Map<String, Object> body = Map.of("tenantId", tenantId.toString(), "name", name);
        return webClient.post()
                .uri("/internal/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .cast((Class<Map<String, Object>>) (Class<?>) Map.class)
                .onErrorResume(ex -> {
                    log.error("api-keys create failed tenantId={}: {}", tenantId, ex.getMessage());
                    return Mono.error(ex);
                });
    }

    public Mono<Void> revoke(UUID id) {
        return webClient.delete()
                .uri("/internal/api-keys/{id}", id)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(ex -> {
                    log.error("api-keys revoke failed id={}: {}", id, ex.getMessage());
                    return Mono.error(ex);
                });
    }
}
