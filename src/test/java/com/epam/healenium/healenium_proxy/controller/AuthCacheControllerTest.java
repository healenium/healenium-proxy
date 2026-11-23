package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.auth.IamAuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthCacheControllerTest {

    private static final String HASH = "a".repeat(64);

    private IamAuthClient iamAuthClient;
    private AuthCacheController controller;

    @BeforeEach
    void setUp() {
        iamAuthClient = mock(IamAuthClient.class);
        controller = new AuthCacheController(iamAuthClient);
    }

    @Test
    void aSha256Hash_evictsThatKey() {
        ResponseEntity<Map<String, Object>> response = controller.evict(Map.of("keyHash", HASH));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(iamAuthClient).evictApiKey(HASH);
    }

    @Test
    void anythingElse_isRejected_andNothingIsEvicted() {
        Map<String, String> missing = new HashMap<>();
        for (Map<String, String> body : new Map[] {
                missing,
                Map.of("keyHash", ""),
                Map.of("keyHash", "hlm_live_the-raw-key"),
                Map.of("keyHash", "A".repeat(64)),
                Map.of("keyHash", "a".repeat(63))}) {
            assertEquals(HttpStatus.BAD_REQUEST, controller.evict(body).getStatusCode());
        }

        verify(iamAuthClient, never()).evictApiKey(anyString());
    }
}
