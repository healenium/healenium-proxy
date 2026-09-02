package com.epam.healenium.healenium_proxy.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Shared tenant selection for gateway filter and proxy-local UI controllers.
 */
@Component
@RequiredArgsConstructor
public class TenantResolver {

    private final HealeniumAuthProperties properties;

    /**
     * Resolve tenant for proxy-local controllers ({@code /hlm-proxy/settings}, {@code /hlm-proxy/logs}).
     * <p>
     * auth.enabled=true: reads pre-resolved tenantId from exchange attribute (set by AlbOidcTenantFilter).
     * auth.enabled=false: reads Healenium-Tenant-Id header directly.
     */
    public Mono<String> resolve(ServerWebExchange exchange) {
        String requested = exchange.getRequest().getHeaders().getFirst(TenantAuthGlobalFilter.TENANT_HEADER);
        if (!properties.getAuth().isEnabled()) {
            return resolveWhenAuthDisabled(requested);
        }
        String tenantId = exchange.getAttribute(AlbOidcTenantFilter.RESOLVED_TENANT_ATTR);
        if (!StringUtils.hasText(tenantId)) {
            return Mono.error(new TenantResolutionException(HttpStatus.UNAUTHORIZED, "Tenant not resolved"));
        }
        return Mono.just(tenantId);
    }

    /**
     * Auth off: use request header when present; require it when M2M is configured (Pro).
     * Empty string = Free/dev (no tenant header on outbound calls).
     */
    private Mono<String> resolveWhenAuthDisabled(String requestedRaw) {
        boolean m2mConfigured = StringUtils.hasText(properties.getM2m().getInternalToken());
        if (StringUtils.hasText(requestedRaw)) {
            try {
                return Mono.just(UUID.fromString(requestedRaw.trim()).toString());
            } catch (IllegalArgumentException e) {
                return Mono.error(new TenantResolutionException(
                        HttpStatus.BAD_REQUEST, "Healenium-Tenant-Id must be a UUID"));
            }
        }
        if (m2mConfigured) {
            return Mono.error(new TenantResolutionException(
                    HttpStatus.BAD_REQUEST, "Healenium-Tenant-Id header is required"));
        }
        return Mono.just("");
    }

    /**
     * Select tenant from membership list and optional requested header value.
     * Used by AlbOidcTenantFilter.
     */
    public UUID selectFromMembership(List<UUID> tenants, String requestedRaw) {
        if (tenants == null || tenants.isEmpty()) {
            String defaultId = properties.getAuth().getDefaultTenantId();
            if (StringUtils.hasText(defaultId)) {
                return UUID.fromString(defaultId);
            }
            throw new TenantResolutionException(HttpStatus.FORBIDDEN, "No tenant membership");
        }

        if (tenants.size() == 1) {
            UUID selected = tenants.getFirst();
            if (StringUtils.hasText(requestedRaw)) {
                UUID requested = parseUuid(requestedRaw);
                if (!requested.equals(selected)) {
                    throw new TenantResolutionException(
                            HttpStatus.FORBIDDEN, "Healenium-Tenant-Id is not allowed for this user");
                }
            }
            return selected;
        }

        if (!StringUtils.hasText(requestedRaw)) {
            throw new TenantResolutionException(
                    HttpStatus.BAD_REQUEST, "Healenium-Tenant-Id is required when user has multiple tenants");
        }
        UUID requested = parseUuid(requestedRaw);
        if (!tenants.contains(requested)) {
            throw new TenantResolutionException(
                    HttpStatus.FORBIDDEN, "Healenium-Tenant-Id is not allowed for this user");
        }
        return requested;
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new TenantResolutionException(HttpStatus.BAD_REQUEST, "Healenium-Tenant-Id must be a UUID");
        }
    }

    @Getter
    public static final class TenantResolutionException extends RuntimeException {
        private final HttpStatus status;

        public TenantResolutionException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }
    }
}
