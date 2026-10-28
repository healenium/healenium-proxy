package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.epam.healenium.healenium_proxy.model.TenantSettingsDto;
import com.epam.healenium.healenium_proxy.restore.RestoreDriverFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionContextServiceTest {

    @Mock
    private RestoreDriverFactory restoreDriverFactory;

    @Mock
    private ProxyConfig proxyConfig;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private TenantSettingsClient tenantSettingsClient;

    private SessionContextService service;

    private final String sessionId = "test-session-123";
    private final String tenantId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        service = new SessionContextService(restoreDriverFactory, proxyConfig, jsonMapper, tenantSettingsClient);

        Config baseConfig = ConfigFactory.empty()
                .withValue("heal-enabled", com.typesafe.config.ConfigValueFactory.fromAnyRef(true))
                .withValue("recovery-tries", com.typesafe.config.ConfigValueFactory.fromAnyRef(1))
                .withValue("score-cap", com.typesafe.config.ConfigValueFactory.fromAnyRef(0.60))
                .withValue("selector-type", com.typesafe.config.ConfigValueFactory.fromAnyRef("cssSelector"));

        when(proxyConfig.getConfig(sessionId)).thenReturn(baseConfig);
    }

    @Test
    void testBuildSessionConfigWithoutTenantUsesDefaults() {
        ProxySessionContext context = new ProxySessionContext();

        Config config = service.buildSessionConfig(sessionId, context);

        assertTrue(config.getBoolean("heal-enabled"));
        assertEquals(1, config.getInt("recovery-tries"));
        assertEquals(0.60, config.getDouble("score-cap"), 0.001);
        assertEquals("cssSelector", config.getString("selector-type"));
    }

    @Test
    void testBuildSessionConfigAppliesTenantSettings() {
        TenantSettingsDto tenantDto = new TenantSettingsDto()
                .setHealEnabled(false)
                .setRecoveryTries(3)
                .setScoreCap(0.80)
                .setHealedType("xpath");

        when(tenantSettingsClient.getSettings(tenantId)).thenReturn(tenantDto);

        ProxySessionContext context = new ProxySessionContext().setTenantId(tenantId);

        Config config = service.buildSessionConfig(sessionId, context);

        assertFalse(config.getBoolean("heal-enabled"));
        assertEquals(3, config.getInt("recovery-tries"));
        assertEquals(0.80, config.getDouble("score-cap"), 0.001);
        assertEquals("xpath", config.getString("selector-type"));
    }

    @Test
    void testBuildSessionConfigCapabilitiesOverrideTenantSettings() {
        TenantSettingsDto tenantDto = new TenantSettingsDto()
                .setHealEnabled(true)
                .setRecoveryTries(2)
                .setScoreCap(0.70)
                .setHealedType("css");

        when(tenantSettingsClient.getSettings(tenantId)).thenReturn(tenantDto);

        Map<String, Object> caps = new HashMap<>();
        caps.put("hlm:healEnabled", false);
        caps.put("hlm:recoveryTries", 5);
        caps.put("hlm:scoreCap", 0.95);
        caps.put("hlm:healedType", "xpath");

        ProxySessionContext context = new ProxySessionContext()
                .setTenantId(tenantId)
                .setCapabilities(caps);

        Config config = service.buildSessionConfig(sessionId, context);

        // Capabilities must have highest priority
        assertFalse(config.getBoolean("heal-enabled"));
        assertEquals(5, config.getInt("recovery-tries"));
        assertEquals(0.95, config.getDouble("score-cap"), 0.001);
        assertEquals("xpath", config.getString("selector-type"));
    }
}
