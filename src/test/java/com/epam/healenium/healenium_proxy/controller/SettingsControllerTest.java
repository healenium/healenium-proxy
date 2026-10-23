package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.auth.HealeniumAuthProperties;
import com.epam.healenium.healenium_proxy.auth.IamAuthClient;
import com.epam.healenium.healenium_proxy.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsControllerTest {

    @Mock
    private SettingsService settingsService;
    @Mock
    private IamAuthClient iamAuthClient;

    private HealeniumAuthProperties properties;
    private SettingsController controller;

    @BeforeEach
    void setUp() {
        properties = new HealeniumAuthProperties();
        properties.getAuth().setEnabled(true);
        controller = new SettingsController(settingsService, iamAuthClient, properties);
    }

    @Test
    void testUpdateSingleSettingSuccessWithBearerToken() {
        UUID tenantId = UUID.randomUUID();
        when(iamAuthClient.validateBearerToken("valid-token")).thenReturn(Mono.just(tenantId));
        when(settingsService.updateSingleSetting(eq("HEAL_ENABLED"), eq("true"), eq(tenantId.toString())))
                .thenReturn(Mono.just(Map.of("success", true, "message", "Configuration updated successfully")));
        when(settingsService.hasErrors(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest.post("/hlm-proxy/settings/update")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header(HttpHeaders.ORIGIN, "https://44.193.74.68")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, String> body = Map.of("key", "HEAL_ENABLED", "value", "true");

        StepVerifier.create(controller.updateSingleSetting(body, exchange))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals(true, response.getBody().get("success"));
                })
                .verifyComplete();
    }

    @Test
    void testUpdateSingleSettingSuccessWithAuthDisabled() {
        properties.getAuth().setEnabled(false);
        when(settingsService.updateSingleSetting(eq("SELECTOR_TYPE"), eq("cssSelector"), eq("")))
                .thenReturn(Mono.just(Map.of("success", true, "message", "Configuration updated successfully")));
        when(settingsService.hasErrors(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest.post("/hlm-proxy/settings/update")
                .header(HttpHeaders.ORIGIN, "https://44.193.74.68")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, String> body = Map.of("key", "SELECTOR_TYPE", "value", "cssSelector");

        StepVerifier.create(controller.updateSingleSetting(body, exchange))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals(true, response.getBody().get("success"));
                })
                .verifyComplete();
    }

    @Test
    void testUpdateSingleSettingUnauthorizedWhenTokenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/hlm-proxy/settings/update")
                .header(HttpHeaders.ORIGIN, "https://44.193.74.68")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, String> body = Map.of("key", "HEAL_ENABLED", "value", "true");

        StepVerifier.create(controller.updateSingleSetting(body, exchange))
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals("Tenant not resolved", response.getBody().get("error"));
                })
                .verifyComplete();
    }
}
