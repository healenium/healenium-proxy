package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.auth.AlbOidcTenantFilter;
import com.epam.healenium.healenium_proxy.auth.HealeniumAuthProperties;
import com.epam.healenium.healenium_proxy.auth.MembershipClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * UI bootstrap: list tenants for the authenticated Cognito user.
 * Reads identity from exchange attributes set by AlbOidcTenantFilter.
 */
@RestController
@RequestMapping("/hlm-proxy/me")
@RequiredArgsConstructor
public class MeController {

    private final MembershipClient membershipClient;
    private final HealeniumAuthProperties authProperties;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> me(ServerWebExchange exchange) {
        String iss   = exchange.getAttribute(AlbOidcTenantFilter.RESOLVED_ISS_ATTR);
        String sub   = exchange.getAttribute(AlbOidcTenantFilter.RESOLVED_SUB_ATTR);
        String email    = exchange.getAttribute(AlbOidcTenantFilter.RESOLVED_EMAIL_ATTR);
        String username = exchange.getAttribute(AlbOidcTenantFilter.RESOLVED_USERNAME_ATTR);

        if (!StringUtils.hasText(iss) || !StringUtils.hasText(sub)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.<String, Object>of("error", "OIDC identity not available")));
        }

        return membershipClient.resolveTenantIds(iss, sub)
                .flatMap(tenants -> {
                    if (!tenants.isEmpty()) {
                        return Mono.just(tenants);
                    }
                    // First login: auto-provision a tenant for this user
                    return membershipClient.provisionTenant(iss, sub)
                            .flatMap(provisioned -> membershipClient.resolveTenantIds(iss, sub))
                            .defaultIfEmpty(tenants);
                })
                .map(tenants -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("issuer", iss);
                    body.put("sub", sub);
                    if (StringUtils.hasText(username)) body.put("username", username);
                    if (StringUtils.hasText(email)) body.put("email", email);
                    body.put("tenants", tenants.stream().map(UUID::toString).toList());
                    body.put("defaultTenantId", tenants.isEmpty() ? "" : tenants.getFirst().toString());
                    body.put("cognitoDomain", authProperties.getAuth().getCognitoDomain());
                    body.put("clientId", authProperties.getAuth().getClientId());
                    return ResponseEntity.ok(body);
                });
    }
}
