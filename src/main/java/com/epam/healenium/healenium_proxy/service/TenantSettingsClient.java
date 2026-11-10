package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.healenium_proxy.auth.HealeniumAuthProperties;
import com.epam.healenium.healenium_proxy.model.TenantSettingsDto;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "healenium")
@Service
@RequiredArgsConstructor
public class TenantSettingsClient {

    private final HealeniumRestService restService;
    private final HealeniumAuthProperties authProperties;

    // Cache tenant settings for 3 minutes to avoid overhead on every session initialization
    private final Cache<String, TenantSettingsDto> cache = Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /**
     * Anonymous user: no tenant, or {@code healenium.auth.default-tenant-id}.
     * Its settings are the proxy defaults, never a tenant's row.
     */
    public boolean isDefaultTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return true;
        }
        String configured = authProperties.getAuth().getDefaultTenantId();
        return StringUtils.hasText(configured) && configured.trim().equalsIgnoreCase(tenantId.trim());
    }

    /**
     * Get settings for tenant reactively (non-blocking)
     *
     * @param tenantId UUID string of the tenant
     * @return Mono with TenantSettingsDto, or empty if unavailable or for the default tenant
     */
    public Mono<TenantSettingsDto> getSettingsReactive(String tenantId) {
        if (isDefaultTenant(tenantId)) {
            return Mono.empty();
        }

        String key = tenantId.trim();
        TenantSettingsDto cached = cache.getIfPresent(key);
        if (cached != null) {
            return Mono.just(cached);
        }

        return restService.getTenantSettings(key)
                .doOnNext(fetched -> {
                    if (fetched != null) {
                        cache.put(key, fetched);
                    }
                })
                .onErrorResume(e -> {
                    log.debug("Error fetching tenant settings from backend for tenant {}: {}", tenantId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Get settings for tenant (with in-memory cache)
     * Never blocks on Reactor Netty event loop threads!
     *
     * @param tenantId UUID string of the tenant
     * @return TenantSettingsDto, or null if unavailable or for the default tenant
     */
    public TenantSettingsDto getSettings(String tenantId) {
        if (isDefaultTenant(tenantId)) {
            return null;
        }

        String key = tenantId.trim();
        TenantSettingsDto cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // On Reactor/Netty non-blocking threads, never call .block()
        if (reactor.core.scheduler.Schedulers.isInNonBlockingThread()) {
            // Trigger background async fetch to populate cache for subsequent requests
            getSettingsReactive(key).subscribe(
                    dto -> log.debug("Async cached tenant settings for {}", key),
                    err -> log.debug("Async fetch failed for tenant {}: {}", key, err.getMessage())
            );
            return null;
        }

        try {
            TenantSettingsDto fetched = restService.getTenantSettings(key).block(Duration.ofSeconds(2));
            if (fetched != null) {
                cache.put(key, fetched);
                return fetched;
            }
        } catch (Exception e) {
            log.debug("Failed to fetch settings from backend for tenant {}: {}", tenantId, e.getMessage());
        }

        return null;
    }

    /**
     * Invalidate cached settings for a tenant
     *
     * @param tenantId UUID string of the tenant
     */
    public void invalidate(String tenantId) {
        if (StringUtils.hasText(tenantId)) {
            cache.invalidate(tenantId.trim());
        }
    }

    /**
     * Update settings for a tenant and refresh the cache
     */
    public Mono<TenantSettingsDto> updateSettings(String tenantId, TenantSettingsDto dto) {
        if (isDefaultTenant(tenantId)) {
            return Mono.error(new IllegalArgumentException("The default tenant has no settings row"));
        }

        String key = tenantId.trim();
        return restService.updateTenantSettings(key, dto)
                .doOnSuccess(saved -> {
                    if (saved != null) {
                        cache.put(key, saved);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Failed to update tenant settings in backend for tenant {}: {}", key, e.getMessage());
                    return Mono.empty();
                });
    }
}
