package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.model.ProxyDefaultsDto;
import com.epam.healenium.healenium_proxy.service.SettingsClient;
import com.epam.healenium.healenium_proxy.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsControllerTest {

    private SettingsService settingsService;
    private SettingsClient settingsClient;
    private SettingsController controller;

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        settingsClient = mock(SettingsClient.class);
        controller = new SettingsController(settingsService, settingsClient);
    }

    @Test
    void defaults_returnsTheProxyConfiguration() {
        ProxyDefaultsDto defaults = new ProxyDefaultsDto(true, 2, 0.7, "css", "INFO", "SELENIUM");
        when(settingsService.defaults()).thenReturn(defaults);

        ResponseEntity<ProxyDefaultsDto> response = controller.defaults();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(defaults, response.getBody());
    }

    @Test
    void update_logLevel_isAppliedToThisProcess() {
        ResponseEntity<Map<String, Object>> response = controller.update(Map.of("key", "LOG_LEVEL", "value", "debug"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("DEBUG", response.getBody().get("logLevel"));
        verify(settingsService).applyLogLevel("DEBUG");
    }

    @Test
    void update_anyOtherKey_isRejected_andNothingIsApplied() {
        ResponseEntity<Map<String, Object>> response = controller.update(Map.of("key", "SCORE_CAP", "value", "0.9"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(settingsService, never()).applyLogLevel(anyString());
    }

    @Test
    void update_invalidLogLevel_keepsTheMessage() {
        ResponseEntity<Map<String, Object>> response = controller.update(Map.of("key", "LOG_LEVEL", "value", "LOUD"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("logLevel", "Log level must be one of: ERROR, WARN, INFO, DEBUG, TRACE"),
                response.getBody().get("errors"));
        verify(settingsService, never()).applyLogLevel(anyString());
    }

    @Test
    void invalidate_withTenant_dropsOnlyThatTenant() {
        controller.invalidate("11111111-1111-1111-1111-111111111111");

        verify(settingsClient).invalidate("11111111-1111-1111-1111-111111111111");
        verify(settingsClient, never()).invalidateAll();
    }

    @Test
    void invalidate_withoutTenant_dropsEverything() {
        controller.invalidate(null);

        verify(settingsClient).invalidateAll();
    }
}
