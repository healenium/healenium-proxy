package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.auth.TenantResolver;
import com.epam.healenium.healenium_proxy.model.SettingsDto;
import com.epam.healenium.healenium_proxy.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j(topic = "healenium")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/hlm-proxy/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final TenantResolver tenantResolver;

    /**
     * Get all configuration parameters for UI display (reactive)
     */
    @GetMapping
    public Mono<ResponseEntity<SettingsDto>> getConfig() {
        return settingsService.getAllSettings()
                .map(ResponseEntity::ok);
    }

    /**
     * Update a single configuration parameter (reactive)
     * @param request Map containing "key" and "value" for the configuration to update
     * @return Mono containing ResponseEntity with status message
     */
    @PostMapping("/update")
    public Mono<ResponseEntity<Map<String, Object>>> updateSingleSetting(
            @RequestBody Map<String, String> request,
            ServerWebExchange exchange) {
        String key = request.get("key");
        String value = request.get("value");

        return tenantResolver.resolve(exchange)
                .flatMap(tenantId -> settingsService.updateSingleSetting(key, value, tenantId)
                        .map(result -> {
                            if (settingsService.hasErrors(result)) {
                                return ResponseEntity.badRequest().body(result);
                            }
                            return ResponseEntity.ok(result);
                        }))
                .onErrorResume(TenantResolver.TenantResolutionException.class, e ->
                        Mono.just(ResponseEntity.status(e.getStatus())
                                .body(Map.of("error", e.getMessage()))));
    }

}
