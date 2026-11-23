package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.auth.IamAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Called by hlm-iam when an API key is revoked (spec 004). Like the settings endpoints, no edge path
 * reaches it and the network policy admits only hlm-iam. It names the key by its SHA-256 hash and can
 * only remove a cache entry, so the worst a caller could do is force one key to be validated again.
 */
@Slf4j(topic = "healenium")
@RestController
@RequiredArgsConstructor
@RequestMapping("/hlm-proxy/auth")
public class AuthCacheController {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final IamAuthClient iamAuthClient;

    @PostMapping("/cache/evict")
    public ResponseEntity<Map<String, Object>> evict(@RequestBody Map<String, String> request) {
        String keyHash = request.get("keyHash");
        if (keyHash == null || !SHA_256_HEX.matcher(keyHash).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "keyHash must be a lowercase SHA-256 hex string"));
        }
        iamAuthClient.evictApiKey(keyHash);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
