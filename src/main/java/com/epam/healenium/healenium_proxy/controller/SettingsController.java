package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.model.SettingsDto;
import com.epam.healenium.healenium_proxy.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j(topic = "healenium")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/hlm-proxy/settings")
public class SettingsController {

    private final SettingsService settingsService;
    
    /**
     * Get all configuration parameters for UI display
     */
    @GetMapping
    public ResponseEntity<SettingsDto> getConfig() {
        SettingsDto config = settingsService.getAllSettings();
        return ResponseEntity.ok(config);
    }

    /**
     * Update a single configuration parameter
     * @param request Map containing "key" and "value" for the configuration to update
     * @return ResponseEntity with status message
     */
    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> updateSingleSetting(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        String value = request.get("value");
        
        Map<String, Object> result = settingsService.updateSingleSetting(key, value);
        
        if (settingsService.hasErrors(result)) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

}
