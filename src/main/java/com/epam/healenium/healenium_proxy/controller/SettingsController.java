package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.model.ProxyDefaultsDto;
import com.epam.healenium.healenium_proxy.service.SettingsClient;
import com.epam.healenium.healenium_proxy.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Management endpoints for hlm-iam only (spec 004, Requirements 3 and 4). No edge path reaches them,
 * and the network policy admits only hlm-iam. The path names are unchanged for the dashboard.
 */
@Slf4j(topic = "healenium")
@RestController
@RequiredArgsConstructor
@RequestMapping("/healenium-proxy/settings")
public class SettingsController {

    static final String TENANT_HEADER = "Healenium-Tenant-Id";
    private static final String LOG_LEVEL = "LOG_LEVEL";

    private final SettingsService settingsService;
    private final SettingsClient settingsClient;

    @GetMapping("/defaults")
    public ResponseEntity<ProxyDefaultsDto> defaults() {
        return ResponseEntity.ok(settingsService.defaults());
    }

    /** This proxy applies only LOG_LEVEL. Every other key belongs to hlm-iam or to the backend. */
    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> update(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        if (!LOG_LEVEL.equals(key)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "errors", Map.of("key", "Unsupported key: " + key)));
        }
        String level = request.get("value") == null ? null : request.get("value").toUpperCase();
        String error = SettingsService.logLevelError(level);
        if (error != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "errors", Map.of("logLevel", error)));
        }
        settingsService.applyLogLevel(level);
        return ResponseEntity.ok(Map.of("success", true, "key", LOG_LEVEL, "value", level, "logLevel", level));
    }

    /** Drops one tenant's cached settings, or the whole cache when the tenant header is absent. */
    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, Object>> invalidate(
            @RequestHeader(value = TENANT_HEADER, required = false) String tenantId) {
        if (StringUtils.hasText(tenantId)) {
            settingsClient.invalidate(tenantId);
        } else {
            settingsClient.invalidateAll();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
