package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.auth.AlbOidcTenantFilter;
import com.epam.healenium.healenium_proxy.auth.ApiKeyProxyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User-facing API key management.
 * Auth is enforced by AlbOidcTenantFilter — tenantId is read from exchange attributes.
 */
@RestController
@RequestMapping("/hlm-proxy/api-keys")
@RequiredArgsConstructor
public class ApiKeysController {

    private final ApiKeyProxyClient apiKeyProxyClient;

    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> list(ServerWebExchange exchange) {
        UUID tenantId = resolveTenant(exchange);
        if (tenantId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return apiKeyProxyClient.list(tenantId)
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> create(
            @RequestBody(required = false) Map<String, String> body,
            ServerWebExchange exchange) {
        UUID tenantId = resolveTenant(exchange);
        if (tenantId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String name = (body != null && StringUtils.hasText(body.get("name"))) ? body.get("name") : "default";
        return apiKeyProxyClient.create(tenantId, name)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .onErrorResume(ex -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> revoke(@PathVariable UUID id, ServerWebExchange exchange) {
        UUID tenantId = resolveTenant(exchange);
        if (tenantId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return apiKeyProxyClient.revoke(id)
                .<ResponseEntity<Void>>then(Mono.just(ResponseEntity.<Void>noContent().build()))
                .onErrorResume(ex -> Mono.just(ResponseEntity.<Void>status(HttpStatus.INTERNAL_SERVER_ERROR).build()));
    }

    private UUID resolveTenant(ServerWebExchange exchange) {
        String raw = exchange.getAttribute(AlbOidcTenantFilter.RESOLVED_TENANT_ATTR);
        if (!StringUtils.hasText(raw)) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
