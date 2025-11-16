package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.model.ConfigDto;
import com.epam.healenium.healenium_proxy.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j(topic = "healenium")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/hlm-proxy/config")
public class ConfigController {

    private final ConfigService configService;
    
    /**
     * Get all configuration parameters for UI display
     */
    @GetMapping
    public ResponseEntity<ConfigDto> getConfig() {
        ConfigDto config = configService.getAllConfiguration();
        return ResponseEntity.ok(config);
    }

    /**
     * Update configuration parameters from UI
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody ConfigDto request) {
        Map<String, Object> result = configService.updateConfiguration(request);
        if (configService.hasErrors(result)) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
