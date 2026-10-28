package com.epam.healenium.healenium_proxy.service;

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

    // Cache tenant settings for 3 minutes to avoid overhead on every session initialization
    private final Cache<String, TenantSettingsDto> cache = Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /**
     * Get settings for tenant (with in-memory cache)
     *
     * @param tenantId UUID string of the tenant
     * @return TenantSettingsDto or null if unavailable
     */
    public TenantSettingsDto getSettings(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return null;
        }

        String key = tenantId.trim();
        TenantSettingsDto cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
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
        if (!StringUtils.hasText(tenantId)) {
            return Mono.error(new IllegalArgumentException("Tenant ID cannot be null or empty"));
        }

        String key = tenantId.trim();
        return restService.updateTenantSettings(key, dto)
                .doOnSuccess(saved -> {
                    if (saved != null) {
                        cache.put(key, saved);
                    }
                });
    }
}
