package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.model.SelectorTypeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for managing selector type configuration
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SelectorConfigController {

    private final ProxyConfig proxyConfig;
    
    @Autowired
    private Environment env;

    /**
     * Get current selector type configuration
     * 
     * @return current selector type
     */
    @GetMapping("/selector-type")
    public ResponseEntity<Map<String, String>> getSelectorType() {
        String selectorType = env.getProperty("healing.selectortype", "cssSelector");
        log.info("Getting current selector type: {}", selectorType);
        return ResponseEntity.ok(Collections.singletonMap("selectorType", selectorType));
    }

    /**
     * Update selector type configuration
     * 
     * @param request selector type request
     * @return updated configuration
     */
    @PutMapping("/selector-type")
    public ResponseEntity<Map<String, String>> updateSelectorType(@RequestBody SelectorTypeRequest request) {
        String selectorType = request.getSelectorType();
        
        if (selectorType == null || (!selectorType.equals("cssSelector") && !selectorType.equals("xpath"))) {
            log.warn("Invalid selector type: {}. Must be 'cssSelector' or 'xpath'", selectorType);
            return ResponseEntity.badRequest().body(
                Collections.singletonMap("error", "Invalid selector type. Must be 'cssSelector' or 'xpath'")
            );
        }
        
        proxyConfig.updateSelectorType(selectorType);
        log.info("Selector type updated to: {}", selectorType);
        
        Map<String, String> response = new HashMap<>();
        response.put("selectorType", selectorType);
        response.put("message", "Selector type updated successfully");
        
        return ResponseEntity.ok(response);
    }
}
